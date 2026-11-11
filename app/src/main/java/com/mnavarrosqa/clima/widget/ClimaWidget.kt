package com.mnavarrosqa.clima.widget

import androidx.glance.layout.fillMaxSize
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mnavarrosqa.clima.data.Insights
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.work.WeatherWorker

/**
 * Widget "Clima ahora": se adapta del 1x1 al 4x4 como los del Pixel. Usa el tamaño exacto para
 * elegir el diseño según el espacio real y el tamaño de letra del teléfono, así nada se corta.
 */
class ClimaWidget : GlanceAppWidget(), PreviewableWidget {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Body() }
    }

    @Composable
    override fun Body() {
        ClimaGlanceTheme {
            val current = widgetSnapshot()
            if (current == null) EmptyWidget(WidgetStyle.SKY) else ClimaContent(current)
        }
    }

    companion object {
        val TINY = DpSize(50.dp, 40.dp)
        val SQUARE = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(220.dp, 90.dp)
        val LARGE_MIN_WIDTH = 260.dp
    }
}

@Composable
private fun ClimaContent(snapshot: WeatherSnapshot) {
    val size = LocalSize.current
    // Alturas que necesita cada diseño, corregidas por el tamaño de letra del sistema.
    val scale = fontScale()
    when {
        size.width >= ClimaWidget.WIDE.width -> when {
            // Grande (4×4): además, los próximos días, como el widget grande del Clima de Apple.
            // Con menos de 260 dp de ancho las filas de días no entran (día, mín, barra y máx).
            size.height >= 330.dp * scale && size.width >= ClimaWidget.LARGE_MIN_WIDTH -> LargeContent(snapshot)
            // Desde 220: encabezado grande y las horas.
            size.height >= 220.dp * scale -> WideContent(snapshot, compact = false, showHours = true)
            size.height >= 176.dp * scale -> WideContent(snapshot, compact = true, showHours = true)
            size.height >= 150.dp * scale -> WideContent(snapshot, compact = false, showHours = false)
            else -> WideContent(snapshot, compact = true, showHours = false)
        }
        size.height >= ClimaWidget.SQUARE.height && size.width >= ClimaWidget.SQUARE.width -> SquareContent(snapshot)
        else -> TinyContent(snapshot)
    }
}

/** Colores del widget Clima: por defecto, el cielo del clima de fondo. */
@Composable
private fun climaColors(snapshot: WeatherSnapshot) = widgetColors(snapshot, default = WidgetStyle.SKY)

@Composable
private fun TinyContent(snapshot: WeatherSnapshot) {
    val colors = climaColors(snapshot)
    val current = snapshot.current
    WidgetSurface(colors) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                contentDescription = WeatherCodes.description(current.code),
                modifier = GlanceModifier.size(28.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                current.temperature.degrees(),
                style = TextStyle(color = colors.text, fontSize = WidgetType.tempTiny),
            )
        }
    }
}

/** Con las horas, la temperatura del 2×2 se puede achicar hasta acá; más chica, mejor sin horas. */
private const val SQUARE_MIN_TEMP = 40f

/** Separación entre el texto y las horas del 2×2. */
private val SquareHoursGap = 10.dp

/**
 * Margen del cálculo del 2×2: el alto que informa el launcher no siempre es el real, y si las
 * horas no entran lo que se corta es justo su temperatura.
 */
private const val SQUARE_SLACK = 6f

/**
 * Chico (2×2), como el del Clima de Apple: la ciudad con el ícono al lado, la temperatura grande
 * (crece con el widget) y abajo el estado y la máxima y mínima. Si el widget es alto, también las
 * próximas horas: antes ese espacio quedaba vacío en el medio.
 *
 * Las horas van solo si entran de verdad: se suma el alto de cada parte y, si falta lugar, primero
 * se achica la temperatura y después sale la línea de máxima y mínima. Antes aparecían desde
 * 200 dp aunque el contenido pedía unos 235, y la temperatura de cada hora quedaba cortada.
 */
@Composable
private fun SquareContent(snapshot: WeatherSnapshot) {
    val colors = climaColors(snapshot)
    val current = snapshot.current
    val size = LocalSize.current
    val scale = fontScale()
    val rangeText = todayRangeText(snapshot)?.takeIf { widgetShows(WidgetPart.RANGE) }
    // La temperatura toma un tercio del ancho (en dp: al pasarla a sp se divide por la letra del sistema).
    val fullTemp = (size.width.value * 0.32f).coerceIn(SQUARE_MIN_TEMP, 64f)
    val captionLine = textLineHeight(WidgetType.caption, scale)
    val placeLine = textLineHeight(WidgetType.place, scale)
    // Encabezado: lo más alto entre el ⟳ (32 para el cálculo), el ícono del clima (32) y el nombre de la ciudad.
    val headerHeight = maxOf(WidgetTouch.tight.value, placeLine)
    // Todo menos la temperatura y la línea de máxima y mínima, con las horas incluidas.
    val fixedWithHours = WidgetPadding.small.value * 2 + headerHeight + captionLine +
        SquareHoursGap.value + hoursRowHeight(colors, scale) + SQUARE_SLACK

    /** La temperatura más grande (en dp) que deja entrar las horas; null si ni la mínima entra. */
    fun tempWithHours(range: Boolean): Float? {
        val rest = fixedWithHours + if (range) captionLine else 0f
        // La línea de la temperatura mide lo mismo con cualquier letra del sistema (escala 1).
        val fits = (size.height.value - rest) / textLineHeight(1.sp, 1f)
        return if (fits >= SQUARE_MIN_TEMP) minOf(fits, fullTemp) else null
    }
    val tempKeepingRange = if (rangeText != null) tempWithHours(range = true) else null
    val tempHours = tempKeepingRange ?: tempWithHours(range = false)
    val showHours = tempHours != null
    val showRange = rangeText != null && (!showHours || tempKeepingRange != null)
    val tempSize = ((tempHours ?: fullTemp) / scale).sp
    // El ⟳ de 40 dp solo si sobra alto después de repartirlo con botones de 32: en un 2×2 las
    // horas y la temperatura grande valen más que 8 dp de botón.
    val tempLine = textLineHeight(1.sp, 1f) * (tempHours ?: fullTemp)
    val used = if (showHours) {
        fixedWithHours + (if (showRange) captionLine else 0f) + tempLine
    } else {
        WidgetPadding.small.value * 2 + headerHeight + tempLine + captionLine + (if (showRange) captionLine else 0f) + SQUARE_SLACK
    }
    val buttonHeight = if (size.height.value - used >= maxOf(WidgetTouch.full.value, placeLine) - headerHeight) WidgetTouch.full else WidgetTouch.tight
    WidgetSurface(colors, modifier = GlanceModifier.padding(WidgetPadding.small)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    PlaceLabel(snapshot, colors, fontSize = WidgetType.place, fontWeight = FontWeight.Medium, buttonHeight = buttonHeight)
                }
                Image(
                    provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(if (showHours) 32.dp else 28.dp),
                )
            }
            Text(
                current.temperature.degrees(),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = tempSize),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                WeatherCodes.description(current.code),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = WidgetType.caption, fontWeight = FontWeight.Medium),
            )
            if (showRange && rangeText != null) {
                Text(rangeText, maxLines = 1, style = TextStyle(color = colors.textVariant, fontSize = WidgetType.range))
            }
            if (showHours) {
                Spacer(GlanceModifier.height(SquareHoursGap))
                // Una hora cada unos 42 dp ("Ahora" no entra en menos): en un 2×2 entran 3 o 4.
                val count = ((size.width.value - WidgetPadding.small.value * 2) / 42f).toInt().coerceIn(2, 5)
                HoursRow(snapshot, count = count, colors = colors)
            }
        }
    }
}

/**
 * Mediano (4×2): la ciudad y la temperatura a la izquierda, el estado a la derecha y abajo las
 * próximas horas sobre el cielo, sin recuadro.
 */
@Composable
private fun WideContent(snapshot: WeatherSnapshot, compact: Boolean, showHours: Boolean) {
    val colors = climaColors(snapshot)
    val scale = fontScale()
    val vertical = if (compact) WidgetPadding.verticalTight else WidgetPadding.vertical
    val header = if (compact) HeaderSize.COMPACT else HeaderSize.FULL
    // Lo que va debajo del encabezado: las horas, o la frase del momento (hasta dos líneas).
    val below = when {
        showHours -> hoursRowHeight(colors, scale)
        !compact -> textLineHeight(WidgetType.caption, scale) * 2
        else -> 0f
    }
    val buttonHeight = headerButton(header, LocalSize.current.height, vertical.value * 2 + below, scale)
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = WidgetPadding.horizontal, vertical = vertical)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            NowHeader(snapshot, colors, header, buttonHeight = buttonHeight)
            if (!showHours && !compact && widgetShows(WidgetPart.INSIGHT)) {
                // La frase del momento: lluvia, UV, cambio de temperatura, atardecer…
                Insights.headline(snapshot)?.let {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(it, maxLines = 2, style = TextStyle(color = colors.text, fontSize = WidgetType.caption, fontWeight = FontWeight.Medium))
                }
            }
            if (showHours) {
                Spacer(GlanceModifier.defaultWeight())
                HoursRow(snapshot, count = 6, colors = colors)
            }
        }
    }
}

/** Grande (4×4): lo del mediano y debajo los próximos días con su barra de temperatura. */
@Composable
private fun LargeContent(snapshot: WeatherSnapshot) {
    val colors = climaColors(snapshot)
    val size = LocalSize.current
    val scale = fontScale()
    // Cada día ocupa unos 30 dp; arriba van los márgenes, el encabezado con botones de 40 dp, las
    // horas y la línea (10 + 8 + 1 + 4). Antes era un 208 fijo, que solo valía para las horas sin recuadro.
    val top = WidgetPadding.vertical.value * 2 + headerHeight(HeaderSize.FULL, scale, WidgetTouch.full) +
        hoursRowHeight(colors, scale) + 23f
    val dayCount = ((size.height.value - top) / (30f * scale)).toInt().coerceIn(3, 7)
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = WidgetPadding.horizontal, vertical = WidgetPadding.vertical)) {
        // Dos grupos y no una sola lista: Glance corta una Column a los 10 hijos, y 6 de arriba
        // más 7 días eran 13 (se perdían los últimos días).
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                // El grande siempre tiene lugar: los días se cuentan con el encabezado de 40 dp.
                NowHeader(snapshot, colors, HeaderSize.FULL, buttonHeight = WidgetTouch.full)
                Spacer(GlanceModifier.height(10.dp))
                HoursRow(snapshot, count = 6, colors = colors)
                Spacer(GlanceModifier.height(8.dp))
                // Una línea fina separa las horas de los días.
                Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.chip))
                Spacer(GlanceModifier.height(4.dp))
            }
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                // El ancho real sin el padding de los lados.
                DayRangeList(
                    snapshot,
                    snapshot.upcomingDays(dayCount),
                    colors,
                    innerWidth = size.width - WidgetPadding.horizontal * 2,
                    rowHeight = 30.dp * scale,
                )
            }
        }
    }
}

class ClimaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClimaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWorker.schedule(context)
    }
}
