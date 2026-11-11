package com.mnavarrosqa.clima.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mnavarrosqa.clima.BuildConfig
import com.mnavarrosqa.clima.data.Updates
import com.mnavarrosqa.clima.data.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pasarle Climatin a alguien: el instalador que ya está en el teléfono (sirve sin conexión, por
 * Quick Share o un chat) o el link a la última versión publicada. La versión de Google Play
 * comparte su página de la tienda: su APK no sirve para instalar (va sin los splits y firmado
 * por Play) y el de GitHub no se instala encima.
 */
object AppShare {

    /** Página de la última versión publicada, con el APK para bajar. */
    const val RELEASES_URL = "https://github.com/mnavarrosqa/climatin-releases/releases/latest"

    /** Página de la app en Google Play. */
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.mnavarrosqa.clima"

    const val PRIVACY_URL = "https://github.com/mnavarrosqa/climatin/blob/main/PRIVACIDAD.md"

    const val CONTACT_EMAIL = "martin@upgservicios.com"

    /** Compilada para Play o instalada desde ahí: no se ofrece el instalador ni el link de GitHub. */
    fun isPlayBuild(context: Context): Boolean = !BuildConfig.SELF_UPDATE || Updates.installedFromPlay(context)

    /**
     * Copia el APK instalado a la caché (la carpeta "compartir/" que expone el FileProvider) y abre
     * el selector para mandarlo. Siempre el mismo nombre por versión: no se acumulan copias.
     */
    suspend fun shareApk(context: Context) {
        val app = context.applicationContext
        val version = Updates.installedVersion(app).first
        val uri = withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "compartir").apply { mkdirs() }
            // Se borran las copias de versiones anteriores.
            dir.listFiles { file -> file.name.endsWith(".apk") }?.forEach { it.delete() }
            val target = File(dir, "Climatin-$version.apk")
            File(app.applicationInfo.sourceDir).copyTo(target, overwrite = true)
            FileProvider.getUriForFile(app, "${app.packageName}.updates", target)
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, tr("Climatin $version, el clima con avisos de lluvia. Para instalarlo, abrí el archivo y permití instalar apps de esa fuente.", "Climatin $version, weather with rain alerts. To install it, open the file and allow installing apps from that source."))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri)
        start(context, Intent.createChooser(send, tr("Mandar Climatin", "Send Climatin")))
    }

    /** Comparte el link a la última versión, para quien prefiere bajarla. */
    fun shareLink(context: Context) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, tr("Probá Climatin, el clima con avisos de lluvia: $RELEASES_URL", "Try Climatin, weather with rain alerts: $RELEASES_URL"))
        start(context, Intent.createChooser(send, tr("Compartir Climatin", "Share Climatin")))
    }

    /** Comparte la página de Google Play. */
    fun sharePlay(context: Context) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, tr("Probá Climatin, el clima con avisos de lluvia: $PLAY_URL", "Try Climatin, weather with rain alerts: $PLAY_URL"))
        start(context, Intent.createChooser(send, tr("Compartir Climatin", "Share Climatin")))
    }

    /** Abre la app de correo con el destinatario y el asunto puestos. */
    fun contact(context: Context) {
        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL?subject=Climatin"))
            .putExtra(Intent.EXTRA_SUBJECT, "Climatin")
        if (context !is android.app.Activity) mail.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(mail)
    }

    private fun start(context: Context, chooser: Intent) {
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
