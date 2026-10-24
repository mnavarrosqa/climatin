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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.work.WeatherWorker
import kotlin.math.roundToInt

/** "Gráfico": la curva de temperatura de las próximas horas. */
class GraficoWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ClimaGlanceTheme {
                val current = widgetSnapshot()
                if (current == null) EmptyWidget() else GraficoContent(current)
            }
        }
    }

    companion object {
        val MIN = DpSize(250.dp, 110.dp)
    }
}

@Composable
private fun GraficoContent(snapshot: WeatherSnapshot) {
    val colors = widgetColors()
    val context = LocalContext.current
    val size = LocalSize.current
    val current = snapshot.current
    val padding = 16.dp
    val header = 44.dp
    val chartWidth = size.width - padding * 2
    val chartHeight = (size.height - padding * 2 - header - 8.dp).coerceAtLeast(56.dp)
    val hourCount = (chartWidth.value / 44f).roundToInt().coerceIn(4, 12)
    val density = context.resources.displayMetrics.density
    val bitmap = ChartBitmap.render(
        context,
        snapshot.upcomingHours(hourCount),
        (chartWidth.value * density).roundToInt(),
        (chartHeight.value * density).roundToInt(),
        onTransparent = colors.transparent,
    )

    WidgetSurface(colors, modifier = GlanceModifier.padding(padding)) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(header),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                    contentDescription = WeatherCodes.description(current.code),
                    modifier = GlanceModifier.size(36.dp),
                )
                Spacer(GlanceModifier.width(10.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        "${current.temperature.degrees()} ${WeatherCodes.description(current.code)}",
                        maxLines = 1,
                        style = TextStyle(
                            color = colors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        snapshot.place ?: "Tu ubicación",
                        maxLines = 1,
                        style = TextStyle(color = colors.textVariant, fontSize = 12.sp),
                    )
                }
                snapshot.today()?.let { today ->
                    Text(
                        "${today.max.degrees()} / ${today.min.degrees()}",
                        style = TextStyle(color = colors.textVariant, fontSize = 13.sp),
                    )
                }
            }
            Spacer(GlanceModifier.height(8.dp))
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Temperatura de las próximas horas",
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.fillMaxWidth().height(chartHeight),
            )
        }
    }
}

class GraficoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GraficoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWorker.schedule(context)
    }
}
