package com.mnavarrosqa.clima.data

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** Una versión publicada en el repo público de versiones. */
data class AppRelease(val versionName: String, val versionCode: Long, val apkUrl: String, val notes: String?)

/**
 * Busca versiones nuevas en GitHub Releases (repo público, solo con los APK). Si la app se instaló
 * desde Google Play no hace nada: ahí las actualizaciones las maneja Play.
 */
object Updates {
    private const val REPO = "mnavarrosqa/climatin-releases"
    private const val CHECK_EVERY_MS = 12 * 60 * 60_000L
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
     * Devuelve la versión nueva si hay una. Sin [force], consulta como mucho cada 12 horas;
     * los errores de red se ignoran (no es algo urgente).
     */
    suspend fun check(context: Context, force: Boolean): AppRelease? {
        if (installedFromPlay(context)) return null
        val prefs = context.getSharedPreferences("clima", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("updates_checked_at", 0L) < CHECK_EVERY_MS) return null
        val latest = runCatching { latest() }.getOrNull() ?: return null
        prefs.edit().putLong("updates_checked_at", now).apply()
        return latest.takeIf { it.versionCode > installedVersion(context).second }
    }

    private suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Climatin (Android)")
            if (connection.responseCode !in 200..299) return@withContext null
            val release = json.decodeFromString(GitHubRelease.serializer(), connection.inputStream.bufferedReader().use { it.readText() })
            val asset = release.assets.firstNotNullOfOrNull { asset ->
                apkName.matchEntire(asset.name)?.let { it.groupValues[1].toLong() to asset.url }
            } ?: return@withContext null
            AppRelease(release.tag.removePrefix("v"), asset.first, asset.second, release.body?.trim())
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
