package com.mnavarrosqa.clima.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mnavarrosqa.clima.MainActivity
import com.mnavarrosqa.clima.tile.ClimaTileService
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.ui.theme.DarkColors
import com.mnavarrosqa.clima.ui.theme.LightColors

/** En Android 12+ GlanceTheme toma los colores del fondo de pantalla (Material You). */
@Composable
fun ClimaGlanceTheme(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors), content = content)
    }
}

/** Estilo de fondo que elige cada widget en su configuración. */
enum class WidgetStyle(val label: String) {
    SYSTEM("Sistema"),
    COLOR("Color"),
    TRANSPARENT("Transparente"),
    ;

    companion object {
        fun from(name: String?, default: WidgetStyle = SYSTEM): WidgetStyle =
            entries.firstOrNull { it.name == name } ?: default
    }
}

/** Colores del widget según su estilo. Sin fondo, el texto va en blanco como en el Pixel. */
data class WidgetColors(
    val background: ColorProvider?,
    val text: ColorProvider,
    val textVariant: ColorProvider,
    val chip: ColorProvider,
    val onChip: ColorProvider,
    val transparent: Boolean,
)

@Composable
fun widgetColors(default: WidgetStyle = WidgetStyle.SYSTEM): WidgetColors {
    val c = GlanceTheme.colors
    return when (WidgetStyle.from(currentState(WidgetPrefs.STYLE), default)) {
        WidgetStyle.SYSTEM -> WidgetColors(
            background = c.widgetBackground,
            text = c.onSurface,
            textVariant = c.onSurfaceVariant,
            chip = c.secondaryContainer,
            onChip = c.onSecondaryContainer,
            transparent = false,
        )
        WidgetStyle.COLOR -> WidgetColors(
            background = c.primaryContainer,
            text = c.onPrimaryContainer,
            textVariant = c.onPrimaryContainer,
            chip = c.primary,
            onChip = c.onPrimary,
            transparent = false,
        )
        WidgetStyle.TRANSPARENT -> WidgetColors(
            background = null,
            text = ColorProvider(Color.White),
            textVariant = ColorProvider(Color.White.copy(alpha = 0.85f)),
            chip = ColorProvider(Color.White.copy(alpha = 0.18f)),
            onChip = ColorProvider(Color.White),
            transparent = true,
        )
    }
}

/** Contenedor redondeado del widget; tocarlo abre la app. */
@Composable
fun WidgetSurface(
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    var base = GlanceModifier.fillMaxSize().appWidgetBackground()
    if (colors.background != null) base = base.cornerRadius(cornerRadius).background(colors.background)
    Box(
        modifier = base
            .clickable(actionStartActivity<MainActivity>())
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun EmptyWidget() {
    val colors = widgetColors()
    WidgetSurface(colors, modifier = GlanceModifier.padding(16.dp)) {
        Text(
            "Abrí Climatin para ver el pronóstico",
            style = TextStyle(
                color = colors.text,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Ciudad y estilo de cada widget, guardados en el estado de Glance de esa instancia. */
object WidgetPrefs {
    val PLACE = stringPreferencesKey("place")
    val STYLE = stringPreferencesKey("style")
}

/** Pronóstico de la ciudad de este widget; se recompone cuando cambia la caché. */
@Composable
fun widgetSnapshot(): WeatherSnapshot? {
    val repository = WeatherRepository.get(LocalContext.current)
    val placeId = repository.resolvePlaceId(currentState(WidgetPrefs.PLACE))
    val snapshot by remember(placeId) { repository.snapshots(placeId) }
        .collectAsState(repository.cached(placeId))
    return snapshot
}

suspend fun updateWeatherWidgets(context: Context) {
    runCatching { ClimaWidget().updateAll(context) }
    runCatching { PronosticoWidget().updateAll(context) }
    runCatching { VistazoWidget().updateAll(context) }
    runCatching { GraficoWidget().updateAll(context) }
    runCatching { BurbujaWidget().updateAll(context) }
    runCatching { PildoraWidget().updateAll(context) }
    runCatching { SemanaWidget().updateAll(context) }
    runCatching { SolWidget().updateAll(context) }
    ClimaTileService.requestUpdate(context)
}
