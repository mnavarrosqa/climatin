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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

/**
 * Apariencia de la app, guardada en SharedPreferences. Es estado de Compose: al cambiarla, todas
 * las pantallas abiertas se repintan sin reiniciar.
 */
object Appearance {
    private const val FILE = "clima"
    private const val MODE = "tema"
    private const val DYNAMIC = "colores_dinamicos"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var dynamicColors by mutableStateOf(true)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(MODE, null) } ?: ThemeMode.SYSTEM
        dynamicColors = prefs.getBoolean(DYNAMIC, true)
    }

    fun setMode(context: Context, value: ThemeMode) {
        mode = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(MODE, value.name).apply()
    }

    fun setDynamicColors(context: Context, value: Boolean) {
        dynamicColors = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(DYNAMIC, value).apply()
    }
}

/** Si la app está en oscuro (por el sistema o por elección). */
val LocalDarkTheme = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        if (dark) DarkColors else LightColors
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
        // Movimiento de Material 3 Expressive: resortes con un poco más de vida en todos los componentes.
        MaterialTheme(
            colorScheme = colors,
            typography = ClimaTypography,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
