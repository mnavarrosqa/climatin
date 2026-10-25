package com.mnavarrosqa.clima.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mnavarrosqa.clima.data.tr

/** Paleta de respaldo para Android 11 o anterior (sin Material You). */
val LightColors = lightColorScheme(
    primary = Color(0xFF3A5F90),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF555F71),
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF6E5676),
    tertiaryContainer = Color(0xFFF7D8FF),
    onTertiaryContainer = Color(0xFF27132F),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF43474E),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFA4C9FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF1F4876),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBDC7DC),
    secondaryContainer = Color(0xFF3E4758),
    onSecondaryContainer = Color(0xFFD9E3F8),
    tertiary = Color(0xFFDABDE2),
    tertiaryContainer = Color(0xFF553F5D),
    onTertiaryContainer = Color(0xFFF7D8FF),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    onSurface = Color(0xFFE1E2E9),
    onSurfaceVariant = Color(0xFFC4C6CF),
)

/** Tema elegido en Ajustes → Apariencia. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Color de acento de botones, interruptores y resaltados. WALLPAPER es Material You (Android 12+). */
enum class Accent(private val es: String, private val en: String, val swatch: Color) {
    WALLPAPER("Del fondo de pantalla", "From wallpaper", Color(0xFF7F8DA6)),
    BLUE("Azul", "Blue", Color(0xFF3A5F90)),
    GREEN("Verde", "Green", Color(0xFF2E6B3A)),
    ORANGE("Naranja", "Orange", Color(0xFF8F4C00)),
    PINK("Rosa", "Pink", Color(0xFF9C3D6A)),
    PURPLE("Violeta", "Purple", Color(0xFF6750A4)),
    ;

    val label: String get() = tr(es, en)
}

/** Cuánto se mueve el cielo del pronóstico. */
enum class SkyMotion(private val es: String, private val en: String) {
    FULL("Animado", "Animated"),
    REDUCED("Reducido", "Reduced"),
    STILL("Quieto", "Still"),
    ;

    val label: String get() = tr(es, en)
}

/** Tamaño del número grande de la temperatura. */
enum class HeroSize(private val es: String, private val en: String, val scale: Float) {
    SMALL("Chica", "Small", 0.75f),
    MEDIUM("Mediana", "Medium", 1f),
    LARGE("Grande", "Large", 1.25f),
    ;

    val label: String get() = tr(es, en)
}

/** Datos que pueden ir debajo de la temperatura grande. */
enum class HeroLine(private val es: String, private val en: String) {
    HIGH_LOW("Máxima y mínima", "High and low"),
    FEELS_LIKE("Sensación térmica", "Feels like"),
    WIND("Viento", "Wind"),
    HUMIDITY("Humedad", "Humidity"),
    RAIN("Lluvia de hoy", "Rain today"),
    SUN("Amanecer o atardecer", "Sunrise or sunset"),
    ;

    val label: String get() = tr(es, en)
}

/**
 * Apariencia de la app, guardada en SharedPreferences. Es estado de Compose: al cambiarla, todas
 * las pantallas abiertas se repintan sin reiniciar.
 */
object Appearance {
    private const val FILE = "clima"
    private const val MODE = "tema"
    private const val DYNAMIC = "colores_dinamicos"
    private const val ACCENT = "acento"
    private const val COMPACT = "compacto"
    private const val HERO_SIZE = "temperatura_tamano"
    private const val HERO_LINES = "temperatura_datos"
    private const val SKY = "cielo_movimiento"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var accent by mutableStateOf(Accent.WALLPAPER)
        private set
    /** Pronóstico más apretado: menos aire entre tarjetas, encabezado más bajo y una semana. */
    var compact by mutableStateOf(false)
        private set
    var heroSize by mutableStateOf(HeroSize.MEDIUM)
        private set
    var heroLines by mutableStateOf(setOf(HeroLine.HIGH_LOW, HeroLine.FEELS_LIKE))
        private set
    var skyMotion by mutableStateOf(SkyMotion.FULL)
        private set
    private var loaded = false

    /** Material You solo existe desde Android 12: antes, "del fondo" queda en azul. */
    val dynamicColors: Boolean get() = accent == Accent.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        mode = enumOf(prefs.getString(MODE, null), ThemeMode.SYSTEM)
        // Antes había solo un interruptor de colores dinámicos: apagado equivale a azul.
        accent = enumOf(prefs.getString(ACCENT, null), if (prefs.getBoolean(DYNAMIC, true)) Accent.WALLPAPER else Accent.BLUE)
        compact = prefs.getBoolean(COMPACT, false)
        heroSize = enumOf(prefs.getString(HERO_SIZE, null), HeroSize.MEDIUM)
        heroLines = prefs.getStringSet(HERO_LINES, null)
            ?.mapNotNull { name -> HeroLine.entries.firstOrNull { it.name == name } }?.toSet()
            ?: setOf(HeroLine.HIGH_LOW, HeroLine.FEELS_LIKE)
        skyMotion = enumOf(prefs.getString(SKY, null), SkyMotion.FULL)
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        prefs(context).edit().putString(MODE, value.name).apply()
    }

    fun setAccent(context: Context, value: Accent) {
        accent = value
        prefs(context).edit().putString(ACCENT, value.name).apply()
    }

    fun setCompact(context: Context, value: Boolean) {
        compact = value
        prefs(context).edit().putBoolean(COMPACT, value).apply()
    }

    fun setHeroSize(context: Context, value: HeroSize) {
        heroSize = value
        prefs(context).edit().putString(HERO_SIZE, value.name).apply()
    }

    fun setHeroLine(context: Context, line: HeroLine, shown: Boolean) {
        heroLines = if (shown) heroLines + line else heroLines - line
        prefs(context).edit().putStringSet(HERO_LINES, heroLines.map { it.name }.toSet()).apply()
    }

    fun setSkyMotion(context: Context, value: SkyMotion) {
        skyMotion = value
        prefs(context).edit().putString(SKY, value.name).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}

/** Paletas de acento fijo: solo cambia la familia del color principal, el resto queda neutro. */
private fun ColorScheme.withAccent(accent: Accent, dark: Boolean): ColorScheme {
    // primary, onPrimary, primaryContainer, onPrimaryContainer (claro y oscuro).
    val tones: List<Long> = when (accent) {
        Accent.WALLPAPER, Accent.BLUE -> return this
        Accent.GREEN -> if (dark) listOf(0xFF96D59E, 0xFF003915, 0xFF135226, 0xFFB1F1B8) else listOf(0xFF2E6B3A, 0xFFFFFFFF, 0xFFB1F1B8, 0xFF00210A)
        Accent.ORANGE -> if (dark) listOf(0xFFFFB77C, 0xFF4C2700, 0xFF6D3A00, 0xFFFFDCC2) else listOf(0xFF8F4C00, 0xFFFFFFFF, 0xFFFFDCC2, 0xFF2E1500)
        Accent.PINK -> if (dark) listOf(0xFFFFB0CF, 0xFF5F1139, 0xFF7E2551, 0xFFFFD8E7) else listOf(0xFF9C3D6A, 0xFFFFFFFF, 0xFFFFD8E7, 0xFF3E0022)
        Accent.PURPLE -> if (dark) listOf(0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF) else listOf(0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D)
    }
    return copy(
        primary = Color(tones[0]),
        onPrimary = Color(tones[1]),
        primaryContainer = Color(tones[2]),
        onPrimaryContainer = Color(tones[3]),
        surfaceTint = Color(tones[0]),
    )
}

/** Si la app está en oscuro (por el sistema o por elección). */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ClimaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    remember { Appearance.load(context) }
    val dark = when (Appearance.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (Appearance.dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        (if (dark) DarkColors else LightColors).withAccent(Appearance.accent, dark)
    }
    // Íconos de la barra de estado y de navegación según el tema. La pantalla del pronóstico los
    // vuelve a poner claros, porque ahí siempre hay cielo detrás.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, typography = ClimaTypography, content = content)
    }
}
