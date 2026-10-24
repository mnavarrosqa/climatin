package com.mnavarrosqa.clima.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.rainSoonText
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.windFrom
import com.mnavarrosqa.clima.data.windText
import java.time.Duration
import kotlin.math.roundToInt

// --- Próxima hora: barras de lluvia cada 15 minutos ---

class ProximaHoraWidget : SimpleWeatherWidget() {
    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot)
        val context = LocalContext.current
        val size = LocalSize.current
        val now = snapshot.nowAtPlace()
        val minutely = snapshot.minutely
            .filter { !it.dateTime.isBefore(now.minusMinutes(14)) }
            .take(8)
        // Con datos cada 15 min se muestra la intensidad (2 mm ya es lluvia fuerte); si no, la probabilidad por hora.
        val bars = if (minutely.isNotEmpty() && minutely.any { it.precipitation > 0 }) {
            minutely.mapIndexed { i, m ->
                val minutes = Duration.between(now, m.dateTime).toMinutes().coerceAtLeast(0)
                (if (i == 0) "Ahora" else "${(minutes + 7) / 15 * 15}'") to (m.precipitation / 2.0).toFloat()
            }
        } else {
            snapshot.upcomingHours(6).mapIndexed { i, h ->
                (if (i == 0) "Ahora" else shortHour(h.dateTime)) to (h.precipitationProbability ?: 0) / 100f
            }
        }
        val raining = snapshot.current.precipitation >= 0.1
        val title = when {
            raining -> "Lloviendo ahora"
            snapshot.minutesUntilRain() != null -> rainSoonText(snapshot.minutesUntilRain()!!)
            else -> "Sin lluvia en las próximas 2 horas"
        }
        val chartHeight = (size.height - 32.dp - 40.dp * fontScale()).coerceAtLeast(40.dp)
        val chartWidth = size.width - 32.dp
        val density = context.resources.displayMetrics.density
        val bitmap = ChartBitmap.renderRainBars(
            context,
            bars,
            (chartWidth.value * density).roundToInt(),
            (chartHeight.value * density).roundToInt(),
            colors.transparent,
        )
        WidgetSurface(colors, modifier = GlanceModifier.padding(16.dp)) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    title,
                    maxLines = 1,
                    style = TextStyle(color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                PlaceLabel(snapshot, colors, fontSize = 12.sp)
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxWidth().height(chartHeight),
                )
            }
        }
    }
}

class ProximaHoraWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProximaHoraWidget()
}

// --- Ciudades: todas tus ciudades en una lista ---

class CiudadesWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = WeatherRepository.get(context)
        provideContent {
            ClimaGlanceTheme {
                val all by remember { repository.allSnapshots() }.collectAsState(repository.allCached())
                if (all.isEmpty()) EmptyWidget() else CiudadesContent(all)
            }
        }
    }
}

@Composable
private fun CiudadesContent(all: List<WeatherSnapshot>) {
    val colors = widgetColors(all.first())
    val size = LocalSize.current
    val rowHeight = 38.dp * fontScale()
    val count = ((size.height - 24.dp) / rowHeight).toInt().coerceIn(1, minOf(all.size, 6))
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            all.take(count).forEach { snapshot ->
                val current = snapshot.current
                val today = snapshot.today()
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(rowHeight).clickable(openPlace(snapshot.placeId)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (snapshot.placeId == CURRENT_PLACE_ID) "📍 ${snapshot.place ?: "Donde estoy"}" else snapshot.place ?: "",
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = colors.text, fontSize = 14.sp),
                    )
                    if (today != null && size.width >= 220.dp) {
                        Text(
                            "${today.max.degrees()} / ${today.min.degrees()}",
                            style = TextStyle(color = colors.textVariant, fontSize = 12.sp),
                        )
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    Image(
                        provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                        contentDescription = WeatherCodes.description(current.code),
                        modifier = GlanceModifier.size(24.dp),
                    )
                    Text(
                        current.temperature.degrees(),
                        modifier = GlanceModifier.width(42.dp),
                        style = TextStyle(
                            color = colors.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                        ),
                    )
                }
            }
        }
    }
}

class CiudadesWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CiudadesWidget()
}

// --- Indicador: un dato con medidor (UV, calidad del aire, humedad o viento) ---

enum class Metric(val label: String) {
    UV("Índice UV"),
    AIR("Calidad del aire"),
    HUMIDITY("Humedad"),
    WIND("Viento"),
    ;

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: UV
    }
}

class IndicadorWidget : SimpleWeatherWidget() {
    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot)
        val context = LocalContext.current
        val size = LocalSize.current
        val metric = Metric.from(currentState(WidgetPrefs.METRIC))
        val current = snapshot.current
        val diameter = (minOf(size.width, size.height) - 20.dp).coerceAtLeast(48.dp)
        val px = (diameter.value * context.resources.displayMetrics.density).roundToInt()

        val uv = current.uvIndex ?: snapshot.today()?.uvIndex ?: 0.0
        val aqi = snapshot.airQuality?.europeanAqi
        val (value, caption, bitmap) = when (metric) {
            Metric.UV -> Triple(
                "${uv.roundToInt()}",
                WeatherCodes.uvLabel(uv).replaceFirstChar { it.uppercase() },
                ChartBitmap.renderGauge(context, (uv / 11).toFloat(), levelColor(uv / 11), px, colors.transparent),
            )
            Metric.AIR -> Triple(
                aqi?.toString() ?: "—",
                aqi?.let(WeatherCodes::airQualityLabel) ?: "Sin datos",
                ChartBitmap.renderGauge(context, (aqi ?: 0) / 100f, levelColor((aqi ?: 0) / 100.0), px, colors.transparent),
            )
            Metric.HUMIDITY -> Triple(
                "${current.humidity}%",
                "Humedad",
                ChartBitmap.renderGauge(context, current.humidity / 100f, 0xFF42A5F5.toInt(), px, colors.transparent),
            )
            Metric.WIND -> Triple(
                windText(current.windSpeed).substringBefore(' '),
                current.windDirection?.let { "${windText(current.windSpeed).substringAfter(' ')} del ${windFrom(it)}" }
                    ?: windText(current.windSpeed).substringAfter(' '),
                ChartBitmap.renderCompass(context, (current.windDirection ?: 0.0).toFloat(), 0xFF26A69A.toInt(), px, colors.transparent),
            )
        }
        val small = diameter < 90.dp
        WidgetSurface(colors, modifier = GlanceModifier.padding(10.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "${metric.label}: $value $caption",
                    modifier = GlanceModifier.size(diameter),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        value,
                        style = TextStyle(
                            color = colors.text,
                            fontSize = (diameter.value * 0.24f).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    if (!small) {
                        Text(
                            caption,
                            maxLines = 1,
                            style = TextStyle(color = colors.textVariant, fontSize = 11.sp, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
        }
    }

    /** Verde, amarillo, naranja, rojo y violeta según el nivel (0 a 1). */
    private fun levelColor(level: Double): Int = when {
        level < 0.27 -> 0xFF4CAF50.toInt()
        level < 0.55 -> 0xFFFFC107.toInt()
        level < 0.73 -> 0xFFFF9800.toInt()
        level < 1.0 -> 0xFFF44336.toInt()
        else -> 0xFF9C27B0.toInt()
    }
}

class IndicadorWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IndicadorWidget()
}
