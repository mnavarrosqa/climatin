package com.mnavarrosqa.clima.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
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
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.work.WeatherWorker

/** Widget "Clima ahora": se adapta del 1x1 al 4x2 como los del Pixel. */
class ClimaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(TINY, SQUARE, WIDE, WIDE_TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WeatherRepository.get(context)
        val initial = repository.cached()
        provideContent {
            val snapshot by repository.snapshots.collectAsState(initial)
            ClimaGlanceTheme {
                val current = snapshot
                if (current == null) EmptyWidget() else ClimaContent(current)
            }
        }
    }

    companion object {
        val TINY = DpSize(50.dp, 40.dp)
        val SQUARE = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(220.dp, 90.dp)
        val WIDE_TALL = DpSize(250.dp, 180.dp)
    }
}

@Composable
private fun ClimaContent(snapshot: WeatherSnapshot) {
    val size = LocalSize.current
    when {
        size.width >= ClimaWidget.WIDE_TALL.width && size.height >= ClimaWidget.WIDE_TALL.height ->
            WideContent(snapshot, showHours = true)
        size.width >= ClimaWidget.WIDE.width -> WideContent(snapshot, showHours = false)
        size.height >= ClimaWidget.SQUARE.height && size.width >= ClimaWidget.SQUARE.width -> SquareContent(snapshot)
        else -> TinyContent(snapshot)
    }
}

@Composable
private fun TinyContent(snapshot: WeatherSnapshot) {
    val current = snapshot.current
    WidgetSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                contentDescription = WeatherCodes.description(current.code),
                modifier = GlanceModifier.size(28.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                current.temperature.degrees(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp),
            )
        }
    }
}

@Composable
private fun SquareContent(snapshot: WeatherSnapshot) {
    val current = snapshot.current
    WidgetSurface(modifier = GlanceModifier.padding(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                contentDescription = WeatherCodes.description(current.code),
                modifier = GlanceModifier.size(48.dp),
            )
            Text(
                current.temperature.degrees(),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 34.sp),
            )
            Text(
                snapshot.place ?: WeatherCodes.description(current.code),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun WideContent(snapshot: WeatherSnapshot, showHours: Boolean) {
    val current = snapshot.current
    val today = snapshot.today()
    WidgetSurface(modifier = GlanceModifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        snapshot.place ?: "Tu ubicación",
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        current.temperature.degrees(),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 44.sp),
                    )
                    Text(
                        WeatherCodes.description(current.code),
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                    )
                    if (today != null) {
                        Text(
                            "Máx ${today.max.degrees()} · Mín ${today.min.degrees()}",
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        )
                    }
                }
                Image(
                    provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                    contentDescription = WeatherCodes.description(current.code),
                    modifier = GlanceModifier.size(72.dp),
                )
            }
            if (showHours) {
                Spacer(GlanceModifier.height(12.dp))
                HoursRow(snapshot, count = 5)
            }
        }
    }
}

/** Fila de próximas horas, compartida con el widget de pronóstico. */
@Composable
fun HoursRow(snapshot: WeatherSnapshot, count: Int) {
    val hours = snapshot.upcomingHours(count)
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(16.dp)
            .padding(vertical = 8.dp),
    ) {
        hours.forEachIndexed { index, hour ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (index == 0) "Ahora" else shortHour(hour.dateTime),
                    style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 11.sp),
                )
                Image(
                    provider = ImageProvider(WeatherCodes.icon(hour.code, hour.isDay)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp).padding(vertical = 2.dp),
                )
                Text(
                    hour.temperature.degrees(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
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
