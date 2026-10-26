package com.mnavarrosqa.clima.widget

import androidx.glance.semantics.semantics
import androidx.glance.semantics.contentDescription
import com.mnavarrosqa.clima.data.tr
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
import androidx.glance.text.TextStyle
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.ClimaActivity
import com.mnavarrosqa.clima.ui.skyColors
import com.mnavarrosqa.clima.data.Moon
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.timeOf
import com.mnavarrosqa.clima.work.WeatherWorker
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Base de los widgets simples: tema, ciudad y estado vacío ya resueltos. */
abstract class SimpleWeatherWidget : GlanceAppWidget(), PreviewableWidget {
    override val sizeMode: SizeMode = SizeMode.Exact

    /** Estilo por defecto: el mismo que en WidgetCatalog, así el widget vacío ya se ve como el lleno. */
    open val defaultStyle: WidgetStyle get() = WidgetStyle.SYSTEM

    @Composable
    abstract fun Content(snapshot: WeatherSnapshot)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Body() }
    }

    @Composable
    override fun Body() {
        ClimaGlanceTheme {
            val snapshot = widgetSnapshot()
            if (snapshot == null) EmptyWidget(defaultStyle) else Content(snapshot)
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
    override val defaultStyle: WidgetStyle get() = WidgetStyle.COLOR

    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot, default = defaultStyle)
        // Los textos se miden con el diámetro (en dp) pero van en sp: se divide por el tamaño de
        // letra del sistema, si no con letra grande se salían de la forma.
        val scale = fontScale()
        val size = LocalSize.current
        val diameter = minOf(size.width, size.height)
        val current = snapshot.current
        val today = snapshot.today()
        // Forma "galletita" de Material 3 Expressive, dibujada como imagen. Va como imagen que
        // llena el widget (ajustada para no deformarse): con un tamaño fijo, el que informa el
        // launcher suele ser menor que el real y quedaba mucho borde vacío alrededor.
        val context = LocalContext.current
        val px = (diameter.value * context.resources.displayMetrics.density).toInt()
        val cookie: android.graphics.Bitmap? = when (colors.style) {
            WidgetStyle.TRANSPARENT -> null
            WidgetStyle.SKY -> WidgetBitmaps.cookie(
                px,
                skyColors(WeatherCodes.kind(current.code), current.isDay).map { it.toArgb() }.toIntArray(),
            )
            // El color ya trae el color fijo y la opacidad elegidos.
            else -> WidgetBitmaps.cookie(px, intArrayOf(colors.background?.getColor(context)?.toArgb() ?: WidgetBitmaps.fill(context, colors.style)))
        }
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(widgetTapAction()),
            contentAlignment = Alignment.Center,
        ) {
            if (cookie != null) {
                Image(
                    provider = ImageProvider(cookie),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                    contentDescription = WeatherCodes.description(current.code),
                    modifier = GlanceModifier.size(diameter * 0.4f),
                )
                Text(
                    current.temperature.degrees(),
                    style = TextStyle(
                        color = colors.text,
                        fontSize = (diameter.value * 0.27f / scale).sp,
                        fontWeight = FontWeight.Normal,
                    ),
                )
                if (today != null && diameter >= 100.dp && widgetShows(WidgetPart.RANGE)) {
                    Text(
                        "${today.max.degrees()} / ${today.min.degrees()}",
                        style = TextStyle(color = colors.textVariant, fontSize = ((diameter.value * 0.09f).coerceIn(12f, 18f) / scale).sp),
                    )
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
                        fontWeight = FontWeight.Normal,
                    ),
                )
                if (size.width >= 180.dp) {
                    Spacer(GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            WeatherCodes.description(current.code),
                            maxLines = 1,
                            style = TextStyle(color = colors.text, fontSize = WidgetType.rowLabel, fontWeight = FontWeight.Medium),
                        )
                        if (today != null && widgetShows(WidgetPart.RANGE)) {
                            Text(
                                "${today.max.degrees()} / ${today.min.degrees()}",
                                maxLines = 1,
                                style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption),
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
    override val defaultStyle: WidgetStyle get() = WidgetStyle.SKY

    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot, default = defaultStyle)
        val size = LocalSize.current
        // Cada día ocupa unos 30 dp y el encabezado lo suyo, más con letra grande: si no, con la
        // letra agrandada los días quedaban cortados. Bajo, el encabezado de una línea.
        val scale = fontScale()
        val header = if (size.height >= 220.dp * scale) HeaderSize.COMPACT else HeaderSize.INLINE
        val dayCount = ((size.height.value - header.reserve.value * scale - 24f - 6f) / (30f * scale)).toInt().coerceIn(2, 7)
        WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                NowHeader(snapshot, colors, header)
                Spacer(GlanceModifier.defaultWeight())
                // Mínima | barra | máxima, como Clima grande y las tarjetas de la app.
                DayRangeList(snapshot, snapshot.upcomingDays(dayCount), colors, innerWidth = size.width - 32.dp, rowHeight = 30.dp * scale)
            }
        }
    }
}

class SemanaWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SemanaWidget()
}

// --- Sol: arco de amanecer a atardecer con la posición del sol ---

class SolWidget : SimpleWeatherWidget() {
    // Con el cielo de fondo se ve el degradado del amanecer y el atardecer (SkyTime).
    override val defaultStyle: WidgetStyle get() = WidgetStyle.SKY

    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot, default = defaultStyle)
        val context = LocalContext.current
        val size = LocalSize.current
        val scale = fontScale()
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
        // De noche, la luna: su arco de salida a puesta y la fase.
        val moon = if (isDaytime || sunrise == null) null else moonPass(snapshot, now)
        val title: String
        val caption: String?
        val left: Pair<String, String>?
        val right: Pair<String, String>?
        if (sunrise == null || sunset == null || today == null) {
            title = tr("Sol", "Sun")
            caption = null
            left = null
            right = null
        } else if (isDaytime) {
            title = tr("Atardece en", "Sunset in") + " ${untilText(Duration.between(now, sunset))}"
            caption = dayLengthText(snapshot, sunrise, sunset)
            left = "↑ ${timeOf(today.sunrise)}" to tr("Sale a las ${timeOf(today.sunrise)}", "Rises at ${timeOf(today.sunrise)}")
            right = "↓ ${timeOf(today.sunset)}" to tr("Se pone a las ${timeOf(today.sunset)}", "Sets at ${timeOf(today.sunset)}")
        } else {
            val age = Moon.age(now.toLocalDate())
            val lit = (Moon.illumination(age) * 100).roundToInt()
            title = Moon.phaseName(now.toLocalDate())
            // Cuándo vuelve el sol: el amanecer de hoy (de madrugada) o el de mañana.
            val nextSunrise = if (now.isBefore(sunrise)) sunrise else snapshot.upcomingDays(2).getOrNull(1)?.let { LocalDateTime.parse(it.sunrise) }
            caption = tr("Iluminada $lit%", "$lit% lit") +
                (nextSunrise?.let { tr(" · amanece a las ${shortHour(it)}", " · sunrise at ${shortHour(it)}") } ?: "")
            left = moon?.moonrise?.let { "↑ ${shortHour(it)}" to tr("La luna sale a las ${shortHour(it)}", "Moonrise at ${shortHour(it)}") }
            right = moon?.moonset?.let { "↓ ${shortHour(it)}" to tr("La luna se pone a las ${shortHour(it)}", "Moonset at ${shortHour(it)}") }
        }
        val arcWidth = size.width - 32.dp
        val textHeight = (if (caption != null) 18f + 16f else 18f) + (if (left != null || right != null) 16f else 0f)
        val arcHeight = (size.height - 32.dp - textHeight.dp * scale).coerceAtLeast(32.dp)
        val density = context.resources.displayMetrics.density
        val widthPx = (arcWidth.value * density).roundToInt()
        val heightPx = (arcHeight.value * density).roundToInt()
        val bitmap = if (isDaytime || moon == null) {
            ChartBitmap.renderSunArc(context, progress, widthPx, heightPx, onTransparent = colors.transparent, accent = chartAccent())
        } else {
            ChartBitmap.renderMoonArc(
                context,
                moon.progress,
                Moon.age(now.toLocalDate()),
                southern = snapshot.latitude < 0,
                widthPx = widthPx,
                heightPx = heightPx,
                onTransparent = colors.transparent,
                accent = chartAccent(),
            )
        }
        WidgetSurface(colors, modifier = GlanceModifier.padding(16.dp)) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    title,
                    maxLines = 1,
                    style = TextStyle(color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                caption?.let {
                    Text(it, maxLines = 1, style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption))
                }
                Spacer(GlanceModifier.defaultWeight())
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = listOfNotNull(title, caption).joinToString(". "),
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxWidth().height(arcHeight),
                )
                if (left != null || right != null) {
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        // Las flechas se ven; TalkBack lee "Sale" y "Se pone" en vez de "flecha arriba".
                        Text(
                            left?.first ?: "",
                            modifier = GlanceModifier.defaultWeight().semantics { contentDescription = left?.second ?: "" },
                            style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption),
                        )
                        Text(
                            right?.first ?: "",
                            modifier = GlanceModifier.semantics { contentDescription = right?.second ?: "" },
                            style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption),
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

    /** "10 h 32 min de luz · +2 min": lo que dura el día y cuánto cambió desde ayer. */
    private fun dayLengthText(snapshot: WeatherSnapshot, sunrise: LocalDateTime, sunset: LocalDateTime): String {
        val length = Duration.between(sunrise, sunset).toMinutes()
        val base = tr("${length / 60} h ${length % 60} min de luz", "${length / 60} h ${length % 60} min of daylight")
        val yesterday = snapshot.yesterday() ?: return base
        val change = length - Duration.between(LocalDateTime.parse(yesterday.sunrise), LocalDateTime.parse(yesterday.sunset)).toMinutes()
        return when {
            change > 0 -> "$base · +$change min"
            change < 0 -> "$base · −${-change} min"
            else -> base
        }
    }
}

/** Paso de la luna por el cielo: cuándo sale y se pone, y por dónde va ([progress] -1 si no está). */
private class MoonPass(val moonrise: LocalDateTime?, val moonset: LocalDateTime?, val progress: Float)

/**
 * Salida y puesta de la luna alrededor de [now], con [Moon.altitude] cada 10 minutos (como la
 * tarjeta Luna de la app). Si ya salió: la última salida y la próxima puesta. Si no: la próxima
 * salida y la puesta que le sigue.
 */
private fun moonPass(snapshot: WeatherSnapshot, now: LocalDateTime): MoonPass {
    val zone = snapshot.zone
    val latitude = snapshot.latitude
    val longitude = snapshot.longitude
    val step = 10L
    val start = now.minusHours(14)
    val count = (44 * 60 / step).toInt() + 1
    val times = List(count) { start.plusMinutes(it * step) }
    val altitudes = times.map { Moon.altitude(it.atZone(zone).toInstant(), latitude, longitude) }
    // Momento del cruce por el horizonte entre dos muestras, interpolado.
    fun crossing(i: Int): LocalDateTime {
        val a = altitudes[i - 1]
        val b = altitudes[i]
        val fraction = (a / (a - b)).coerceIn(0.0, 1.0)
        return times[i - 1].plusSeconds((fraction * step * 60).toLong())
    }
    val rises = (1 until count).filter { altitudes[it - 1] < 0 && altitudes[it] >= 0 }.map(::crossing)
    val sets = (1 until count).filter { altitudes[it - 1] >= 0 && altitudes[it] < 0 }.map(::crossing)
    val up = Moon.altitude(now.atZone(zone).toInstant(), latitude, longitude) >= 0
    val rise = if (up) rises.lastOrNull { !it.isAfter(now) } else rises.firstOrNull { it.isAfter(now) }
    val set = if (up) sets.firstOrNull { it.isAfter(now) } else rise?.let { r -> sets.firstOrNull { it.isAfter(r) } }
    val progress = if (up && rise != null && set != null) {
        Duration.between(rise, now).seconds.toFloat() / Duration.between(rise, set).seconds.coerceAtLeast(1)
    } else {
        -1f
    }
    return MoonPass(rise, set, progress)
}

class SolWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SolWidget()
}
