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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.work.WeatherWorker

/** Widget "Pronóstico": ahora + próximos días; si es alto, también las próximas horas. */
class PronosticoWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ClimaGlanceTheme {
                val current = widgetSnapshot()
                if (current == null) EmptyWidget() else PronosticoContent(current)
            }
        }
    }

    companion object {
        val MEDIUM = DpSize(180.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

@Composable
private fun PronosticoContent(snapshot: WeatherSnapshot) {
    val colors = widgetColors()
    val size = LocalSize.current
    val large = size.height >= PronosticoWidget.LARGE.height
    val dayCount = if (size.width >= 300.dp) 5 else 4
    val current = snapshot.current
    WidgetSurface(colors, modifier = GlanceModifier.padding(16.dp)) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                    contentDescription = WeatherCodes.description(current.code),
                    modifier = GlanceModifier.size(40.dp),
                )
                Spacer(GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        "${current.temperature.degrees()} ${WeatherCodes.description(current.code)}",
                        maxLines = 1,
                        style = TextStyle(
                            color = colors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    PlaceLabel(snapshot, colors, fontSize = 12.sp)
                }
            }
            if (large) {
                Spacer(GlanceModifier.height(12.dp))
                HoursRow(snapshot, count = 5, colors = colors)
            }
            Spacer(GlanceModifier.height(12.dp))
            DaysRow(snapshot, dayCount)
        }
    }
}

@Composable
private fun DaysRow(snapshot: WeatherSnapshot, count: Int) {
    val colors = widgetColors()
    val today = snapshot.nowAtPlace().toLocalDate()
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        snapshot.upcomingDays(count).forEach { day ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    dayLabel(day.localDate, today).let { if (it == "Mañana") "Mañ." else it },
                    maxLines = 1,
                    style = TextStyle(
                        color = colors.textVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
                Image(
                    provider = ImageProvider(WeatherCodes.icon(day.code)),
                    contentDescription = WeatherCodes.description(day.code),
                    modifier = GlanceModifier.size(28.dp).padding(vertical = 2.dp),
                )
                Text(
                    day.max.degrees(),
                    style = TextStyle(
                        color = colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    day.min.degrees(),
                    style = TextStyle(color = colors.textVariant, fontSize = 12.sp),
                )
            }
        }
    }
}

class PronosticoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PronosticoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWorker.schedule(context)
    }
}
