package com.mnavarrosqa.clima.widget

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.currentState
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.tr

/** Ajustes extra de cada widget, además de ciudad, estilo y dato. */
object WidgetOptionKeys {
    val ACCENT = stringPreferencesKey("accent")
    /** Opacidad del fondo en los estilos Sistema y Color, de 0 a 100. */
    val OPACITY = intPreferencesKey("opacity")
    val CORNERS = stringPreferencesKey("corners")
    val TAP = stringPreferencesKey("tap")
    /** Partes ocultas, separadas por coma (ver [WidgetPart]). */
    val HIDDEN = stringPreferencesKey("hidden")
}

/** Ciudad especial: el widget pasa por todas tus ciudades, una por cada actualización. */
const val ROTATE_PLACE_ID = "rotar"

/** Cada cuánto cambia de ciudad un widget que rota (coincide con la actualización automática). */
private const val ROTATE_MINUTES = 30L

/** Color del widget: el del fondo de pantalla (Material You) o uno fijo. */
enum class WidgetAccent(val hue: Float?, private val es: String, private val en: String) {
    DYNAMIC(null, "Fondo de pantalla", "Wallpaper"),
    BLUE(215f, "Azul", "Blue"),
    GREEN(140f, "Verde", "Green"),
    ORANGE(28f, "Naranja", "Orange"),
    PINK(330f, "Rosa", "Pink"),
    GRAY(220f, "Gris", "Gray"),
    ;

    val label: String get() = tr(es, en)

    /** Saturación de la paleta: el gris es casi neutro. */
    val saturation: Float get() = if (this == GRAY) 0.08f else 0.55f

    /** Color de muestra para el selector. */
    val swatch: Color get() = hue?.let { tone(it, saturation, 0.55f) } ?: Color.Unspecified

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: DYNAMIC
    }
}

enum class WidgetCorners(private val es: String, private val en: String) {
    AUTO("Automáticas", "Automatic"),
    SQUARE("Rectas", "Square"),
    ROUND("Muy redondas", "Extra round"),
    ;

    val label: String get() = tr(es, en)

    fun radius(default: Dp): Dp = when (this) {
        AUTO -> default
        SQUARE -> 8.dp
        ROUND -> maxOf(default, 36.dp)
    }

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Qué pasa al tocar el widget (fuera de sus botones y de los días u horas). */
enum class WidgetTap(private val es: String, private val en: String) {
    APP("Abrir la app", "Open the app"),
    RADAR("Mapa de lluvia", "Rain map"),
    NEXT("Siguiente ciudad", "Next city"),
    REFRESH("Actualizar", "Refresh"),
    ;

    val label: String get() = tr(es, en)

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: APP
    }
}

/** Partes que se pueden ocultar. */
enum class WidgetPart(private val es: String, private val en: String, private val esNote: String, private val enNote: String) {
    INSIGHT("Frase del momento", "Smart line", "\"Llevá paraguas a las 17\", \"UV alto\"…", "\"Take an umbrella at 5 PM\", \"High UV\"…"),
    PLACE("Ciudad", "City", "El nombre del lugar", "The place name"),
    RANGE("Máxima y mínima", "High and low", "La de hoy, junto a la temperatura", "Today's, next to the temperature"),
    REFRESH("Botón ⟳", "⟳ button", "Actualizar sin abrir la app", "Refresh without opening the app"),
    NEXT("Botón ›", "› button", "Pasar a la siguiente ciudad", "Switch to the next city"),
    ;

    val label: String get() = tr(es, en)
    val note: String get() = tr(esNote, enNote)
}

fun hiddenParts(raw: String?): Set<WidgetPart> =
    raw.orEmpty().split(',').mapNotNull { name -> WidgetPart.entries.firstOrNull { it.name == name } }.toSet()

fun hiddenRaw(parts: Set<WidgetPart>): String = parts.joinToString(",") { it.name }

/** Qué partes muestra este widget. */
@Composable
fun widgetShows(part: WidgetPart): Boolean = part !in hiddenParts(currentState(WidgetOptionKeys.HIDDEN))

/** Todo lo que se elige en la pantalla de personalización, para guardar, copiar o previsualizar. */
data class WidgetConfig(
    val placeId: String = CURRENT_PLACE_ID,
    val style: WidgetStyle = WidgetStyle.SYSTEM,
    val metric: Metric = Metric.UV,
    val accent: WidgetAccent = WidgetAccent.DYNAMIC,
    val opacity: Int = 100,
    val corners: WidgetCorners = WidgetCorners.AUTO,
    val tap: WidgetTap = WidgetTap.APP,
    val hidden: Set<WidgetPart> = emptySet(),
) {
    fun writeTo(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs[WidgetPrefs.PLACE] = placeId
        prefs[WidgetPrefs.STYLE] = style.name
        prefs[WidgetPrefs.METRIC] = metric.name
        prefs[WidgetOptionKeys.ACCENT] = accent.name
        prefs[WidgetOptionKeys.OPACITY] = opacity
        prefs[WidgetOptionKeys.CORNERS] = corners.name
        prefs[WidgetOptionKeys.TAP] = tap.name
        prefs[WidgetOptionKeys.HIDDEN] = hiddenRaw(hidden)
    }

    companion object {
        fun from(prefs: Preferences, defaultStyle: WidgetStyle, resolvePlace: (String?) -> String): WidgetConfig = WidgetConfig(
            placeId = prefs[WidgetPrefs.PLACE]?.takeIf { it == ROTATE_PLACE_ID } ?: resolvePlace(prefs[WidgetPrefs.PLACE]),
            style = WidgetStyle.from(prefs[WidgetPrefs.STYLE], defaultStyle),
            metric = Metric.from(prefs[WidgetPrefs.METRIC]),
            accent = WidgetAccent.from(prefs[WidgetOptionKeys.ACCENT]),
            opacity = prefs[WidgetOptionKeys.OPACITY] ?: 100,
            corners = WidgetCorners.from(prefs[WidgetOptionKeys.CORNERS]),
            tap = WidgetTap.from(prefs[WidgetOptionKeys.TAP]),
            hidden = hiddenParts(prefs[WidgetOptionKeys.HIDDEN]),
        )
    }
}

/**
 * Ciudad de un widget. Si rota, elige una de "donde estoy" y las guardadas según la hora, así
 * cambia sola en cada actualización automática.
 */
fun resolveWidgetPlace(context: Context, raw: String?): String {
    val repository = WeatherRepository.get(context)
    if (raw != ROTATE_PLACE_ID) return repository.resolvePlaceId(raw)
    val ids = listOf(CURRENT_PLACE_ID) + repository.places().map { it.id }
    val slot = System.currentTimeMillis() / 60_000 / ROTATE_MINUTES
    return ids[(slot % ids.size).toInt()]
}

/** Color en HSL (tono 0–360, saturación y luz 0–1). */
private fun tone(hue: Float, saturation: Float, lightness: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))

/** Paleta clara y oscura de Material 3 armada a partir de un tono, para los colores fijos. */
fun accentSchemes(accent: WidgetAccent): Pair<ColorScheme, ColorScheme>? {
    val hue = accent.hue ?: return null
    val s = accent.saturation
    val neutral = s * 0.25f
    val light = lightColorScheme(
        primary = tone(hue, s, 0.40f),
        onPrimary = Color.White,
        primaryContainer = tone(hue, s, 0.88f),
        onPrimaryContainer = tone(hue, s, 0.14f),
        secondary = tone(hue, s * 0.5f, 0.40f),
        secondaryContainer = tone(hue, s * 0.5f, 0.86f),
        onSecondaryContainer = tone(hue, s * 0.5f, 0.14f),
        tertiary = tone((hue + 60f) % 360f, s, 0.40f),
        background = tone(hue, neutral, 0.97f),
        surface = tone(hue, neutral, 0.97f),
        surfaceVariant = tone(hue, neutral, 0.90f),
        onSurface = tone(hue, neutral, 0.10f),
        onSurfaceVariant = tone(hue, neutral, 0.30f),
    )
    val dark = darkColorScheme(
        primary = tone(hue, s, 0.78f),
        onPrimary = tone(hue, s, 0.20f),
        primaryContainer = tone(hue, s, 0.30f),
        onPrimaryContainer = tone(hue, s, 0.90f),
        secondary = tone(hue, s * 0.5f, 0.78f),
        secondaryContainer = tone(hue, s * 0.5f, 0.28f),
        onSecondaryContainer = tone(hue, s * 0.5f, 0.90f),
        tertiary = tone((hue + 60f) % 360f, s, 0.78f),
        background = tone(hue, neutral, 0.09f),
        surface = tone(hue, neutral, 0.09f),
        surfaceVariant = tone(hue, neutral, 0.22f),
        onSurface = tone(hue, neutral, 0.90f),
        onSurfaceVariant = tone(hue, neutral, 0.78f),
    )
    return light to dark
}
