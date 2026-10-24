package com.mnavarrosqa.clima.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.unit.ColorProvider
import com.mnavarrosqa.clima.MainActivity
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.longDate
import com.mnavarrosqa.clima.data.rainSoonText
import com.mnavarrosqa.clima.work.WeatherWorker
import java.time.LocalDate

/** "De un vistazo": fecha y clima sin fondo, directo sobre el fondo de pantalla como en el Pixel. */
class VistazoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            VistazoContent(widgetSnapshot())
        }
    }
}

private val White = ColorProvider(Color.White)

@Composable
private fun VistazoContent(snapshot: WeatherSnapshot?) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val date = snapshot?.nowAtPlace()?.toLocalDate() ?: LocalDate.now()
        Text(
            longDate(date),
            maxLines = 1,
            style = TextStyle(color = White, fontSize = 24.sp, fontWeight = FontWeight.Medium),
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
                val place = snapshot.place?.let { " · $it" } ?: ""
                Text(
                    "${current.temperature.degrees()} ${WeatherCodes.description(current.code)}$place",
                    maxLines = 1,
                    style = TextStyle(color = White, fontSize = 16.sp),
                )
            }
            snapshot.minutesUntilRain()?.let { minutes ->
                Text(
                    rainSoonText(minutes),
                    maxLines = 1,
                    style = TextStyle(color = White, fontSize = 14.sp),
                )
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
