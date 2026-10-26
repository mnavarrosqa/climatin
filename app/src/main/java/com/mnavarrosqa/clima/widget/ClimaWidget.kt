package com.mnavarrosqa.clima.widget

import androidx.glance.layout.fillMaxSize
import com.mnavarrosqa.clima.data.tr
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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.shortHour
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
            if (current == null) EmptyWidget() else ClimaContent(current)
        }
    }

    companion object {
        val TINY = DpSize(50.dp, 40.dp)
        val SQUARE = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(220.dp, 90.dp)
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
            size.height >= 330.dp * scale -> LargeContent(snapshot)
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

/** Máxima y mínima de hoy, en una línea ("Máx 25° Mín 12°"). */
private fun rangeText(snapshot: WeatherSnapshot): String? = snapshot.today()?.let {
    tr("Máx", "H") + " ${it.max.degrees()}  " + tr("Mín", "L") + " ${it.min.degrees()}"
}

/**
 * Chico (2×2), como el del Clima de Apple: la ciudad y la temperatura grande arriba; abajo el
 * ícono, el estado y la máxima y mínima.
 */
@Composable
private fun SquareContent(snapshot: WeatherSnapshot) {
    val colors = climaColors(snapshot)
    val current = snapshot.current
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            PlaceLabel(snapshot, colors, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                current.temperature.degrees(),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = 40.sp),
            )
            Spacer(GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp),
            )
            Text(
                WeatherCodes.description(current.code),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            rangeText(snapshot)?.takeIf { widgetShows(WidgetPart.RANGE) }?.let {
                Text(it, maxLines = 1, style = TextStyle(color = colors.text, fontSize = 12.sp))
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
            NowHeader(snapshot, colors, compact)
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

/** Ciudad y temperatura a la izquierda; ícono, estado y máxima y mínima a la derecha. */
@Composable
private fun NowHeader(snapshot: WeatherSnapshot, colors: WidgetColors, compact: Boolean) {
    val current = snapshot.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            PlaceLabel(snapshot, colors, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                current.temperature.degrees(),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = if (compact) 36.sp else 46.sp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Image(
                provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(if (compact) 26.dp else 32.dp),
            )
            Text(
                WeatherCodes.description(current.code),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            rangeText(snapshot)?.takeIf { widgetShows(WidgetPart.RANGE) }?.let {
                Text(it, maxLines = 1, style = TextStyle(color = colors.text, fontSize = 12.sp))
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
    val context = androidx.glance.LocalContext.current
    val density = context.resources.displayMetrics.density
    val today = snapshot.nowAtPlace().toLocalDate()
    // Cada día ocupa unos 30 dp; arriba van el encabezado y las horas (unos 200).
    val dayCount = ((size.height.value - 200f * scale) / (30f * scale)).toInt().coerceIn(3, 7)
    val days = snapshot.upcomingDays(dayCount)
    val weekMin = days.minOfOrNull { it.min } ?: 0.0
    val weekMax = days.maxOfOrNull { it.max } ?: 1.0
    // Lo que queda para la barra después del día, el ícono, la lluvia y las dos temperaturas.
    val barWidth = (size.width - 36.dp - 64.dp - 26.dp - 38.dp - 40.dp - 40.dp).coerceAtLeast(40.dp)
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            NowHeader(snapshot, colors, compact = false)
            Spacer(GlanceModifier.height(10.dp))
            HoursRow(snapshot, count = 6, colors = colors)
            Spacer(GlanceModifier.height(8.dp))
            // Una línea fina separa las horas de los días.
            Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(colors.chip))
            Spacer(GlanceModifier.height(4.dp))
            days.forEach { day ->
                val isToday = day.localDate == today
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(30.dp * scale).clickable(openDay(snapshot.placeId, day.date)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        dayLabel(day.localDate, today),
                        maxLines = 1,
                        modifier = GlanceModifier.width(64.dp),
                        style = TextStyle(color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    )
                    Image(
                        provider = ImageProvider(WeatherCodes.icon(day.code)),
                        contentDescription = WeatherCodes.description(day.code),
                        modifier = GlanceModifier.size(22.dp),
                    )
                    Text(
                        day.precipitationProbability?.takeIf { it >= 30 }?.let { "$it%" } ?: "",
                        maxLines = 1,
                        modifier = GlanceModifier.width(38.dp),
                        style = TextStyle(color = colors.textVariant, fontSize = 12.sp, textAlign = androidx.glance.text.TextAlign.Center),
                    )
                    Text(
                        day.min.degrees(),
                        modifier = GlanceModifier.width(40.dp),
                        style = TextStyle(color = colors.textVariant, fontSize = 14.sp, textAlign = androidx.glance.text.TextAlign.End),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Image(
                        provider = ImageProvider(
                            WidgetBitmaps.rangeBar(
                                (barWidth.value * density).toInt(),
                                (6 * density).toInt(),
                                weekMin,
                                weekMax,
                                day.min,
                                day.max,
                                snapshot.current.temperature.takeIf { isToday },
                            ),
                        ),
                        contentDescription = null,
                        contentScale = androidx.glance.layout.ContentScale.FillBounds,
                        modifier = GlanceModifier.width(barWidth).height(6.dp),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        day.max.degrees(),
                        modifier = GlanceModifier.width(36.dp),
                        style = TextStyle(color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}

/**
 * Fila de próximas horas, compartida con el widget de pronóstico. Sobre el cielo (o sin fondo)
 * va suelta, como en el Clima de Apple; sobre un fondo de color, en un recuadro.
 */
@Composable
fun HoursRow(snapshot: WeatherSnapshot, count: Int, colors: WidgetColors) {
    val hours = snapshot.upcomingHours(count)
    val loose = colors.transparent
    val textColor = if (loose) colors.text else colors.onChip
    var modifier = GlanceModifier
        .fillMaxWidth()
        .clickable(openDay(snapshot.placeId, snapshot.nowAtPlace().toLocalDate().toString()))
    if (!loose) modifier = modifier.background(colors.chip).cornerRadius(16.dp).padding(vertical = 6.dp)
    Row(modifier = modifier) {
        hours.forEachIndexed { index, hour ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (index == 0) tr("Ahora", "Now") else shortHour(hour.dateTime),
                    maxLines = 1,
                    style = TextStyle(color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                )
                Image(
                    provider = ImageProvider(WeatherCodes.icon(hour.code, hour.isDay)),
                    contentDescription = WeatherCodes.description(hour.code),
                    modifier = GlanceModifier.size(24.dp).padding(vertical = 2.dp),
                )
                Text(
                    hour.temperature.degrees(),
                    style = TextStyle(
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
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
