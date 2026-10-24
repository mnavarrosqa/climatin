package com.mnavarrosqa.clima.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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
import com.mnavarrosqa.clima.data.longDate
import com.mnavarrosqa.clima.work.WeatherWorker
import java.time.LocalDate

/** "De un vistazo": fecha y clima sin fondo, directo sobre el fondo de pantalla como en el Pixel. */
class VistazoWidget : GlanceAppWidget(), PreviewableWidget {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Body() }
    }

    @Composable
    override fun Body() {
        ClimaGlanceTheme { VistazoContent(widgetSnapshot()) }
    }
}

@Composable
private fun VistazoContent(snapshot: WeatherSnapshot?) {
    val colors = widgetColors(snapshot, default = WidgetStyle.TRANSPARENT)
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val date = snapshot?.nowAtPlace()?.toLocalDate() ?: LocalDate.now()
            Text(
                longDate(date),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Medium),
            )
            if (snapshot != null) {
                val current = snapshot.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                        contentDescription = null,
                        modifier = GlanceModifier.size(22.dp),
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    val place = snapshot.place?.takeIf { widgetShows(WidgetPart.PLACE) }?.let { " · $it" } ?: ""
                    Text(
                        "${current.temperature.degrees()} ${WeatherCodes.description(current.code)}$place",
                        maxLines = 1,
                        style = TextStyle(color = colors.text, fontSize = 16.sp),
                    )
                }
                Insights.headline(snapshot)?.takeIf { widgetShows(WidgetPart.INSIGHT) }?.let { headline ->
                    Text(
                        headline,
                        maxLines = 1,
                        style = TextStyle(color = colors.textVariant, fontSize = 14.sp),
                    )
                }
            }
        }
    }
}

class VistazoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VistazoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWorker.schedule(context)
    }
}
