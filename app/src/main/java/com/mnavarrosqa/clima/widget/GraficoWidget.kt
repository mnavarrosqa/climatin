package com.mnavarrosqa.clima.widget

import com.mnavarrosqa.clima.data.tr
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.work.WeatherWorker
import kotlin.math.roundToInt

/** "Gráfico": la curva de temperatura de las próximas horas. */
class GraficoWidget : GlanceAppWidget(), PreviewableWidget {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Body() }
    }

    @Composable
    override fun Body() {
        ClimaGlanceTheme {
            val current = widgetSnapshot()
            if (current == null) EmptyWidget(WidgetStyle.SKY) else GraficoContent(current)
        }
    }

    companion object {
        val MIN = DpSize(250.dp, 110.dp)
    }
}

@Composable
private fun GraficoContent(snapshot: WeatherSnapshot) {
    // Por defecto, el cielo de fondo como Clima (WidgetCatalog tiene el mismo valor).
    val colors = widgetColors(snapshot, default = WidgetStyle.SKY)
    val context = LocalContext.current
    val size = LocalSize.current
    val scale = fontScale()
    val padding = 16.dp
    // El encabezado compacto de Clima si hay alto; en el 4×2 bajo, el de una línea.
    val header = if (size.height >= 170.dp * scale) HeaderSize.COMPACT else HeaderSize.INLINE
    // Con letra grande el encabezado crece: si no, tapaba el gráfico.
    val chartWidth = size.width - padding * 2
    val chartHeight = (size.height - padding * 2 - header.reserve * scale - 8.dp).coerceAtLeast(40.dp)
    val hourCount = (chartWidth.value / 44f).roundToInt().coerceIn(4, 12)
    val density = context.resources.displayMetrics.density
    val bitmap = ChartBitmap.render(
        context,
        snapshot.upcomingHours(hourCount),
        (chartWidth.value * density).roundToInt(),
        (chartHeight.value * density).roundToInt(),
        onTransparent = colors.transparent,
        accent = chartAccent(),
    )

    WidgetSurface(colors, modifier = GlanceModifier.padding(padding)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            NowHeader(snapshot, colors, header)
            Spacer(GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = tr("Temperatura de las próximas horas", "Temperature for the next hours"),
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
