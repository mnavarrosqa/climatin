package com.mnavarrosqa.clima.widget

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
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.work.WeatherWorker

/**
 * Widget "Clima ahora": se adapta del 1x1 al 4x2 como los del Pixel. Usa el tamaño exacto para
 * elegir el diseño según el espacio real y el tamaño de letra del teléfono, así nada se corta.
 */
class ClimaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ClimaGlanceTheme {
                val current = widgetSnapshot()
                if (current == null) EmptyWidget() else ClimaContent(current)
            }
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
            size.height >= 254.dp * scale -> WideContent(snapshot, compact = false, showHours = true)
            size.height >= 176.dp * scale -> WideContent(snapshot, compact = true, showHours = true)
            size.height >= 150.dp * scale -> WideContent(snapshot, compact = false, showHours = false)
            else -> WideContent(snapshot, compact = true, showHours = false)
        }
        size.height >= ClimaWidget.SQUARE.height && size.width >= ClimaWidget.SQUARE.width -> SquareContent(snapshot)
        else -> TinyContent(snapshot)
    }
}

@Composable
private fun TinyContent(snapshot: WeatherSnapshot) {
    val colors = widgetColors(snapshot)
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

@Composable
private fun SquareContent(snapshot: WeatherSnapshot) {
    val colors = widgetColors(snapshot)
    val current = snapshot.current
    WidgetSurface(colors, modifier = GlanceModifier.padding(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                contentDescription = WeatherCodes.description(current.code),
                modifier = GlanceModifier.size(48.dp),
            )
            Text(
                current.temperature.degrees(),
                style = TextStyle(color = colors.text, fontSize = 34.sp),
            )
            Text(
                snapshot.place ?: WeatherCodes.description(current.code),
                maxLines = 1,
                style = TextStyle(color = colors.textVariant, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun WideContent(snapshot: WeatherSnapshot, compact: Boolean, showHours: Boolean) {
    val colors = widgetColors(snapshot)
    val current = snapshot.current
    val today = snapshot.today()
    val minMax = today?.let { "Máx ${it.max.degrees()} · Mín ${it.min.degrees()}" }
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 20.dp, vertical = if (compact) 10.dp else 14.dp)) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    PlaceLabel(snapshot, colors, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (compact) {
                        // Temperatura y estado lado a lado para ahorrar alto.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                current.temperature.degrees(),
                                style = TextStyle(color = colors.text, fontSize = 34.sp),
                            )
                            Spacer(GlanceModifier.width(10.dp))
                            Column {
                                Text(
                                    WeatherCodes.description(current.code),
                                    maxLines = 1,
                                    style = TextStyle(color = colors.text, fontSize = 13.sp),
                                )
                                if (minMax != null) {
                                    Text(minMax, maxLines = 1, style = TextStyle(color = colors.textVariant, fontSize = 12.sp))
                                }
                            }
                        }
                    } else {
                        Text(
                            current.temperature.degrees(),
                            style = TextStyle(color = colors.text, fontSize = 44.sp),
                        )
                        Text(
                            WeatherCodes.description(current.code),
                            maxLines = 1,
                            style = TextStyle(color = colors.text, fontSize = 14.sp),
                        )
                        if (minMax != null) {
                            Text(minMax, maxLines = 1, style = TextStyle(color = colors.textVariant, fontSize = 12.sp))
                        }
                        // La frase del momento: lluvia, UV, cambio de temperatura, atardecer…
                        Insights.headline(snapshot)?.let {
                            Text(
                                it,
                                maxLines = 1,
                                style = TextStyle(color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
                Image(
                    provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                    contentDescription = WeatherCodes.description(current.code),
                    modifier = GlanceModifier.size(if (compact) 52.dp else 72.dp),
                )
            }
            if (showHours) {
                Spacer(GlanceModifier.height(if (compact) 8.dp else 12.dp))
                HoursRow(snapshot, count = 5, colors = colors)
            }
        }
    }
}

/** Fila de próximas horas, compartida con el widget de pronóstico. */
@Composable
fun HoursRow(snapshot: WeatherSnapshot, count: Int, colors: WidgetColors) {
    val hours = snapshot.upcomingHours(count)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(colors.chip)
            .cornerRadius(16.dp)
            .clickable(openDay(snapshot.placeId, snapshot.nowAtPlace().toLocalDate().toString()))
            .padding(vertical = 6.dp),
    ) {
        hours.forEachIndexed { index, hour ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (index == 0) "Ahora" else shortHour(hour.dateTime),
                    style = TextStyle(color = colors.onChip, fontSize = 11.sp),
                )
                Image(
                    provider = ImageProvider(WeatherCodes.icon(hour.code, hour.isDay)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(22.dp).padding(vertical = 1.dp),
                )
                Text(
                    hour.temperature.degrees(),
                    style = TextStyle(
                        color = colors.onChip,
                        fontSize = 13.sp,
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
