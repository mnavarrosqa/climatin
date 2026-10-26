package com.mnavarrosqa.clima.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.mnavarrosqa.clima.data.Insights
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.work.WeatherWorker

/** Widget "Pronóstico": ahora + próximos días; si es alto, también las próximas horas. */
class PronosticoWidget : GlanceAppWidget(), PreviewableWidget {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Body() }
    }

    @Composable
    override fun Body() {
        ClimaGlanceTheme {
            val current = widgetSnapshot()
            if (current == null) EmptyWidget(WidgetStyle.SKY) else PronosticoContent(current)
        }
    }

    companion object {
        val MEDIUM = DpSize(180.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

@Composable
private fun PronosticoContent(snapshot: WeatherSnapshot) {
    val colors = widgetColors(snapshot, default = WidgetStyle.SKY)
    val size = LocalSize.current
    val scale = fontScale()
    // El tamaño por defecto (4×3) suele medir 250–280 dp de alto: desde 230 ya entran las horas
    // con el encabezado compacto; con más alto, el grande y la frase del momento.
    val large = size.height >= 230.dp * scale
    val header = when {
        size.height >= 290.dp * scale -> HeaderSize.FULL
        size.height >= 170.dp * scale -> HeaderSize.COMPACT
        else -> HeaderSize.INLINE
    }
    val dayCount = if (size.width >= 300.dp) 5 else 4
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        // Ocupa todo el alto y los días van abajo, como en Clima: si no, el contenido quedaba
        // flotando en el medio del cielo.
        Column(modifier = GlanceModifier.fillMaxSize()) {
            NowHeader(snapshot, colors, header)
            if (large) {
                Insights.headline(snapshot)?.takeIf { size.height >= 300.dp * scale && widgetShows(WidgetPart.INSIGHT) }?.let {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        it,
                        maxLines = 1,
                        style = TextStyle(color = colors.text, fontSize = WidgetType.condition, fontWeight = FontWeight.Medium),
                    )
                }
                Spacer(GlanceModifier.height(8.dp))
                HoursRow(snapshot, count = if (size.width >= 300.dp) 6 else 5, colors = colors)
            }
            Spacer(GlanceModifier.defaultWeight())
            DaysRow(snapshot, dayCount, colors)
        }
    }
}

@Composable
private fun DaysRow(snapshot: WeatherSnapshot, count: Int, colors: WidgetColors) {
    val today = snapshot.nowAtPlace().toLocalDate()
    val days = snapshot.upcomingDays(count)
    // La lluvia va en una línea más, solo si algún día la tiene (así las columnas quedan parejas).
    val showRain = days.any { (it.precipitationProbability ?: 0) >= 30 }
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        days.forEach { day ->
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(openDay(snapshot.placeId, day.date)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    dayLabel(day.localDate, today),
                    maxLines = 1,
                    style = TextStyle(
                        color = colors.textVariant,
                        fontSize = WidgetType.caption,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
                Image(
                    provider = ImageProvider(WeatherCodes.icon(day.code)),
                    contentDescription = WeatherCodes.description(day.code),
                    modifier = GlanceModifier.size(28.dp).padding(vertical = 2.dp),
                )
                // Máxima y mínima en una línea ("25° 12°"), como en el Clima de Apple.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        day.max.degrees(),
                        maxLines = 1,
                        style = TextStyle(color = colors.text, fontSize = WidgetType.rowLabel, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Text(
                        day.min.degrees(),
                        maxLines = 1,
                        style = TextStyle(color = colors.textVariant, fontSize = WidgetType.rowLabel),
                    )
                }
                if (showRain) {
                    Text(
                        day.precipitationProbability?.takeIf { it >= 30 }?.let { "$it%" } ?: " ",
                        maxLines = 1,
                        style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption),
                    )
                }
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
