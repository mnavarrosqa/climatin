package com.mnavarrosqa.clima.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.mnavarrosqa.clima.data.Place
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.ClimaActivity
import com.mnavarrosqa.clima.work.Outing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.annotation.DrawableRes
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.core.R as CoreR
import com.mnavarrosqa.clima.data.tr

/**
 * Íconos de la app. Cada uno es un activity-alias del manifiesto; se prende el elegido y se
 * apagan los demás. El launcher puede tardar unos segundos en mostrar el cambio.
 */
enum class AppIcon(
    private val alias: String,
    private val es: String,
    private val en: String,
    @DrawableRes val background: Int,
    @DrawableRes val foreground: Int,
) {
    MONOGRAMA(".MainActivity", "Monograma", "Monogram", CoreR.drawable.ic_launcher_background, CoreR.drawable.ic_launcher_foreground),
    NOCHE(".IconoNoche", "Noche", "Night", R.drawable.ic_launcher_noche_background, R.drawable.ic_launcher_noche_foreground),
    ATARDECER(".IconoAtardecer", "Atardecer", "Sunset", R.drawable.ic_launcher_atardecer_background, R.drawable.ic_launcher_atardecer_foreground),
    CLARO(".IconoClaro", "Claro", "Light", R.drawable.ic_launcher_claro_background, R.drawable.ic_launcher_claro_foreground),
    ;

    val label: String get() = tr(es, en)

    private fun component(context: Context) = ComponentName(context.packageName, "com.mnavarrosqa.clima$alias")

    /** El alias como componente, para colgarle accesos directos. */
    fun componentName(context: Context): ComponentName = component(context)

    companion object {
        /** El elegido, leído del sistema una sola vez (son llamadas al sistema) y como estado de Compose. */
        private var selected by mutableStateOf<AppIcon?>(null)

        /** El que está prendido ahora; si el sistema no dice nada, el que viene por defecto. */
        fun current(context: Context): AppIcon = selected ?: read(context).also { selected = it }

        private fun read(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull { icon ->
                when (pm.getComponentEnabledSetting(icon.component(context))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == MONOGRAMA
                    else -> false
                }
            } ?: MONOGRAMA
        }

        /** Cambia el ícono. Son varias llamadas al sistema: va fuera del hilo principal. */
        suspend fun set(context: Context, icon: AppIcon) {
            selected = icon
            withContext(Dispatchers.IO) { enable(context, icon) }
        }

        private fun enable(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            // Primero se prende el nuevo: si no, por un instante la app quedaría sin ícono.
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            // El acceso directo "Salgo ahora" queda colgado del ícono viejo: se lo pasa al nuevo.
            runCatching {
                val shortcut = ShortcutInfoCompat.Builder(context, "salgo")
                    .setShortLabel(context.getString(R.string.tile_outing_name))
                    .setLongLabel(tr("Salgo ahora: 2 horas de clima en vivo", "Heading out: 2 hours of live weather"))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_outing))
                    .setIntent(Intent(context, ClimaActivity::class.java).setAction(Outing.ACTION_START))
                    .setActivity(icon.component(context))
                    .build()
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            }
            // Los de las ciudades también cuelgan del ícono: se rearman con el nuevo.
            CityShortcuts.update(context, WeatherRepository.get(context).places())
        }
    }
}

/**
 * Accesos directos a las primeras ciudades guardadas (al mantener apretado el ícono de la app).
 * Abren la app en esa ciudad con el mismo extra "place" que usan los widgets. "Salgo ahora"
 * queda primero y no se toca.
 */
object CityShortcuts {
    private const val PREFIX = "ciudad:"
    private const val MAX_CITIES = 3

    /** Lo último publicado: con las ciudades iguales no se vuelve a llamar al sistema. */
    @Volatile
    private var lastKey: String? = null

    /**
     * Son llamadas al sistema y se dibujan íconos: va fuera del hilo principal. Sincronizado
     * porque lo llaman a la vez la lista de ciudades y el fin de una actualización.
     */
    @Synchronized
    fun update(context: Context, places: List<Place>) {
        runCatching {
            val app = context.applicationContext
            val alias = AppIcon.current(app)
            // Los launchers muestran pocos (4 o 5): uno queda para "Salgo ahora".
            val room = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(app) - 1).coerceAtLeast(0)
            val shown = places.take(minOf(MAX_CITIES, room))
            val repository = WeatherRepository.get(app)
            // Con el estado en la clave el ícono se renueva cuando cambia el clima de la ciudad.
            val codes = shown.map { place ->
                val current = repository.cached(place.id)?.current
                if (current == null) "" else "${current.code}-${current.isDay}"
            }
            val key = alias.name + "|" + shown.mapIndexed { i, p -> "${p.id}=${p.shownName}=${codes[i]}" }.joinToString("|")
            if (key == lastKey) return
            val ids = shown.map { PREFIX + it.id }.toSet()
            // Las de ciudades quitadas (o que ya no entran) se sacan.
            val stale = ShortcutManagerCompat.getDynamicShortcuts(app).map { it.id }.filter { it.startsWith(PREFIX) && it !in ids }
            if (stale.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(app, stale)
            shown.forEachIndexed { index, place ->
                val snapshot = repository.cached(place.id)
                val intent = Intent(app, ClimaActivity::class.java)
                    // Los accesos directos necesitan una acción; el extra es el de los widgets.
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra("place", place.id)
                val shortcut = ShortcutInfoCompat.Builder(app, PREFIX + place.id)
                    .setShortLabel(place.shownName.take(24))
                    .setLongLabel(tr("El clima en ${place.shownName}", "Weather in ${place.shownName}"))
                    .setIcon(IconCompat.createWithAdaptiveBitmap(icon(app, snapshot)))
                    .setIntent(intent)
                    .setActivity(alias.componentName(app))
                    // Después de "Salgo ahora", en el orden de la lista de ciudades.
                    .setRank(index + 1)
                    .build()
                ShortcutManagerCompat.pushDynamicShortcut(app, shortcut)
            }
            lastKey = key
        }
    }

    /**
     * Ícono adaptable: el cielo de la ciudad con el ícono de su clima en el centro (dentro de la
     * zona segura, el 66 % del medio, que el launcher nunca recorta).
     */
    private fun icon(context: Context, snapshot: WeatherSnapshot?): Bitmap {
        val size = 324
        val current = snapshot?.current
        val code = current?.code ?: 2
        val isDay = current?.isDay ?: true
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = skyColors(WeatherCodes.kind(code), isDay).map { it.toArgb() }.toIntArray()
        Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, size.toFloat(), colors, floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), this)
        }
        val drawable = ContextCompat.getDrawable(context, WeatherCodes.icon(code, isDay))?.mutate()
        if (drawable != null) {
            val inset = (size * 0.28f).toInt()
            drawable.setBounds(inset, inset, size - inset, size - inset)
            drawable.draw(canvas)
        }
        return bitmap
    }
}
