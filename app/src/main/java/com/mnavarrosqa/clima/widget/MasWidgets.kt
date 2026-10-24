package com.mnavarrosqa.clima.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.MainActivity
import com.mnavarrosqa.clima.ui.skyColors
import com.mnavarrosqa.clima.data.Moon
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.timeOf
import com.mnavarrosqa.clima.work.WeatherWorker
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Base de los widgets simples: tema, ciudad y estado vacío ya resueltos. */
abstract class SimpleWeatherWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    @Composable
    abstract fun Content(snapshot: WeatherSnapshot)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ClimaGlanceTheme {
                val snapshot = widgetSnapshot()
                if (snapshot == null) EmptyWidget() else Content(snapshot)
            }
        }
    }
}

abstract class SimpleWeatherReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWorker.schedule(context)
    }
}

// --- Burbuja: círculo con ícono y temperatura, como el del Pixel ---

class BurbujaWidget : SimpleWeatherWidget() {
    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot, default = WidgetStyle.COLOR)
        val size = LocalSize.current
        val diameter = minOf(size.width, size.height)
        val current = snapshot.current
        val today = snapshot.today()
        // Forma "galletita" de Material 3 Expressive, dibujada como imagen.
        val context = LocalContext.current
        val px = (diameter.value * context.resources.displayMetrics.density).toInt()
        var circle = GlanceModifier.size(diameter)
        when (colors.style) {
            WidgetStyle.TRANSPARENT -> Unit
            WidgetStyle.SKY -> {
                val sky = skyColors(
                    WeatherCodes.kind(current.code),
                    current.isDay,
                ).map { it.toArgb() }.toIntArray()
                circle = circle.background(ImageProvider(WidgetBitmaps.cookie(px, sky)), ContentScale.FillBounds)
            }
            else -> circle = circle.background(
                ImageProvider(WidgetBitmaps.cookie(px, intArrayOf(WidgetBitmaps.fill(context, colors.style)))),
                ContentScale.FillBounds,
            )
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = circle, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                        contentDescription = WeatherCodes.description(current.code),
                        modifier = GlanceModifier.size(diameter * 0.34f),
                    )
                    Text(
                        current.temperature.degrees(),
                        style = TextStyle(
                            color = colors.text,
                            fontSize = (diameter.value * 0.22f).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    if (today != null && diameter >= 100.dp) {
                        Text(
                            "${today.max.degrees()} / ${today.min.degrees()}",
                            style = TextStyle(color = colors.textVariant, fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }
}

class BurbujaWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BurbujaWidget()
}

// --- Píldora: una línea con ícono, temperatura y estado ---

class PildoraWidget : SimpleWeatherWidget() {
    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot)
        val size = LocalSize.current
        val current = snapshot.current
        val today = snapshot.today()
        val iconSize = (size.height * 0.55f).coerceIn(24.dp, 48.dp)
        WidgetSurface(
            colors,
            modifier = GlanceModifier.padding(horizontal = 16.dp),
            cornerRadius = size.height / 2,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                    contentDescription = WeatherCodes.description(current.code),
                    modifier = GlanceModifier.size(iconSize),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    current.temperature.degrees(),
                    style = TextStyle(
                        color = colors.text,
                        fontSize = (iconSize.value * 0.62f).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (size.width >= 180.dp) {
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            WeatherCodes.description(current.code),
                            maxLines = 1,
                            style = TextStyle(color = colors.text, fontSize = 14.sp),
                        )
                        if (today != null) {
                            Text(
                                "${today.max.degrees()} / ${today.min.degrees()}",
                                maxLines = 1,
                                style = TextStyle(color = colors.textVariant, fontSize = 12.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

class PildoraWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PildoraWidget()
}

// --- Semana: lista vertical de los próximos días ---

class SemanaWidget : SimpleWeatherWidget() {
    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot)
        val size = LocalSize.current
        val current = snapshot.current
        // Cada día ocupa unos 30 dp; el encabezado, unos 56.
        val dayCount = ((size.height.value - 56f - 24f) / 30f).toInt().coerceIn(2, 7)
        val today = snapshot.nowAtPlace().toLocalDate()
        WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        PlaceLabel(snapshot, colors, fontSize = 12.sp)
                        Text(
                            current.temperature.degrees(),
                            style = TextStyle(color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    Image(
                        provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                        contentDescription = WeatherCodes.description(current.code),
                        modifier = GlanceModifier.size(40.dp),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                snapshot.upcomingDays(dayCount).forEach { day ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().height(30.dp).clickable(openDay(snapshot.placeId, day.date)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dayLabel(day.localDate, today),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = colors.text, fontSize = 14.sp),
                        )
                        Image(
                            provider = ImageProvider(WeatherCodes.icon(day.code)),
                            contentDescription = WeatherCodes.description(day.code),
                            modifier = GlanceModifier.size(22.dp),
                        )
                        Text(
                            day.max.degrees(),
                            modifier = GlanceModifier.width(40.dp),
                            style = TextStyle(
                                color = colors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                            ),
                        )
                        Text(
                            day.min.degrees(),
                            modifier = GlanceModifier.width(36.dp),
                            style = TextStyle(color = colors.textVariant, fontSize = 14.sp, textAlign = TextAlign.End),
                        )
                    }
                }
            }
        }
    }
}

class SemanaWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SemanaWidget()
}

// --- Sol: arco de amanecer a atardecer con la posición del sol ---

class SolWidget : SimpleWeatherWidget() {
    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot)
        val context = LocalContext.current
        val size = LocalSize.current
        val today = snapshot.today()
        val now = snapshot.nowAtPlace()
        val sunrise = today?.let { LocalDateTime.parse(it.sunrise) }
        val sunset = today?.let { LocalDateTime.parse(it.sunset) }
        val progress = if (sunrise != null && sunset != null) {
            Duration.between(sunrise, now).seconds.toFloat() / Duration.between(sunrise, sunset).seconds.coerceAtLeast(1)
        } else {
            -1f
        }
        val isDaytime = progress in 0f..1f
        val arcWidth = size.width - 32.dp
        val arcHeight = (size.height - 32.dp - 44.dp).coerceAtLeast(40.dp)
        val density = context.resources.displayMetrics.density
        val bitmap = ChartBitmap.renderSunArc(
            context,
            progress,
            (arcWidth.value * density).roundToInt(),
            (arcHeight.value * density).roundToInt(),
            onTransparent = colors.transparent,
        )
        val title = when {
            sunrise == null || sunset == null -> "Sol"
            isDaytime -> "Atardece en ${untilText(Duration.between(now, sunset))}"
            now.isBefore(sunrise) -> "Amanece en ${untilText(Duration.between(now, sunrise))}"
            else -> Moon.phaseName(now.toLocalDate())
        }
        WidgetSurface(colors, modifier = GlanceModifier.padding(16.dp)) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    title,
                    maxLines = 1,
                    style = TextStyle(color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxWidth().height(arcHeight),
                )
                if (today != null) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Text(
                            "↑ ${timeOf(today.sunrise)}",
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = colors.textVariant, fontSize = 12.sp),
                        )
                        Text(
                            "↓ ${timeOf(today.sunset)}",
                            style = TextStyle(color = colors.textVariant, fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }

    private fun untilText(duration: Duration): String {
        val minutes = duration.toMinutes().coerceAtLeast(0)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }
}

class SolWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SolWidget()
}
