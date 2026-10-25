package com.mnavarrosqa.clima.data

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.mnavarrosqa.clima.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** Una versión publicada en el repo público de versiones. */
@Serializable
data class AppRelease(val versionName: String, val versionCode: Long, val apkUrl: String, val notes: String?)

/**
 * Busca versiones nuevas en GitHub Releases (repo público, solo con los APK). Si la app se instaló
 * desde Google Play no hace nada: ahí las actualizaciones las maneja Play.
 */
object Updates {
    private const val REPO = "mnavarrosqa/climatin-releases"
    // Las versiones salen seguido: consultar cada 3 horas hacía que el aviso casi nunca apareciera.
    // Con ETag, si no cambió nada GitHub responde 304 y no cuenta para su límite de pedidos.
    private const val CHECK_EVERY_MS = 10 * 60_000L
    private const val KEY_ETAG = "updates_etag"
    private const val KEY_LATEST = "updates_latest"
    private const val KEY_DISMISSED = "updates_dismissed"
    private val json = Json { ignoreUnknownKeys = true }
    private val apkName = Regex("""climatin-(\d+)\.apk""")

    fun installedVersion(context: Context): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return (info.versionName ?: "") to PackageInfoCompat.getLongVersionCode(info)
    }

    fun installedFromPlay(context: Context): Boolean = runCatching {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        installer == "com.android.vending"
    }.getOrDefault(false)

    /**
     * Devuelve la versión nueva si hay una. Sin [force], consulta GitHub como mucho cada 10 minutos y
     * mientras tanto responde con la última que encontró (antes devolvía nada y el aviso se
     * perdía al volver a abrir la app). Los errores de red se ignoran (no es algo urgente).
     */
    suspend fun check(context: Context, force: Boolean): AppRelease? {
        if (!BuildConfig.SELF_UPDATE || installedFromPlay(context)) return null
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val installed = installedVersion(context).second
        val known = prefs.getString(KEY_LATEST, null)?.let { runCatching { json.decodeFromString(AppRelease.serializer(), it) }.getOrNull() }
        if (!force && now - prefs.getLong("updates_checked_at", 0L) < CHECK_EVERY_MS) {
            return known?.takeIf { it.versionCode > installed }
        }
        val etag = prefs.getString(KEY_ETAG, null)?.takeIf { known != null }
        val response = runCatching { latest(etag) }.getOrNull() ?: return known?.takeIf { it.versionCode > installed }
        val latest = response.first ?: known ?: return null
        prefs.edit()
            .putLong("updates_checked_at", now)
            .putString(KEY_LATEST, json.encodeToString(AppRelease.serializer(), latest))
            .apply {
                response.second?.let { putString(KEY_ETAG, it) }
            }
            .apply()
        return latest.takeIf { it.versionCode > installed }
    }

    /** "Ahora no" en el aviso: no se vuelve a mostrar para esta versión (sigue en Ajustes → Acerca de). */
    fun dismiss(context: Context, release: AppRelease) {
        prefs(context).edit().putLong(KEY_DISMISSED, release.versionCode).apply()
    }

    fun isDismissed(context: Context, release: AppRelease): Boolean =
        prefs(context).getLong(KEY_DISMISSED, 0L) >= release.versionCode

    private fun prefs(context: Context) = context.getSharedPreferences("clima", Context.MODE_PRIVATE)

    /**
     * La última versión publicada y el ETag de la respuesta. Con [etag] y sin cambios, GitHub
     * responde 304: la versión vuelve null (se usa la guardada). Null entero si falló.
     */
    private suspend fun latest(etag: String?): Pair<AppRelease?, String?>? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Climatin (Android)")
            etag?.let { connection.setRequestProperty("If-None-Match", it) }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return@withContext null to etag
            if (code !in 200..299) return@withContext null
            val release = json.decodeFromString(GitHubRelease.serializer(), connection.inputStream.bufferedReader().use { it.readText() })
            val asset = release.assets.firstNotNullOfOrNull { asset ->
                apkName.matchEntire(asset.name)?.let { it.groupValues[1].toLong() to asset.url }
            } ?: return@withContext null
            AppRelease(release.tag.removePrefix("v"), asset.first, asset.second, release.body?.trim()) to connection.getHeaderField("ETag")
        } finally {
            connection.disconnect()
        }
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
)

/**
 * Baja el APK de una versión nueva y abre el instalador de Android. Solo en la variante "github":
 * en Play las actualizaciones las maneja la tienda.
 */
object UpdateInstaller {

    /** Baja el APK a la caché, informando el avance de 0 a 1. */
    suspend fun download(context: Context, release: AppRelease, onProgress: (Float) -> Unit): java.io.File =
        withContext(Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
            // Se borran APK viejos para no ocupar espacio.
            dir.listFiles()?.forEach { it.delete() }
            val file = java.io.File(dir, "climatin-${release.versionCode}.apk")
            // Se baja a un archivo aparte y se renombra al final: un APK cortado no queda como bueno.
            val partial = java.io.File(dir, file.name + ".part")
            val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "Climatin (Android)")
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                        if (total > 0 && done != total) error("Descarga incompleta: $done de $total bytes")
                    }
                }
                if (!partial.renameTo(file)) error("No pude guardar la actualización")
            } catch (failure: Throwable) {
                partial.delete()
                throw failure
            } finally {
                connection.disconnect()
            }
            file
        }

    /** Si Android ya permite que Climatin instale apps (se pide una sola vez). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Abre el ajuste "Instalar apps desconocidas" de Climatin. */
    fun openPermissionSettings(context: Context) {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Abre el instalador de Android con el APK bajado. */
    fun install(context: Context, file: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
