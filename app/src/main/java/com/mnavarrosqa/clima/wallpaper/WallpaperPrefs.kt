package com.mnavarrosqa.clima.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.tr

/** Diseños del fondo de pantalla. */
enum class WallpaperStyle(val label: () -> String, val description: () -> String) {
    SKY({ tr("Cielo", "Sky") }, { tr("El cielo de la app, de punta a punta", "The app's sky, edge to edge") }),
    LANDSCAPE({ tr("Paisaje", "Landscape") }, { tr("Cielo con sierras que se mueven al pasar de pantalla", "Sky over hills that shift as you swipe") }),
    SHAPES({ tr("Formas", "Shapes") }, { tr("Manchas de color suaves y un sol con forma de galleta", "Soft color blobs and a cookie-shaped sun") }),
    DARK({ tr("Oscuro", "Dark") }, { tr("Negro con lluvia, nieve o estrellas: gasta menos batería", "Black with rain, snow or stars: easier on the battery") }),
}

/** Cuánto se mueve el fondo. */
enum class WallpaperMotion(val label: () -> String) {
    SMOOTH({ tr("Fluida", "Smooth") }),
    SAVER({ tr("Ahorro", "Saver") }),
    STILL({ tr("Quieta", "Still") }),
}

data class WallpaperSettings(
    val placeId: String = CURRENT_PLACE_ID,
    val style: WallpaperStyle = WallpaperStyle.SKY,
    val motion: WallpaperMotion = WallpaperMotion.SMOOTH,
    val timeOfDay: Boolean = true,
    val showWeather: Boolean = false,
    val touch: Boolean = true,
)

/** Ajustes del fondo de pantalla, compartidos entre la pantalla de ajustes y el servicio. */
object WallpaperPrefs {
    private const val FILE = "fondo"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context): WallpaperSettings {
        val p = prefs(context)
        val defaults = WallpaperSettings()
        return WallpaperSettings(
            placeId = p.getString("lugar", null) ?: defaults.placeId,
            style = p.getString("estilo", null)?.let { name -> WallpaperStyle.entries.firstOrNull { it.name == name } } ?: defaults.style,
            motion = p.getString("movimiento", null)?.let { name -> WallpaperMotion.entries.firstOrNull { it.name == name } } ?: defaults.motion,
            timeOfDay = p.getBoolean("hora", defaults.timeOfDay),
            showWeather = p.getBoolean("clima", defaults.showWeather),
            touch = p.getBoolean("tacto", defaults.touch),
        )
    }

    fun save(context: Context, settings: WallpaperSettings) {
        prefs(context).edit()
            .putString("lugar", settings.placeId)
            .putString("estilo", settings.style.name)
            .putString("movimiento", settings.motion.name)
            .putBoolean("hora", settings.timeOfDay)
            .putBoolean("clima", settings.showWeather)
            .putBoolean("tacto", settings.touch)
            .apply()
    }
}

object LiveWallpaper {
    fun component(context: Context) = ComponentName(context, SkyWallpaperService::class.java)

    /** Si el cielo de Climatin ya es el fondo de pantalla. */
    fun isActive(context: Context): Boolean =
        runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.serviceName == SkyWallpaperService::class.java.name }
            .getOrDefault(false)

    /** Abre la vista previa del sistema para poner el fondo. */
    fun open(context: Context) {
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
        // Algunos launchers no aceptan ir directo: entonces se abre el selector general.
        runCatching { context.startActivity(direct) }
            .onFailure { runCatching { context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } }
    }
}
