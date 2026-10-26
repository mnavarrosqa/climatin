package com.mnavarrosqa.clima.widget

import androidx.glance.LocalSize
import com.mnavarrosqa.clima.data.tr
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
import androidx.compose.ui.graphics.toArgb
import androidx.glance.currentState
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
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
import com.mnavarrosqa.clima.ClimaActivity
import com.mnavarrosqa.clima.tile.ClimaTileService
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.ui.theme.DarkColors
import com.mnavarrosqa.clima.ui.theme.LightColors

/**
 * En Android 12+ GlanceTheme toma los colores del fondo de pantalla (Material You). Si el widget
 * eligió un color fijo, usa esa paleta en cualquier versión.
 */
@Composable
fun ClimaGlanceTheme(content: @Composable () -> Unit) {
    val fixed = accentSchemes(WidgetAccent.from(currentState(WidgetOptionKeys.ACCENT)))
    if (fixed != null) {
        GlanceTheme(colors = ColorProviders(light = fixed.first, dark = fixed.second), content = content)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors), content = content)
    }
}

/**
 * El color fijo elegido al personalizar el widget, para los gráficos que se dibujan como imagen
 * (no pueden usar GlanceTheme). Null si sigue los colores del sistema.
 */
@Composable
fun chartAccent(): Int? {
    val (light, dark) = accentSchemes(WidgetAccent.from(currentState(WidgetOptionKeys.ACCENT))) ?: return null
    val night = (LocalContext.current.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    return (if (night) dark.primary else light.primary).toArgb()
}

/** Lo que hace tocar el widget, según lo elegido al personalizarlo. */
@Composable
fun widgetTapAction(placeId: String = widgetPlaceId()): androidx.glance.action.Action =
    when (WidgetTap.from(currentState(WidgetOptionKeys.TAP))) {
        WidgetTap.APP -> openPlace(placeId)
        WidgetTap.RADAR -> actionStartActivity<ClimaActivity>(actionParametersOf(DeepLink.PLACE to placeId, DeepLink.RADAR to true))
        WidgetTap.NEXT -> actionRunCallback<NextCityAction>()
        WidgetTap.REFRESH -> actionRunCallback<RefreshAction>()
    }

/** Widgets que se pueden dibujar fuera de la pantalla de inicio (vista previa al personalizar). */
interface PreviewableWidget {
    @Composable
    fun Body()
}

/** Estilo de fondo que elige cada widget en su configuración. */
enum class WidgetStyle(private val es: String, private val en: String) {
    SYSTEM("Sistema", "System"),
    COLOR("Color", "Color"),
    TRANSPARENT("Transparente", "Transparent"),
    SKY("Cielo", "Sky"),
    ;

    /** Se calcula al usarlo: si cambia el idioma de la app, el nombre cambia también. */
    val label: String get() = tr(es, en)

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
private val Ink = ColorProvider(Color(0xFF1B1D21))
private val InkVariant = ColorProvider(Color(0xFF1B1D21).copy(alpha = 0.75f))
private val InkChip = ColorProvider(Color.Black.copy(alpha = 0.10f))

/**
 * Si el fondo de pantalla es claro (el sistema lo marca como apto para texto oscuro). Se consulta
 * como mucho cada 5 minutos: es una llamada al sistema y los widgets se dibujan seguido.
 */
private object WallpaperTone {
    @Volatile private var checkedAt = 0L
    @Volatile private var dark = false

    fun wantsDarkText(context: android.content.Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
        val now = System.currentTimeMillis()
        if (now - checkedAt > 5 * 60_000L) {
            checkedAt = now
            dark = runCatching {
                val colors = android.app.WallpaperManager.getInstance(context).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
                ((colors?.colorHints ?: 0) and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            }.getOrDefault(false)
        }
        return dark
    }
}

@Composable
fun widgetColors(snapshot: WeatherSnapshot?, default: WidgetStyle = WidgetStyle.SYSTEM): WidgetColors {
    val c = GlanceTheme.colors
    val style = WidgetStyle.from(currentState(WidgetPrefs.STYLE), default)
    // Opacidad elegida: se resuelve el color del tema actual (claro u oscuro) y se le baja el alfa.
    val opacity = (currentState(WidgetOptionKeys.OPACITY) ?: 100).coerceIn(0, 100)
    val context = LocalContext.current
    // Se resuelven las dos variantes (claro y oscuro): así el widget cambia solo con el modo
    // noche, sin esperar a que la app lo vuelva a dibujar.
    fun faded(provider: ColorProvider): ColorProvider {
        if (opacity >= 100) return provider
        fun resolved(night: Boolean): androidx.compose.ui.graphics.Color {
            val config = android.content.res.Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            return provider.getColor(context.createConfigurationContext(config)).copy(alpha = opacity / 100f)
        }
        return androidx.glance.color.ColorProvider(day = resolved(false), night = resolved(true))
    }
    // Sin fondo propio (transparente, o casi sin opacidad), el texto va sobre el fondo de pantalla:
    // blanco sobre fondos oscuros y oscuro sobre fondos claros, para que siempre se lea.
    val overWallpaper = style == WidgetStyle.TRANSPARENT ||
        ((style == WidgetStyle.SYSTEM || style == WidgetStyle.COLOR) && opacity < 40)
    if (overWallpaper) {
        val darkText = WallpaperTone.wantsDarkText(context)
        val background = if (style == WidgetStyle.TRANSPARENT) null else faded(if (style == WidgetStyle.COLOR) c.primaryContainer else c.widgetBackground)
        return if (darkText) {
            // "transparent" pide dibujos claros (para fondos oscuros): acá van los del tema.
            WidgetColors(style, background, Ink, InkVariant, InkChip, Ink, transparent = false)
        } else {
            WidgetColors(style, background, White, WhiteVariant, WhiteChip, White, transparent = true)
        }
    }
    return when (style) {
        WidgetStyle.SYSTEM -> WidgetColors(
            style = style,
            background = faded(c.widgetBackground),
            text = c.onSurface,
            textVariant = c.onSurfaceVariant,
            chip = c.secondaryContainer,
            onChip = c.onSecondaryContainer,
            transparent = false,
        )
        WidgetStyle.COLOR -> WidgetColors(
            style = style,
            background = faded(c.primaryContainer),
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
                backgroundImage = ImageProvider(
                    WidgetBitmaps.skyScene(snapshot, kind, current?.isDay ?: true, LocalSize.current.width.value, LocalSize.current.height.value),
                ),
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
    val radius = WidgetCorners.from(currentState(WidgetOptionKeys.CORNERS)).radius(cornerRadius)
    var base = GlanceModifier.fillMaxSize().appWidgetBackground()
    if (colors.background != null) base = base.cornerRadius(radius).background(colors.background)
    if (colors.backgroundImage != null) {
        base = base.cornerRadius(radius).background(colors.backgroundImage, ContentScale.FillBounds)
    }
    val placeId = widgetPlaceId()
    val tap = widgetTapAction(placeId)
    Box(
        modifier = base
            .clickable(tap)
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
            tr("Abrí Climatin para ver el pronóstico", "Open Climatin to see the forecast"),
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
    /**
     * Cuándo empezó una actualización a pedido; el widget muestra "Actualizando…" un rato. Es una
     * hora y no un sí/no: si el proceso muere a mitad de camino, no queda trabado para siempre.
     */
    val REFRESHING_AT = longPreferencesKey("refreshing_at")
    /** Cuándo falló el último ⟳: el widget dice "Sin conexión" unos minutos. */
    val REFRESH_FAILED_AT = longPreferencesKey("refresh_failed_at")
}

/** Parámetros de los accesos directos: abren la app en una ciudad y un día. */
object DeepLink {
    val PLACE = ActionParameters.Key<String>("place")
    val DAY = ActionParameters.Key<String>("day")
    val RADAR = ActionParameters.Key<Boolean>("radar")
}

/** Acción que abre la app en una ciudad. */
fun openPlace(placeId: String) = actionStartActivity<ClimaActivity>(actionParametersOf(DeepLink.PLACE to placeId))

/** Acción que abre el detalle de un día en la app. */
fun openDay(placeId: String, date: String) =
    actionStartActivity<ClimaActivity>(actionParametersOf(DeepLink.PLACE to placeId, DeepLink.DAY to date))

/** Ciudad de este widget (si se borró, vuelve a "donde estoy"). */
@Composable
fun widgetPlaceId(): String = resolveWidgetPlace(LocalContext.current, currentState(WidgetPrefs.PLACE))

/** Pronóstico de la ciudad de este widget; se recompone cuando cambia la caché. */
@Composable
fun widgetSnapshot(): WeatherSnapshot? {
    val repository = WeatherRepository.get(LocalContext.current)
    val placeId = widgetPlaceId()
    val snapshot by remember(placeId) { repository.snapshots(placeId) }
        .collectAsState(repository.cached(placeId))
    // Si el sistema demoró las actualizaciones, la temperatura de ahora sale del pronóstico por
    // hora en vez de quedar la de hace horas.
    return snapshot?.withCurrentFromHours()
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
