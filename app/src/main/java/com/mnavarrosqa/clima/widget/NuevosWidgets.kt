package com.mnavarrosqa.clima.widget

import com.mnavarrosqa.clima.data.humidityFeel
import com.mnavarrosqa.clima.data.pressureText
import com.mnavarrosqa.clima.data.tr
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
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
import com.mnavarrosqa.clima.data.Lang
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
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.ui.TempColors

// --- Próxima hora: barras de lluvia cada 15 minutos ---

class ProximaHoraWidget : SimpleWeatherWidget() {
    override val defaultStyle: WidgetStyle get() = WidgetStyle.SKY

    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot, default = defaultStyle)
        val context = LocalContext.current
        val size = LocalSize.current
        val scale = fontScale()
        val now = snapshot.nowAtPlace()
        // Cada valor es de los 15 minutos anteriores a su hora: se muestran los tramos que terminan después de ahora.
        val minutely = snapshot.minutely
            .filter { it.dateTime.isAfter(now) }
            .take(8)
        // Con datos cada 15 min se muestra la intensidad (2 mm ya es lluvia fuerte); si no, la probabilidad por hora.
        val bars = if (minutely.isNotEmpty() && minutely.any { it.precipitation > 0 }) {
            minutely.mapIndexed { i, m ->
                val minutes = Duration.between(now, m.dateTime.minusMinutes(15)).toMinutes().coerceAtLeast(0)
                (if (i == 0) tr("Ahora", "Now") else "${(minutes + 7) / 15 * 15}'") to (m.precipitation / 2.0).toFloat()
            }
        } else {
            // La probabilidad de cada hora es de la hora anterior: la barra se rotula con su comienzo.
            snapshot.upcomingHours(7).drop(1).mapIndexed { i, h ->
                (if (i == 0) tr("Ahora", "Now") else shortHour(h.dateTime.minusHours(1))) to (h.precipitationProbability ?: 0) / 100f
            }
        }
        val raining = snapshot.current.precipitation >= 0.1
        val minutesUntilRain = snapshot.minutesUntilRain()
        val title = when {
            raining -> tr("Lloviendo ahora", "Raining now")
            minutesUntilRain != null -> rainSoonText(minutesUntilRain)
            else -> tr("Sin lluvia en las próximas 2 horas", "No rain in the next 2 hours")
        }
        // Seco: en vez de un gráfico vacío, cuándo es la próxima lluvia de los próximos días.
        val dry = !raining && minutesUntilRain == null && bars.all { it.second < 0.2f }
        val chartHeight = (size.height - 32.dp - 40.dp * scale).coerceAtLeast(0.dp)
        // Bajo, el gráfico no se lee: queda solo el texto.
        val showChart = !dry && chartHeight >= 44.dp
        WidgetSurface(colors, modifier = GlanceModifier.padding(16.dp)) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    title,
                    maxLines = 1,
                    style = TextStyle(color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
                PlaceLabel(snapshot, colors, fontSize = WidgetType.caption)
                if (showChart) {
                    val chartWidth = size.width - 32.dp
                    val density = context.resources.displayMetrics.density
                    val bitmap = ChartBitmap.renderRainBars(
                        context,
                        bars,
                        (chartWidth.value * density).roundToInt(),
                        (chartHeight.value * density).roundToInt(),
                        colors.transparent,
                        accent = chartAccent(),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier.fillMaxWidth().height(chartHeight),
                    )
                } else if (dry) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        nextRainText(snapshot),
                        maxLines = 2,
                        style = TextStyle(color = colors.text, fontSize = WidgetType.rowLabel, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }

    /** "Próxima lluvia: jueves 60%" con el primer día con 30% o más, o "Sin lluvia a la vista". */
    private fun nextRainText(snapshot: WeatherSnapshot): String {
        val today = snapshot.nowAtPlace().toLocalDate()
        val day = snapshot.upcomingDays(7).firstOrNull { (it.precipitationProbability ?: 0) >= 30 }
            ?: return tr("Sin lluvia a la vista", "No rain in sight")
        val chance = day.precipitationProbability ?: 0
        val name = when (day.localDate) {
            today -> tr("hoy", "today")
            today.plusDays(1) -> tr("mañana", "tomorrow")
            else -> day.localDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Lang.locale).let {
                if (Lang.locale.language == "es") it else it.replaceFirstChar { c -> c.titlecase(Lang.locale) }
            }
        }
        return tr("Próxima lluvia: $name $chance%", "Next rain: $name $chance%")
    }
}

class ProximaHoraWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProximaHoraWidget()
}

// --- Ciudades: todas tus ciudades en una lista ---

class CiudadesWidget : GlanceAppWidget(), PreviewableWidget {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Body() }
    }

    @Composable
    override fun Body() {
        val repository = WeatherRepository.get(LocalContext.current)
        ClimaGlanceTheme {
            // En la vista previa, lo guardado sin escuchar (ver LocalWidgetPreview).
            val all = if (LocalWidgetPreview.current) {
                repository.allCached()
            } else {
                remember { repository.allSnapshots() }.collectAsState(repository.allCached()).value
            }
            if (all.isEmpty()) EmptyWidget(CIUDADES_DEFAULT_STYLE) else CiudadesContent(all)
        }
    }
}

@Composable
private fun CiudadesContent(all: List<WeatherSnapshot>) {
    val size = LocalSize.current
    val style = WidgetStyle.from(currentState(WidgetPrefs.STYLE), CIUDADES_DEFAULT_STYLE)
    val sky = style == WidgetStyle.SKY
    // Con el cielo, cada fila es su propio recuadro con el cielo de esa ciudad (como la lista del
    // Clima de Apple) y el fondo del widget queda transparente. Con los otros estilos, una lista.
    val colors = if (sky) overSkyColors() else widgetColors(all.first(), CIUDADES_DEFAULT_STYLE)
    val scale = fontScale()
    val rowHeight = 52.dp * scale
    val gap = if (sky) 6.dp else 2.dp
    val vertical = if (sky) 0.dp else 12.dp
    val horizontal = if (sky) 0.dp else 16.dp
    val count = ((size.height - vertical * 2 + gap) / (rowHeight + gap)).toInt().coerceIn(1, minOf(all.size, 6))
    val rowWidth = size.width - horizontal * 2
    WidgetSurface(colors, modifier = GlanceModifier.padding(horizontal = horizontal, vertical = vertical)) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
            all.take(count).forEachIndexed { index, snapshot ->
                if (index > 0) Spacer(GlanceModifier.height(gap))
                val rowColors = if (sky) skyWidgetColors(snapshot, rowWidth.value, rowHeight.value) else colors
                CityRow(snapshot, rowColors, rowHeight, wide = size.width >= 220.dp)
            }
        }
    }
}

/** Estilo por defecto de Ciudades (el mismo que en WidgetCatalog). */
private val CIUDADES_DEFAULT_STYLE = WidgetStyle.SKY

/** Una ciudad: nombre y estado a la izquierda; temperatura y máxima y mínima a la derecha. */
@Composable
private fun CityRow(snapshot: WeatherSnapshot, colors: WidgetColors, height: androidx.compose.ui.unit.Dp, wide: Boolean) {
    val current = snapshot.current
    val today = snapshot.today()
    val image = colors.backgroundImage
    var modifier = GlanceModifier.fillMaxWidth().height(height)
    if (image != null) modifier = modifier.cornerRadius(16.dp).background(image, ContentScale.FillBounds)
    modifier = modifier.clickable(openPlace(snapshot.placeId)).padding(horizontal = if (image != null) 12.dp else 0.dp, vertical = 2.dp)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Donde estás, con el ícono de ubicación (el emoji se leía en voz alta con TalkBack).
                if (snapshot.placeId == CURRENT_PLACE_ID) {
                    Image(
                        provider = ImageProvider(com.mnavarrosqa.clima.R.drawable.ic_widget_location),
                        contentDescription = tr("Mi ubicación", "My location"),
                        colorFilter = androidx.glance.ColorFilter.tint(colors.text),
                        modifier = GlanceModifier.size(12.dp),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                }
                Text(
                    if (snapshot.placeId == CURRENT_PLACE_ID) snapshot.place ?: tr("Mi ubicación", "My location") else snapshot.place ?: "",
                    maxLines = 1,
                    style = TextStyle(color = colors.text, fontSize = WidgetType.rowLabel, fontWeight = FontWeight.Medium),
                )
            }
            Text(
                WeatherCodes.description(current.code),
                maxLines = 1,
                style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                current.temperature.degrees(),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = 24.sp, textAlign = TextAlign.End),
            )
            if (today != null && wide && widgetShows(WidgetPart.RANGE)) {
                Text(
                    tr("Máx", "H") + " ${today.max.degrees()}  " + tr("Mín", "L") + " ${today.min.degrees()}",
                    maxLines = 1,
                    style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption),
                )
            }
        }
    }
}

class CiudadesWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CiudadesWidget()
}

// --- Indicador: un dato con medidor (UV, calidad del aire, humedad o viento) ---

enum class Metric(private val es: String, private val en: String, private val shortEs: String, private val shortEn: String) {
    UV("Índice UV", "UV index", "UV", "UV"),
    AIR("Calidad del aire", "Air quality", "Aire", "Air"),
    HUMIDITY("Humedad", "Humidity", "Humedad", "Humidity"),
    WIND("Viento", "Wind", "Viento", "Wind"),
    FEELS("Sensación", "Feels like", "Sensación", "Feels like"),
    RAIN("Lluvia hoy", "Rain today", "Lluvia", "Rain"),
    PRESSURE("Presión", "Pressure", "Presión", "Pressure"),
    DEW("Punto de rocío", "Dew point", "Rocío", "Dew point"),
    /** No es un dato: va mostrando uno distinto cada media hora. */
    ROTATE("Ir rotando", "Rotate", "", ""),
    ;

    val label: String get() = tr(es, en)

    /** Rótulo corto que va arriba del valor en el widget ("UV", "Aire"…). */
    val shortLabel: String get() = tr(shortEs, shortEn)

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: UV

        /**
         * El dato que toca mostrar en un Indicador que rota: cambia cada media hora, con la misma
         * cuenta que las ciudades que rotan. Sin calidad del aire, ese se saltea.
         */
        fun rotating(hasAir: Boolean): Metric {
            val shown = entries.filter { it != ROTATE && (hasAir || it != AIR) }
            val slot = System.currentTimeMillis() / 60_000 / ROTATE_MINUTES
            return shown[(slot % shown.size).toInt()]
        }
    }
}

class IndicadorWidget : SimpleWeatherWidget() {
    @Composable
    override fun Content(snapshot: WeatherSnapshot) {
        val colors = widgetColors(snapshot)
        val context = LocalContext.current
        val size = LocalSize.current
        val chosen = Metric.from(currentState(WidgetPrefs.METRIC))
        val metric = if (chosen == Metric.ROTATE) Metric.rotating(snapshot.airQuality?.europeanAqi != null) else chosen
        val current = snapshot.current
        val diameter = (minOf(size.width, size.height) - 20.dp).coerceAtLeast(48.dp)
        val px = (diameter.value * context.resources.displayMetrics.density).roundToInt()

        val uv = current.uvIndex ?: snapshot.today()?.uvIndex ?: 0.0
        val aqi = snapshot.airQuality?.europeanAqi
        val (value, caption, bitmap) = when (metric) {
            // ROTATE ya se resolvió arriba a un dato; queda acá solo para que el when sea completo.
            Metric.UV, Metric.ROTATE -> Triple(
                "${uv.roundToInt()}",
                WeatherCodes.uvLabel(uv).replaceFirstChar { it.uppercase() },
                ChartBitmap.renderGauge(context, (uv / 11).toFloat(), uvColor(uv), px, colors.transparent),
            )
            Metric.AIR -> Triple(
                aqi?.toString() ?: "—",
                aqi?.let(WeatherCodes::airQualityLabel) ?: tr("Sin datos", "No data"),
                ChartBitmap.renderGauge(context, (aqi ?: 0) / 100f, levelColor((aqi ?: 0) / 100.0), px, colors.transparent),
            )
            Metric.HUMIDITY -> Triple(
                "${current.humidity}%",
                humidityFeel(dewPoint(snapshot)),
                ChartBitmap.renderGauge(context, current.humidity / 100f, 0xFF42A5F5.toInt(), px, colors.transparent),
            )
            Metric.WIND -> Triple(
                windText(current.windSpeed).substringBefore(' '),
                current.windDirection?.let { "${windText(current.windSpeed).substringAfter(' ')} " + tr("del", "from") + " ${windFrom(it)}" }
                    ?: windText(current.windSpeed).substringAfter(' '),
                ChartBitmap.renderCompass(context, (current.windDirection ?: 0.0).toFloat(), 0xFF26A69A.toInt(), px, colors.transparent),
            )
            // De -10° a 40° en el medidor, con el color de la temperatura de la app (TempColors).
            Metric.FEELS -> Triple(
                current.feelsLike.degrees(),
                tr("Real", "Actual") + " ${current.temperature.degrees()}",
                ChartBitmap.renderGauge(context, ((current.feelsLike + 10) / 50).toFloat(), TempColors.of(current.feelsLike).toArgb(), px, colors.transparent),
            )
            Metric.RAIN -> {
                val today = snapshot.today()
                val chance = today?.precipitationProbability ?: 0
                Triple(
                    "$chance%",
                    today?.precipitationSum?.takeIf { it >= 0.1 }?.let { String.format(java.util.Locale.US, "%.1f mm", it) } ?: tr("Probabilidad", "Chance"),
                    ChartBitmap.renderGauge(context, chance / 100f, 0xFF4A90E2.toInt(), px, colors.transparent),
                )
            }
            // De 980 a 1040 hPa; abajo, si sube o baja respecto de 3 horas atrás.
            Metric.PRESSURE -> {
                val pressure = current.pressure
                val change = pressure?.let { p -> snapshot.pressureHoursAgo(3)?.let { p - it } }
                val text = pressure?.let(::pressureText) ?: "—"
                Triple(
                    text.substringBefore(' '),
                    text.substringAfter(' ', "") + when {
                        change == null -> ""
                        change <= -1 -> tr(" · bajando", " · falling")
                        change >= 1 -> tr(" · subiendo", " · rising")
                        else -> tr(" · estable", " · steady")
                    },
                    ChartBitmap.renderGauge(context, (((pressure ?: 1013.0) - 980) / 60).toFloat(), 0xFF7E57C2.toInt(), px, colors.transparent),
                )
            }
            Metric.DEW -> {
                val dew = dewPoint(snapshot)
                Triple(
                    dew.degrees(),
                    humidityFeel(dew),
                    ChartBitmap.renderGauge(context, ((dew + 5) / 30).toFloat(), TempColors.of(dew).toArgb(), px, colors.transparent),
                )
            }
        }
        val small = diameter < 90.dp
        // El valor se mide con el diámetro (dp) pero va en sp: con letra grande se saldría del medidor.
        val scale = fontScale()
        WidgetSurface(colors, modifier = GlanceModifier.padding(10.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = "${metric.label}: $value $caption",
                    modifier = GlanceModifier.size(diameter),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Qué se mide, arriba del valor: sin esto, "7 / Alto" no decía que era el UV.
                    if (diameter >= 64.dp) {
                        Text(
                            metric.shortLabel,
                            maxLines = 1,
                            style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                        )
                    }
                    Text(
                        value,
                        maxLines = 1,
                        style = TextStyle(
                            color = colors.text,
                            fontSize = (diameter.value * 0.24f / scale).sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    )
                    if (!small) {
                        Text(
                            caption,
                            maxLines = 1,
                            style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption, textAlign = TextAlign.Center),
                        )
                    }
                }
            }
        }
    }

    /** Verde, amarillo, naranja, rojo y violeta según el nivel (0 a 1); para la calidad del aire. */
    private fun levelColor(level: Double): Int = when {
        level < 0.27 -> 0xFF4CAF50.toInt()
        level < 0.55 -> 0xFFFFC107.toInt()
        level < 0.73 -> 0xFFFF9800.toInt()
        level < 1.0 -> 0xFFF44336.toInt()
        else -> 0xFF9C27B0.toInt()
    }

    /** Punto de rocío de la hora; con la fórmula de Magnus si el pronóstico guardado no lo trae. */
    private fun dewPoint(snapshot: WeatherSnapshot): Double {
        val current = snapshot.current
        return snapshot.upcomingHours(1).firstOrNull()?.dewPoint ?: run {
            val alpha = kotlin.math.ln(current.humidity.coerceIn(1, 100) / 100.0) + 17.62 * current.temperature / (243.12 + current.temperature)
            243.12 * alpha / (17.62 - alpha)
        }
    }
}

class IndicadorWidgetReceiver : SimpleWeatherReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IndicadorWidget()
}
