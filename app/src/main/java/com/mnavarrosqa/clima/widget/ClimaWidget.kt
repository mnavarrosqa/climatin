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
 * Widget "Clima ahora": se adapta del 1x1 al 4x2 como los del Pixel. Usa el tamaño exacto para
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
            size.height >= 176.dp * scale -> WideContent(snapshot, compact = size.height < 220.dp * scale, showHours = true)
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
                style = TextStyle(color = colors.text, fontSize = 20.sp),
            )
        }
    }
}

/**
 * Chico (2×2), como el del Clima de Apple: la ciudad con el ícono al lado, la temperatura grande
 * (crece con el widget) y abajo el estado y la máxima y mínima. Si el widget es alto, también las
 * próximas horas: antes ese espacio quedaba vacío en el medio.
 */
@Composable
private fun SquareContent(snapshot: WeatherSnapshot) {
    val colors = climaColors(snapshot)
    val current = snapshot.current
    val size = LocalSize.current
    val scale = fontScale()
    val tall = size.height >= 200.dp * scale
    // La temperatura toma un tercio del ancho; se divide por la letra del sistema para que no se salga.
    val tempSize = ((size.width.value * 0.32f).coerceIn(40f, 64f) / scale).sp
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    PlaceLabel(snapshot, colors, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Image(
                    provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(if (tall) 34.dp else 28.dp),
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
                style = TextStyle(color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            todayRangeText(snapshot)?.takeIf { widgetShows(WidgetPart.RANGE) }?.let {
                Text(it, maxLines = 1, style = TextStyle(color = colors.textVariant, fontSize = 12.sp))
            }
            if (tall) {
                Spacer(GlanceModifier.height(10.dp))
                // Una hora cada unos 42 dp: en un 2×2 entran 3 o 4.
                HoursRow(snapshot, count = ((size.width.value - 28f) / 42f).toInt().coerceIn(3, 5), colors = colors)
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
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 14.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            NowHeader(snapshot, colors, if (compact) HeaderSize.COMPACT else HeaderSize.FULL)
            if (!showHours && !compact && widgetShows(WidgetPart.INSIGHT)) {
                // La frase del momento: lluvia, UV, cambio de temperatura, atardecer…
                Insights.headline(snapshot)?.let {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(it, maxLines = 2, style = TextStyle(color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium))
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
    // Cada día ocupa unos 30 dp; arriba van el encabezado y las horas (unos 200).
    val dayCount = ((size.height.value - 200f * scale) / (30f * scale)).toInt().coerceIn(3, 7)
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            NowHeader(snapshot, colors, HeaderSize.FULL)
            Spacer(GlanceModifier.height(10.dp))
            HoursRow(snapshot, count = 6, colors = colors)
            Spacer(GlanceModifier.height(8.dp))
            // Una línea fina separa las horas de los días.
            Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.chip))
            Spacer(GlanceModifier.height(4.dp))
            // El ancho real sin el padding (18 + 18): antes se restaban 244 dp de 212 fijos y
            // cada fila quedaba 4 dp más ancha que el widget.
            DayRangeList(snapshot, snapshot.upcomingDays(dayCount), colors, innerWidth = size.width - 36.dp, rowHeight = 30.dp * scale)
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
