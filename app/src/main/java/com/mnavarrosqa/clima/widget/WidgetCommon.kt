package com.mnavarrosqa.clima.widget

import androidx.glance.LocalSize
import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.Lang
import com.mnavarrosqa.clima.data.Units
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
        WidgetStyle.SKY -> skyWidgetColors(snapshot, LocalSize.current.width.value, LocalSize.current.height.value)
    }
}

/**
 * Colores del estilo Cielo para un recuadro de [widthDp] × [heightDp]: el cielo de esa ciudad de
 * fondo y texto blanco. Aparte de [widgetColors] para que Ciudades pinte cada fila con su cielo.
 * Sin [snapshot], un cielo parcialmente nublado de día (el del widget vacío).
 */
fun skyWidgetColors(snapshot: WeatherSnapshot?, widthDp: Float, heightDp: Float): WidgetColors {
    val current = snapshot?.current
    val kind = current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY
    return WidgetColors(
        WidgetStyle.SKY, null, White, WhiteVariant, WhiteChip, White,
        transparent = true,
        backgroundImage = ImageProvider(WidgetBitmaps.skyScene(snapshot, kind, current?.isDay ?: true, widthDp, heightDp)),
    )
}

/** Texto claro sin fondo propio: para lo que va sobre recuadros de cielo (las filas de Ciudades). */
fun overSkyColors(): WidgetColors =
    WidgetColors(WidgetStyle.SKY, null, White, WhiteVariant, WhiteChip, White, transparent = true)

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

/**
 * Widget sin pronóstico todavía, con el estilo por defecto de ese widget (el cielo, en los que lo
 * usan) para que no aparezca primero con el del sistema. Dice por qué está vacío: si es "donde
 * estoy" sin permiso de ubicación, pide elegir una ciudad (abre la app); si no, está cargando y
 * tocarlo actualiza.
 */
@Composable
fun EmptyWidget(default: WidgetStyle = WidgetStyle.SYSTEM) {
    val context = LocalContext.current
    val colors = widgetColors(null, default)
    val placeId = widgetPlaceId()
    val needsCity = placeId == CURRENT_PLACE_ID && !hasLocationPermission(context)
    val small = LocalSize.current.height < 90.dp
    val action = if (needsCity) openPlace(placeId) else actionRunCallback<RefreshAction>()
    WidgetSurface(colors) {
        Column(
            modifier = GlanceModifier.fillMaxSize().clickable(action).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!small) {
                Image(
                    provider = ImageProvider(if (needsCity) R.drawable.ic_widget_location else R.drawable.ic_refresh),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.text),
                    modifier = GlanceModifier.size(28.dp),
                )
                Spacer(GlanceModifier.height(6.dp))
            }
            Text(
                if (needsCity) tr("Tocá para elegir una ciudad", "Tap to choose a city") else tr("Cargando…", "Loading…"),
                maxLines = 2,
                style = TextStyle(
                    color = colors.text,
                    fontSize = WidgetType.rowLabel,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

/** Si la app puede usar la ubicación (aproximada alcanza). */
private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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

/**
 * Verdadero cuando el widget se arma como vista previa (al personalizarlo). Esa composición
 * termina recién cuando terminan sus efectos: escuchar la caché la dejaba esperando para siempre
 * y la vista previa quedaba vacía. En la vista previa se usa lo guardado, sin escuchar.
 */
val LocalWidgetPreview = staticCompositionLocalOf { false }

/** Pronóstico de la ciudad de este widget; se recompone cuando cambia la caché. */
@Composable
fun widgetSnapshot(): WeatherSnapshot? {
    val repository = WeatherRepository.get(LocalContext.current)
    val placeId = widgetPlaceId()
    if (LocalWidgetPreview.current) return repository.cached(placeId)?.withCurrentFromHours()
    val snapshot by remember(placeId) { repository.snapshots(placeId) }
        .collectAsState(repository.cached(placeId))
    // Si el sistema demoró las actualizaciones, la temperatura de ahora sale del pronóstico por
    // hora en vez de quedar la de hace horas.
    return snapshot?.withCurrentFromHours()
}

/**
 * Lo último que se dibujó en cada widget (por su id de Android), para no volver a dibujar los que
 * no cambiaron. Vive en memoria: si el proceso muere, la próxima vez se dibujan todos.
 */
private val drawnSignatures = ConcurrentHashMap<Int, String>()

/**
 * Redibuja los widgets puestos, todos a la vez. Con [force] (lo seguro, y lo que hacen los
 * cambios de unidades, tema, estilo o ciudades) se redibujan todos. Sin [force] (después de bajar
 * el pronóstico) se saltean los que mostrarían lo mismo: misma ciudad y misma descarga
 * (`fetchedAt`), mismos ajustes del widget y de la app, y dentro de la misma franja de tiempo
 * (5 min en Sol y Próxima hora, que cambian minuto a minuto; 30 min en el resto, por la hora
 * "Ahora" y la temperatura que sale del pronóstico por hora).
 */
suspend fun updateWeatherWidgets(context: Context, force: Boolean = true) {
    val app = context.applicationContext
    val manager = GlanceAppWidgetManager(app)
    val repository = WeatherRepository.get(app)
    val shared = runCatching { sharedSignature(app, repository) }.getOrNull()
    coroutineScope {
        WidgetCatalog.kinds.map { kind ->
            async {
                runCatching {
                    val widget = kind.widget() as GlanceAppWidget
                    // Los ids salen de Android por el receptor de este tipo, no de la lista de Glance:
                    // si esa lista mezcla tipos, un widget se dibujaba con el contenido de otro (la
                    // Burbuja aparecía como Indicador o Próxima hora según cuál terminaba último).
                    kind.placedIds(app).map { appWidgetId ->
                        async {
                            runCatching {
                                val glanceId = manager.getGlanceIdBy(appWidgetId)
                                val signature = shared?.let { base ->
                                    runCatching { widgetSignature(app, kind, glanceId, repository, base) }.getOrNull()
                                }
                                if (force || signature == null || drawnSignatures[appWidgetId] != signature) {
                                    widget.update(app, glanceId)
                                    if (signature != null) {
                                        drawnSignatures[appWidgetId] = signature
                                    } else {
                                        drawnSignatures.remove(appWidgetId)
                                    }
                                }
                                Unit
                            }
                        }
                    }.awaitAll()
                }
            }
        }.awaitAll()
    }
    ClimaTileService.requestUpdate(context)
    WidgetPreviews.publish(context)
}

/** Lo que comparten todos los widgets: unidades, idioma, modo noche, letra y la lista de ciudades. */
private fun sharedSignature(context: Context, repository: WeatherRepository): String {
    val config = context.resources.configuration
    val night = config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return listOf(
        Units.current.toString(),
        Lang.locale.toLanguageTag(),
        night.toString(),
        config.fontScale.toString(),
        repository.places().joinToString(";") { it.toString() },
    ).joinToString("|")
}

/** Firma de lo que mostraría un widget ahora; si es igual a la última, se puede saltear. */
private suspend fun widgetSignature(
    context: Context,
    kind: WidgetKind,
    glanceId: androidx.glance.GlanceId,
    repository: WeatherRepository,
    shared: String,
): String {
    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
    val now = System.currentTimeMillis()
    val minuteByMinute = kind.receiver == SolWidgetReceiver::class.java || kind.receiver == ProximaHoraWidgetReceiver::class.java
    val slot = now / ((if (minuteByMinute) 5L else 30L) * 60_000L)
    val data = if (kind.supportsCity) {
        val placeId = resolveWidgetPlace(context, prefs[WidgetPrefs.PLACE])
        "$placeId@${repository.cached(placeId)?.fetchedAt}"
    } else {
        repository.allCached().joinToString(",") { "${it.placeId}@${it.fetchedAt}" }
    }
    // Los carteles "Actualizando…" y "Sin conexión" vencen solos: se cuenta si siguen vigentes.
    val refreshing = (prefs[WidgetPrefs.REFRESHING_AT] ?: 0L) > now - 90_000
    val failed = (prefs[WidgetPrefs.REFRESH_FAILED_AT] ?: 0L) > now - 10 * 60_000
    return listOf(shared, prefs.asMap().toString(), data, slot.toString(), refreshing.toString(), failed.toString()).joinToString("|")
}
