package com.mnavarrosqa.clima.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.layout.ContentScale
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
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
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
    SKY("Cielo"),
    ;

    companion object {
        fun from(name: String?, default: WidgetStyle = SYSTEM): WidgetStyle =
            entries.firstOrNull { it.name == name } ?: default
    }
}

/**
 * Colores del widget según su estilo. Sin fondo o sobre el cielo, el texto va en blanco como en el
 * Pixel ([transparent] = contenido claro).
 */
data class WidgetColors(
    val style: WidgetStyle,
    val background: ColorProvider?,
    val text: ColorProvider,
    val textVariant: ColorProvider,
    val chip: ColorProvider,
    val onChip: ColorProvider,
    val transparent: Boolean,
    val backgroundImage: ImageProvider? = null,
)

private val White = ColorProvider(Color.White)
private val WhiteVariant = ColorProvider(Color.White.copy(alpha = 0.85f))
private val WhiteChip = ColorProvider(Color.White.copy(alpha = 0.18f))

@Composable
fun widgetColors(snapshot: WeatherSnapshot?, default: WidgetStyle = WidgetStyle.SYSTEM): WidgetColors {
    val c = GlanceTheme.colors
    val style = WidgetStyle.from(currentState(WidgetPrefs.STYLE), default)
    return when (style) {
        WidgetStyle.SYSTEM -> WidgetColors(
            style = style,
            background = c.widgetBackground,
            text = c.onSurface,
            textVariant = c.onSurfaceVariant,
            chip = c.secondaryContainer,
            onChip = c.onSecondaryContainer,
            transparent = false,
        )
        WidgetStyle.COLOR -> WidgetColors(
            style = style,
            background = c.primaryContainer,
            text = c.onPrimaryContainer,
            textVariant = c.onPrimaryContainer,
            chip = c.primary,
            onChip = c.onPrimary,
            transparent = false,
        )
        WidgetStyle.TRANSPARENT -> WidgetColors(style, null, White, WhiteVariant, WhiteChip, White, transparent = true)
        // El fondo es el cielo del clima actual, como el widget del Pixel.
        WidgetStyle.SKY -> {
            val current = snapshot?.current
            val kind = current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY
            WidgetColors(
                style, null, White, WhiteVariant, WhiteChip, White,
                transparent = true,
                backgroundImage = ImageProvider(WidgetBitmaps.sky(kind, current?.isDay ?: true)),
            )
        }
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
    if (colors.backgroundImage != null) {
        base = base.cornerRadius(cornerRadius).background(colors.backgroundImage, ContentScale.FillBounds)
    }
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
    val colors = widgetColors(null)
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

/** Tamaño de letra del sistema (1 = normal) para reservar el alto que ocupan los textos. */
@Composable
fun fontScale(): Float = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)

/** Ciudad y estilo de cada widget, guardados en el estado de Glance de esa instancia. */
object WidgetPrefs {
    val PLACE = stringPreferencesKey("place")
    val STYLE = stringPreferencesKey("style")
    /** Dato que muestra el widget Indicador. */
    val METRIC = stringPreferencesKey("metric")
    /** Mientras se actualiza a pedido, el widget muestra "Actualizando…". */
    val REFRESHING = booleanPreferencesKey("refreshing")
}

/** Parámetros de los accesos directos: abren la app en una ciudad y un día. */
object DeepLink {
    val PLACE = ActionParameters.Key<String>("place")
    val DAY = ActionParameters.Key<String>("day")
}

/** Acción que abre la app en una ciudad. */
fun openPlace(placeId: String) = actionStartActivity<MainActivity>(actionParametersOf(DeepLink.PLACE to placeId))

/** Acción que abre el detalle de un día en la app. */
fun openDay(placeId: String, date: String) =
    actionStartActivity<MainActivity>(actionParametersOf(DeepLink.PLACE to placeId, DeepLink.DAY to date))

/** Ciudad de este widget (si se borró, vuelve a "donde estoy"). */
@Composable
fun widgetPlaceId(): String =
    WeatherRepository.get(LocalContext.current).resolvePlaceId(currentState(WidgetPrefs.PLACE))

/** Pronóstico de la ciudad de este widget; se recompone cuando cambia la caché. */
@Composable
fun widgetSnapshot(): WeatherSnapshot? {
    val repository = WeatherRepository.get(LocalContext.current)
    val placeId = widgetPlaceId()
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
    runCatching { ProximaHoraWidget().updateAll(context) }
    runCatching { CiudadesWidget().updateAll(context) }
    runCatching { IndicadorWidget().updateAll(context) }
    ClimaTileService.requestUpdate(context)
    WidgetPreviews.publish(context)
}
