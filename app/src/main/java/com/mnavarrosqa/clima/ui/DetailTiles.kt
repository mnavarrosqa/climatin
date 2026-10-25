package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.ui.theme.LocalDarkTheme
import com.mnavarrosqa.clima.data.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.mnavarrosqa.clima.data.degrees
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mnavarrosqa.clima.data.shortHour
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.Moon
import com.mnavarrosqa.clima.data.Lang
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.mnavarrosqa.clima.data.versusYesterdayText
import com.mnavarrosqa.clima.data.versusYesterday
import com.mnavarrosqa.clima.data.uvWindow
import com.mnavarrosqa.clima.data.trend
import com.mnavarrosqa.clima.data.pressureSeries
import com.mnavarrosqa.clima.data.pollenText
import com.mnavarrosqa.clima.data.outfit
import com.mnavarrosqa.clima.data.mainPollutant
import com.mnavarrosqa.clima.data.humidityFeel
import com.mnavarrosqa.clima.data.fog
import com.mnavarrosqa.clima.data.feelsLikeRange
import com.mnavarrosqa.clima.data.dewTonight
import com.mnavarrosqa.clima.data.degreesDelta
import com.mnavarrosqa.clima.data.beaufortLabel
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.CornerRadius
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.pressureText
import com.mnavarrosqa.clima.data.windFrom
import com.mnavarrosqa.clima.data.windText
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Un dato del pronóstico con, opcionalmente, un dibujo chico que lo explica de un vistazo. */
private class Detail(
    val label: String,
    val value: String,
    val note: String? = null,
    val wide: Boolean = false,
    val visual: (@Composable () -> Unit)? = null,
) {
    /** Qué significa el dato y qué hacer con él; tocar la tarjeta lo muestra. */
    var info: String? = null
}

/** Punto de rocío (fórmula de Magnus): a partir de 16° el aire se siente pesado. */
private fun dewPoint(temperature: Double, humidity: Int): Double {
    val a = 17.62
    val b = 243.12
    val alpha = kotlin.math.ln(humidity.coerceIn(1, 100) / 100.0) + a * temperature / (b + temperature)
    return b * alpha / (a - alpha)
}

/** Escala de colores del UV (0 a 11+), igual a la que usa la OMS. */
private val UvColors = listOf(Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFF9C27B0))

/** Escala del índice europeo de calidad del aire (0 a 100+). */
private val AqiColors = listOf(Color(0xFF50F0E6), Color(0xFF50CCAA), Color(0xFFF0E641), Color(0xFFFF5050), Color(0xFF960032))

/** Qué parte de los detalles se muestra: todos, o la primera o segunda mitad (en dos paneles). */
enum class DetailsPart { ALL, FIRST, SECOND }

@Composable
fun DetailsGrid(snapshot: WeatherSnapshot, modifier: Modifier = Modifier, part: DetailsPart = DetailsPart.ALL, minute: Long? = null) {
    val current = snapshot.current
    val today = snapshot.today()
    val date = snapshot.nowAtPlace().toLocalDate()
    // El sol y la luna se mueven: la tarjeta se rearma cada minuto, no solo con datos nuevos.
    // Si viene de afuera, el mismo minuto para las dos mitades (en dos paneles no se desfasan).
    val ownMinute by produceState(System.currentTimeMillis() / 60_000) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            value = System.currentTimeMillis() / 60_000
        }
    }
    val tick = minute ?: ownMinute
    val sky = remember(snapshot, tick) { skyDetail(snapshot) }
    val night = sky?.second == true
    val details = buildList {
        // De día el sol; de noche la luna, con su recorrido (y entonces no va la tarjeta chica de la luna).
        sky?.let { add(it.first) }
        // Qué ponerse en las próximas 12 horas.
        snapshot.outfit().takeIf { it.isNotEmpty() }?.let { items ->
            add(
                Detail(tr("¿Qué me pongo?", "What to wear"), items.first(), items.drop(1).joinToString("   ").ifEmpty { null }, wide = true).apply {
                    info = tr(
                        "Sale de la sensación térmica, la lluvia, el viento y el UV de las próximas 12 horas. Es una idea: cada uno siente el frío a su manera.",
                        "Based on feels-like temperature, rain, wind and UV over the next 12 hours. Just a hint: everyone feels the cold differently.",
                    )
                },
            )
        }
        val uvNow = current.uvIndex ?: if (current.isDay) today?.uvIndex else 0.0
        uvNow?.let {
            val window = snapshot.uvWindow()
            val tomorrowUv = snapshot.upcomingDays(2).getOrNull(1)?.uvIndex
            val todayUv = today?.uvIndex
            // De noche no tiene sentido el UV de ahora: se dice cuánto llega mañana.
            val note = when {
                // De madrugada, "mañana" es hoy: el día que está por empezar.
                !current.isDay && snapshot.nowAtPlace().hour < 12 && todayUv != null -> tr("Hoy llega a ${todayUv.roundToInt()}", "Today up to ${todayUv.roundToInt()}")
                !current.isDay && tomorrowUv != null -> tr("Mañana llega a ${tomorrowUv.roundToInt()}", "Tomorrow up to ${tomorrowUv.roundToInt()}")
                window != null && window.ongoing -> tr("Protegete hasta las ${shortHour(window.until)}", "Protect yourself until ${shortHour(window.until)}")
                window != null -> tr("Protegete de ${shortHour(window.from)} a ${shortHour(window.until)}", "Protect yourself ${shortHour(window.from)}–${shortHour(window.until)}")
                else -> WeatherCodes.uvLabel(it).replaceFirstChar { c -> c.uppercase() }
            }
            add(
                Detail(tr("Índice UV", "UV index"), "${it.roundToInt()}", note) {
                    ScaleBar((it / 11).toFloat(), UvColors)
                }.apply {
                    info = tr(
                        "0–2 bajo: no hace falta cuidarse. 3–5 moderado: anteojos y protector. 6–7 alto: protector, sombrero y sombra al mediodía. 8–10 muy alto: evitá el sol de 11 a 16. 11 o más extremo." +
                            (today?.uvIndex?.let { max -> " Hoy llega a ${max.roundToInt()}." } ?: ""),
                        "0–2 low: no protection needed. 3–5 moderate: sunglasses and sunscreen. 6–7 high: sunscreen, hat and shade at midday. 8–10 very high: avoid the sun from 11 to 4. 11+ extreme." +
                            (today?.uvIndex?.let { max -> " Today it reaches ${max.roundToInt()}." } ?: ""),
                    )
                },
            )
        }
        val gust = current.windGusts?.takeIf { it >= current.windSpeed + 10 }
        add(
            Detail(
                tr("Viento", "Wind"),
                windText(current.windSpeed),
                listOfNotNull(
                    beaufortLabel(current.windSpeed),
                    current.windDirection?.let { tr("del", "from") + " " + windFrom(it) },
                ).joinToString(" "),
                visual = @Composable {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        current.windDirection?.let { WindCompass(it.toFloat()) }
                        // Las ráfagas al lado, como una barra contra el viento medio.
                        if (gust != null) {
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr("Ráfagas", "Gusts"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(windText(gust), style = MaterialTheme.typography.labelLarge)
                                GustBar((current.windSpeed / gust).toFloat())
                            }
                        }
                    }
                },
            ).apply {
                info = tr(
                    "Viento medio a 10 metros del suelo, con su nombre en la escala de Beaufort; las ráfagas pueden ser bastante más fuertes. La dirección es de dónde viene." +
                        (current.windGusts?.let { g -> " Ráfagas de hasta ${windText(g)}." } ?: "") +
                        (today?.windMax?.let { max -> " Hoy, como mucho ${windText(max)}." } ?: ""),
                    "Average wind 10 meters above ground, named on the Beaufort scale; gusts can be much stronger. The direction is where it comes from." +
                        (current.windGusts?.let { g -> " Gusts up to ${windText(g)}." } ?: "") +
                        (today?.windMax?.let { max -> " Today, up to ${windText(max)}." } ?: ""),
                )
            },
        )
        // Sensación térmica: por qué no coincide con la temperatura, y cómo sigue.
        val feelsRange = snapshot.feelsLikeRange(12)
        val feelsSeries = snapshot.upcomingHours(12).mapNotNull { it.feelsLike }
        add(
            Detail(
                tr("Sensación", "Feels like"),
                current.feelsLike.degrees(),
                feelsRange?.let { (low, _) ->
                    tr("Mín ${low.feelsLike!!.degrees()} a las ${shortHour(low.dateTime)}", "Low ${low.feelsLike!!.degrees()} at ${shortHour(low.dateTime)}")
                } ?: when {
                    current.feelsLike < current.temperature - 1.5 -> tr("Más fresco por el viento", "Cooler because of the wind")
                    current.feelsLike > current.temperature + 1.5 -> tr("Más calor por la humedad", "Warmer because of humidity")
                    else -> tr("Como la temperatura", "Same as the temperature")
                },
                visual = if (feelsSeries.size >= 3) ({ Sparkline(feelsSeries, nowIndex = 0) }) else null,
            ).apply {
                val why = when {
                    current.feelsLike < current.temperature - 1.5 -> tr(" Ahora se siente más fresco por el viento.", " Right now it feels cooler because of the wind.")
                    current.feelsLike > current.temperature + 1.5 -> tr(" Ahora se siente más calor por la humedad.", " Right now it feels warmer because of humidity.")
                    else -> ""
                }
                val range = feelsRange?.let { (low, high) ->
                    tr(
                        " En las próximas 12 horas va de ${low.feelsLike!!.degrees()} (${shortHour(low.dateTime)}) a ${high.feelsLike!!.degrees()} (${shortHour(high.dateTime)}).",
                        " Over the next 12 hours it goes from ${low.feelsLike!!.degrees()} (${shortHour(low.dateTime)}) to ${high.feelsLike!!.degrees()} (${shortHour(high.dateTime)}).",
                    )
                } ?: ""
                info = tr(
                    "Cómo se siente en el cuerpo: con viento y frío se siente más fresco (el viento se lleva el calor de la piel); con calor y humedad, más calor (cuesta transpirar).",
                    "How it feels on your body: wind makes cold feel colder (it carries heat away from your skin); humidity makes heat feel hotter (sweat evaporates less).",
                ) + why + range
            },
        )
        // Comparación con ayer a la misma hora.
        snapshot.versusYesterday()?.let { difference ->
            // El signo sale del número ya convertido y redondeado (en °F puede no coincidir con °C).
            val shownDelta = difference.degreesDelta()
            val sign = if (shownDelta == "0°") "" else if (difference > 0) "+" else "−"
            add(
                Detail(tr("Comparado con ayer", "Versus yesterday"), sign + shownDelta, versusYesterdayText(difference)).apply {
                    info = tr(
                        "La temperatura de ahora contra la de ayer a la misma hora. La línea punteada del gráfico por hora muestra ayer completo.",
                        "The temperature now against yesterday at the same time. The dotted line in the hourly chart shows all of yesterday.",
                    )
                },
            )
        }
        val dew = dewPoint(current.temperature, current.humidity)
        val dewTonight = snapshot.dewTonight()
        add(
            Detail(
                tr("Humedad", "Humidity"),
                "${current.humidity}%",
                when {
                    dewTonight?.frost == true -> tr("Escarcha a las ${shortHour(dewTonight.at)}", "Frost at ${shortHour(dewTonight.at)}")
                    dewTonight != null -> tr("Rocío a las ${shortHour(dewTonight.at)}", "Dew at ${shortHour(dewTonight.at)}")
                    else -> humidityFeel(dew) + " · " + tr("rocío", "dew") + " ${dew.degrees()}"
                },
            ) {
                RingGauge(current.humidity / 100f)
            }.apply {
                val tonight = dewTonight?.let {
                    if (it.frost) {
                        tr(" Esta noche baja al punto de rocío bajo cero: escarcha cerca de las ${shortHour(it.at)}.", " Tonight it drops to a below-zero dew point: frost around ${shortHour(it.at)}.")
                    } else {
                        tr(" Esta noche baja al punto de rocío: pasto y autos mojados cerca de las ${shortHour(it.at)}.", " Tonight it reaches the dew point: wet grass and cars around ${shortHour(it.at)}.")
                    }
                } ?: ""
                info = tr(
                    "El punto de rocío dice mejor que la humedad cuánto se siente el aire: hasta 10° seco, de 10° a 16° agradable, de 16° a 20° pesado y desde 20° muy húmedo. Ahora: ${dew.degrees()} (${humidityFeel(dew).lowercase()}).",
                    "Dew point tells better than humidity how the air feels: up to 10° dry, 10–16° comfortable, 16–20° muggy and above 20° oppressive. Now: ${dew.degrees()} (${humidityFeel(dew).lowercase()}).",
                ) + tonight
            },
        )
        snapshot.airQuality?.let { air ->
            val aqi = air.europeanAqi ?: return@let
            val trend = when (air.trend()) {
                1 -> tr("empeora", "worsening")
                -1 -> tr("mejora", "improving")
                0 -> tr("estable", "steady")
                else -> null
            }
            val pollen = air.pollenText()
            add(
                Detail(
                    tr("Calidad del aire", "Air quality"),
                    WeatherCodes.airQualityLabel(aqi),
                    pollen?.let { tr("Polen: ", "Pollen: ") + it } ?: listOfNotNull(tr("Índice", "Index") + " $aqi", trend).joinToString(" · "),
                ) {
                    ScaleBar(aqi / 100f, AqiColors)
                }.apply {
                    val parts = listOfNotNull(
                        air.mainPollutant()?.let { tr(" Lo que más pesa ahora: $it.", " Main pollutant now: $it.") },
                        air.pm25?.let { v -> " PM2,5: ${v.roundToInt()} µg/m³." },
                        trend?.let { tr(" En las próximas horas: $it.", " Next hours: $it.") },
                        pollen?.let { tr(" Polen: $it.", " Pollen: $it.") },
                    ).joinToString("")
                    info = tr(
                        "Índice europeo: hasta 20 bueno, 20–40 aceptable, 40–60 moderado (las personas sensibles pueden notarlo), 60–80 malo (mejor evitar ejercicio fuerte afuera), 80–100 muy malo y más de 100 extremadamente malo.",
                        "European index: up to 20 good, 20–40 fair, 40–60 moderate (sensitive people may notice), 60–80 poor (avoid hard exercise outdoors), 80–100 very poor and above 100 extremely poor.",
                    ) + parts
                },
            )
        }
        // Lluvia: cuándo, hora por hora, en las próximas 24.
        val next = snapshot.upcomingHours(25)
        val rainHours = next.indices.take(24).map { i -> rainAfter(next, i) }
        val rainy = rainHours.indices.filter { (rainHours[it].first ?: 0) >= 50 }
        // La probabilidad más alta de estas 24 horas (no la de hoy: la franja puede ser de mañana).
        val rain = rainHours.maxOfOrNull { it.first ?: 0 } ?: 0
        add(
            Detail(
                tr("Lluvia", "Rain"),
                if (rainy.isEmpty()) tr("Sin lluvia a la vista", "No rain in sight") else tr("Hasta $rain%", "Up to $rain%"),
                rainy.firstOrNull()?.let { first ->
                    // El primer tramo seguido de lluvia probable.
                    var last = first
                    while (last + 1 in rainy) last++
                    val from = next[first].dateTime
                    val until = next[last].dateTime.plusHours(1)
                    val total = (first..last).sumOf { rainHours[it].second ?: 0.0 }
                    tr("Probable de ${shortHour(from)} a ${shortHour(until)}", "Likely ${shortHour(from)}–${shortHour(until)}") +
                        (if (total >= 0.1) " · " + String.format(java.util.Locale.US, "%.1f mm", total) else "")
                } ?: tr("Próximas 24 horas", "Next 24 hours"),
                // Sin lluvia, una tarjeta chica: las barras vacías dejaban una tarjeta ancha en blanco.
                wide = rainHours.any { (it.first ?: 0) >= 10 },
                visual = if (next.size >= 2 && rainHours.any { (it.first ?: 0) >= 10 }) ({ RainBars(rainHours, next.take(24).map { shortHour(it.dateTime) }) }) else null,
            ).apply {
                info = tr(
                    "Cada barra es una hora de las próximas 24: la altura es la probabilidad de lluvia y el color más fuerte, más milímetros. 1 mm es un litro por metro cuadrado: menos de 2 mm es poco, más de 10 mm es bastante y más de 30 mm, mucho.",
                    "Each bar is one of the next 24 hours: height is the chance of rain and a stronger color means more millimeters. 1 mm is one liter per square meter: under 2 mm is little, over 10 mm is a lot and over 30 mm is heavy.",
                )
            },
        )
        if (!night) add(
            Detail(tr("Luna", "Moon"), Moon.phaseName(date)) { MoonPhase(Moon.age(date).toFloat(), southern = snapshot.latitude < 0) }.apply {
                val age = Moon.age(date)
                val toFull = (((0.5 - age).let { if (it < 0) it + 1 else it }) * 29.53).roundToInt()
                val toNew = (((1.0 - age) % 1.0) * 29.53).roundToInt()
                info = tr(
                    "Luna llena en $toFull días y luna nueva en $toNew. Desde el hemisferio sur se ve dada vuelta: la parte iluminada mientras crece queda a la izquierda.",
                    "Full moon in $toFull days and new moon in $toNew. From the southern hemisphere it looks upside down: the lit part while waxing is on the left.",
                )
            },
        )
        current.pressure?.let {
            // Cuánto cambió en 3 horas: más de 1 hPa ya es una tendencia; más de 3, rápida.
            val change = snapshot.pressureHoursAgo(3)?.let { before -> it - before }
            val trend = when {
                change == null -> null
                change <= -3 -> tr("↓ Baja rápido", "↓ Falling fast")
                change <= -1 -> tr("↓ Bajando", "↓ Falling")
                change >= 3 -> tr("↑ Sube rápido", "↑ Rising fast")
                change >= 1 -> tr("↑ Subiendo", "↑ Rising")
                else -> tr("Estable", "Steady")
            }
            val series = snapshot.pressureSeries()
            add(
                Detail(
                    tr("Presión", "Pressure"),
                    pressureText(it),
                    trend,
                    visual = series?.let { (values, index) -> @Composable { Sparkline(values, nowIndex = index) } },
                ).apply {
                    info = tr(
                        "Presión del aire llevada al nivel del mar, para comparar entre lugares. Lo normal ronda los 1013 hPa; si baja rápido suele venir mal tiempo, y si sube, se estabiliza. La flecha compara con 3 horas atrás y la línea va de 12 horas atrás a 12 adelante.",
                        "Air pressure adjusted to sea level, so places can be compared. Around 1013 hPa is normal; a quick drop often means bad weather and a rise means it's settling. The arrow compares with 3 hours ago and the line goes from 12 hours ago to 12 ahead.",
                    )
                },
            )
        }
        current.visibility?.let {
            val km = it / 1000
            val fog = snapshot.fog()
            val fogAt = fog?.at
            add(
                Detail(
                    tr("Visibilidad", "Visibility"),
                    if (km >= 10) "+10 km" else if (km < 1) "${it.roundToInt()} m" else "${km.roundToInt()} km",
                    when {
                        fog?.now == true && fogAt != null -> tr("La niebla se levanta a las ${shortHour(fogAt)}", "Fog lifts at ${shortHour(fogAt)}")
                        fog?.now == true -> tr("Niebla por varias horas", "Fog for hours")
                        fogAt != null -> tr("Niebla desde las ${shortHour(fogAt)}", "Fog from ${shortHour(fogAt)}")
                        km >= 10 -> tr("Despejado", "Clear")
                        else -> null
                    },
                ).apply {
                    info = tr(
                        "Hasta dónde se ve. Con menos de 1 km hay niebla: manejá con luces bajas y más distancia.",
                        "How far you can see. Under 1 km there's fog: drive with low beams and keep more distance.",
                    )
                },
            )
        }
    }
    // Los anchos van solos; los demás de a dos, con la misma altura por fila.
    val rows = buildList<List<Detail>> {
        var pending: Detail? = null
        details.forEach { detail ->
            if (detail.wide) {
                add(listOf(detail))
            } else if (pending == null) {
                pending = detail
            } else {
                add(listOf(pending!!, detail))
                pending = null
            }
        }
        pending?.let { add(listOf(it)) }
    }
    // En dos paneles se reparten: la izquierda (que ya tiene la temperatura) se lleva menos.
    val split = rows.size / 2
    val shown = when (part) {
        DetailsPart.ALL -> rows
        DetailsPart.FIRST -> rows.take(split)
        DetailsPart.SECOND -> rows.drop(split)
    }
    if (shown.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Con clave por tarjeta: si aparece o se va una (de día a noche, el aire), el estado de cada
        // una (la explicación abierta, la animación) no pasa a la de al lado.
        shown.forEach { row ->
            key(row.joinToString("|") { it.label }) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { detail -> key(detail.label) { DetailTile(detail, Modifier.weight(1f).fillMaxHeight()) } }
                if (row.size == 1 && !row[0].wide) Spacer(Modifier.weight(1f))
            }
            }
        }
    }
}

@Composable
private fun DetailTile(detail: Detail, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val info = detail.info
    if (open && info != null) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(detail.label) },
            text = { Text(info) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(tr("Entendido", "Got it")) } },
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .then(if (info != null) Modifier.clickable(onClickLabel = tr("Qué significa", "What it means")) { open = true } else Modifier)
            // Una sola lectura para TalkBack, con la acción de ver qué significa.
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(detail.label, detail.value, detail.note).joinToString(", ")
                if (info != null) {
                    onClick(label = tr("Qué significa", "What it means")) {
                        open = true
                        true
                    }
                }
            },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            CardLabel(detail.label)
            Spacer(Modifier.height(6.dp))
            Text(detail.value, style = MaterialTheme.typography.titleLarge)
            if (detail.note != null) {
                Text(
                    detail.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detail.visual != null) {
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(12.dp))
                detail.visual.invoke()
            }
        }
    }
}

/**
 * Avance de 0 a 1 que crece una vez al aparecer el dibujo. Es un estado que se lee dentro del
 * Canvas (con `by`): así la animación solo redibuja, sin recomponer la tarjeta en cada cuadro.
 */
@Composable
private fun growth(): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, delayMillis = 200, easing = EaseOutCubic)) }
    return progress.asState()
}

/** Barra con la escala de colores y un punto en el valor actual. */
@Composable
private fun ScaleBar(fraction: Float, colors: List<Color>) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val knob = MaterialTheme.colorScheme.onSurface
    val grow by growth()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val bar = 6.dp.toPx()
        val y = size.height / 2
        drawLine(track, Offset(bar / 2, y), Offset(size.width - bar / 2, y), bar, StrokeCap.Round)
        drawLine(Brush.horizontalGradient(colors), Offset(bar / 2, y), Offset(size.width - bar / 2, y), bar, StrokeCap.Round)
        val x = bar / 2 + (size.width - bar) * fraction.coerceIn(0f, 1f) * grow
        drawCircle(track, radius = 7.dp.toPx(), center = Offset(x, y))
        drawCircle(knob, radius = 5.dp.toPx(), center = Offset(x, y))
    }
}

/** Anillo de tres cuartos que se llena según el valor (humedad, chance de lluvia). */
@Composable
private fun RingGauge(fraction: Float, color: Color = MaterialTheme.colorScheme.primary) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val grow by growth()
    Canvas(Modifier.size(56.dp)) {
        val stroke = 7.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(
            color,
            135f,
            270f * fraction.coerceIn(0f, 1f) * grow,
            false,
            Offset(inset, inset),
            arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Brújula con los puntos cardinales y una flecha que apunta hacia donde sopla el viento. */
@Composable
private fun WindCompass(fromDegrees: Float) {
    val colors = MaterialTheme.colorScheme
    val grow by growth()
    val measurer = rememberTextMeasurer()
    val letterStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val letters = remember(letterStyle) {
        (if (Lang.isSpanish) listOf("N", "E", "S", "O") else listOf("N", "E", "S", "W")).map { measurer.measure(it, letterStyle) }
    }
    Canvas(Modifier.size(64.dp)) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(colors.surfaceContainerHigh, radius = radius, center = center)
        // Letras en los cuatro puntos cardinales y marcas cortas en los intermedios.
        val letterRadius = radius - 9.dp.toPx()
        letters.forEachIndexed { i, layout ->
            val angle = i * PI / 2
            val x = center.x + letterRadius * sin(angle).toFloat() - layout.size.width / 2f
            val y = center.y - letterRadius * cos(angle).toFloat() - layout.size.height / 2f
            drawText(layout, topLeft = Offset(x, y))
        }
        repeat(4) { i ->
            val angle = PI / 4 + i * PI / 2
            val outer = radius - 4.dp.toPx()
            val inner = outer - 3.dp.toPx()
            drawLine(
                colors.onSurfaceVariant.copy(alpha = 0.5f),
                Offset(center.x + inner * sin(angle).toFloat(), center.y - inner * cos(angle).toFloat()),
                Offset(center.x + outer * sin(angle).toFloat(), center.y - outer * cos(angle).toFloat()),
                1.5.dp.toPx(),
                StrokeCap.Round,
            )
        }
        // El viento "del norte" va hacia el sur: la flecha gira 180° y entra girando al aparecer.
        rotate(fromDegrees + 180f - (1f - grow) * 90f, center) {
            val length = radius - 18.dp.toPx()
            val head = 7.dp.toPx()
            val arrow = Path().apply {
                moveTo(center.x, center.y - length)
                lineTo(center.x + head * 0.7f, center.y - length + head)
                lineTo(center.x, center.y - length + head * 0.7f)
                lineTo(center.x - head * 0.7f, center.y - length + head)
                close()
            }
            // La cola de donde viene, más finita; la punta hacia donde va.
            drawLine(colors.primary.copy(alpha = 0.5f), Offset(center.x, center.y + length), center, 2.dp.toPx(), StrokeCap.Round)
            drawLine(colors.primary, center, Offset(center.x, center.y - length + head * 0.7f), 3.dp.toPx(), StrokeCap.Round)
            drawPath(arrow, colors.primary)
        }
        drawCircle(colors.primary, 3.dp.toPx(), center)
    }
}

/** La luna con la parte iluminada que corresponde a su edad (0 nueva, 0.5 llena). */
@Composable
private fun MoonPhase(age: Float, southern: Boolean) {
    Canvas(Modifier.size(48.dp)) {
        drawMoonDisk(Offset(size.width / 2, size.height / 2), size.minDimension / 2, age, southern)
    }
}

/** La luna con su fase: la parte en sombra es gris pizarra en los dos temas, así se distingue sobre una tarjeta clara. */
private fun DrawScope.drawMoonDisk(c: Offset, r: Float, age: Float, southern: Boolean, alpha: Float = 1f) {
    val dark = Color(0xFF3A4150).copy(alpha = alpha)
    val light = Color(0xFFF3EBD2).copy(alpha = alpha)
    drawCircle(dark, r, c)
    val circle = Rect(c, r)
    val terminator = r * abs(cos(2 * PI * age)).toFloat()
    val ellipse = Rect(c.x - terminator, c.y - r, c.x + terminator, c.y + r)
    // Desde el hemisferio sur la luna se ve dada vuelta.
    val waxing = (age < 0.5f) != southern
    val crescent = age < 0.25f || age > 0.75f
    val lit = Path().apply {
        moveTo(c.x, c.y - r)
        // Mitad iluminada: la derecha mientras crece, la izquierda mientras mengua.
        arcTo(circle, -90f, if (waxing) 180f else -180f, false)
        // Borde entre luz y sombra: hacia el lado iluminado si es creciente, al otro si es gibosa.
        val towardLit = if (waxing) -180f else 180f
        arcTo(ellipse, 90f, if (crescent) towardLit else -towardLit, false)
        close()
    }
    drawPath(lit, light)
}


/**
 * La tarjeta del cielo: de día el sol, de noche la luna. Devuelve la tarjeta y si es de noche.
 * Null en zonas polares, donde Open-Meteo puede no traer amanecer ni atardecer.
 */
private fun skyDetail(snapshot: WeatherSnapshot): Pair<Detail, Boolean>? {
    val now = snapshot.nowAtPlace()
    val suns = snapshot.days.mapNotNull { day ->
        runCatching { LocalDateTime.parse(day.sunrise) to LocalDateTime.parse(day.sunset) }.getOrNull()
    }
    val (rise, set) = suns.firstOrNull { it.first.toLocalDate() == now.toLocalDate() } ?: return null
    val southern = snapshot.latitude < 0
    if (!now.isBefore(rise) && now.isBefore(set)) {
        val total = Duration.between(rise, set).toMinutes()
        val left = Duration.between(now, set).toMinutes()
        val dayStart = now.toLocalDate().atStartOfDay()
        val noon = rise.plusMinutes(total / 2)
        // Altura del sol aproximada con una onda de 24 horas que cruza el horizonte al amanecer y
        // al atardecer: alcanza para dibujar el recorrido.
        val horizon = cos(PI * total / 1440.0)
        val samples = FloatArray(SKY_SAMPLES) { i ->
            val minute = i * 1440.0 / (SKY_SAMPLES - 1)
            val fromNoon = minute - Duration.between(dayStart, noon).toMinutes()
            (cos(2 * PI * fromNoon / 1440.0) - horizon).toFloat()
        }
        val detail = Detail(
            tr("Sol", "Sun"),
            tr("Se pone a las ${shortHour(set)}", "Sets at ${shortHour(set)}"),
            tr("Quedan ${durationText(left)} de luz · ${durationText(total)} en total", "${durationText(left)} of daylight left · ${durationText(total)} in total"),
            wide = true,
        ) {
            SkyPath(
                samples = samples,
                now = Duration.between(dayStart, now).toMinutes() / 1440f,
                body = null,
                left = "↑ " + shortHour(rise),
                right = "↓ " + shortHour(set),
            )
        }.apply {
            val golden = set.minusHours(1)
            info = tr(
                "Sale a las ${shortHour(rise)} y se pone a las ${shortHour(set)}. La luz más linda es la hora dorada: la primera hora después del amanecer y la última antes del atardecer (hoy, desde las ${shortHour(golden)}). Si querés un aviso, prendé \"Hora dorada\" en Ajustes → Notificaciones.",
                "Rises at ${shortHour(rise)} and sets at ${shortHour(set)}. The nicest light is the golden hour: the first hour after sunrise and the last before sunset (today, from ${shortHour(golden)}). Turn on \"Golden hour\" in Settings → Notifications for a heads-up.",
            )
        }
        return detail to false
    }
    // De noche: de la puesta de hoy al amanecer de mañana, o de la de ayer al de hoy.
    val nightStart = if (now.isBefore(rise)) suns.lastOrNull { it.second.isBefore(rise) }?.second else set
    val nightEnd = if (now.isBefore(rise)) rise else suns.firstOrNull { it.first.isAfter(set) }?.first
    // El recorrido de la luna en 24 horas, de mediodía a mediodía: la noche queda en el medio.
    val windowStart = (if (now.isBefore(rise)) now.toLocalDate().minusDays(1) else now.toLocalDate()).atTime(12, 0)
    fun at(fraction: Float) = windowStart.plusMinutes((fraction * 1440).roundToInt().toLong())
    val samples = FloatArray(SKY_SAMPLES) { i ->
        val time = windowStart.plusMinutes((i * 1440L) / (SKY_SAMPLES - 1))
        Moon.altitude(time.atZone(snapshot.zone).toInstant(), snapshot.latitude, snapshot.longitude).toFloat()
    }
    // Cuándo sale y se pone dentro de esas 24 horas (entre dos muestras, se interpola).
    var moonrise: LocalDateTime? = null
    var moonset: LocalDateTime? = null
    for (i in 1 until samples.size) {
        val a = samples[i - 1]
        val b = samples[i]
        if ((a < 0) == (b < 0)) continue
        val time = at((i - 1 + a / (a - b)) / (SKY_SAMPLES - 1))
        if (b >= 0 && moonrise == null) moonrise = time
        if (b < 0 && moonset == null) moonset = time
    }
    val date = now.toLocalDate()
    val age = Moon.age(date)
    val lit = (Moon.illumination(age) * 100).roundToInt()
    val nowFraction = Duration.between(windowStart, now).toMinutes() / 1440f
    val up = samples[(nowFraction * (SKY_SAMPLES - 1)).roundToInt().coerceIn(0, SKY_SAMPLES - 1)] >= 0
    val visibility = when {
        up && moonset != null && moonset.isAfter(now) -> tr("A la vista hasta las ${shortHour(moonset)}", "Up until ${shortHour(moonset)}")
        up -> tr("A la vista", "Up now")
        moonrise != null && moonrise.isAfter(now) -> tr("Sale a las ${shortHour(moonrise)}", "Rises at ${shortHour(moonrise)}")
        else -> tr("Bajo el horizonte esta noche", "Below the horizon tonight")
    }
    val events = listOfNotNull(moonrise?.let { it to "↑ " + shortHour(it) }, moonset?.let { it to "↓ " + shortHour(it) }).sortedBy { it.first }
    val detail = Detail(
        tr("Luna", "Moon"),
        Moon.phaseName(date),
        tr("Iluminada $lit% · $visibility", "$lit% lit · $visibility"),
        wide = true,
    ) {
        SkyPath(
            samples = samples,
            now = nowFraction,
            body = MoonBody(age.toFloat(), southern),
            left = events.getOrNull(0)?.second,
            right = events.getOrNull(1)?.second,
            shade = if (nightStart != null && nightEnd != null) {
                Duration.between(windowStart, nightStart).toMinutes() / 1440f..Duration.between(windowStart, nightEnd).toMinutes() / 1440f
            } else {
                null
            },
        )
    }.apply {
        val toFull = (((0.5 - age).let { if (it < 0) it + 1 else it }) * 29.53).roundToInt()
        val toNew = (((1.0 - age) % 1.0) * 29.53).roundToInt()
        val sunrise = nightEnd?.let { tr(" Amanece a las ${shortHour(it)}.", " Sunrise at ${shortHour(it)}.") } ?: ""
        info = tr(
            "La línea es la altura de la luna en estas 24 horas y la franja, la noche. Luna llena en $toFull días y luna nueva en $toNew. Desde el hemisferio sur se ve dada vuelta: la parte iluminada mientras crece queda a la izquierda.$sunrise",
            "The line is the moon's height over these 24 hours and the band is the night. Full moon in $toFull days and new moon in $toNew. From the southern hemisphere it looks upside down: the lit part while waxing is on the left.$sunrise",
        )
    }
    return detail to true
}

private const val SKY_SAMPLES = 97

private fun durationText(minutes: Long): String =
    if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"

/** La luna que se dibuja en el recorrido, con su fase. */
private class MoonBody(val age: Float, val southern: Boolean)

/**
 * Recorrido del sol o de la luna en 24 horas: la línea es la altura (arriba del horizonte, de
 * color; abajo, gris), lo que ya pasó va lleno y lo que falta, punteado. [now] y [shade] van de
 * 0 a 1 en esas 24 horas. Sin [body] se dibuja el sol.
 */
@Composable
private fun SkyPath(
    samples: FloatArray,
    now: Float,
    body: MoonBody?,
    left: String?,
    right: String?,
    shade: ClosedFloatingPointRange<Float>? = null,
) {
    val colors = MaterialTheme.colorScheme
    val darkTheme = LocalDarkTheme.current
    val grow by growth()
    val dayColors = if (darkTheme) listOf(Color(0xFFFFB74D), Color(0xFFFFD54F)) else listOf(Color(0xFFF57C00), Color(0xFFFFA000))
    val moonColor = if (darkTheme) Color(0xFFC5CAE9) else Color(0xFF5C6BC0)
    val below = colors.onSurfaceVariant.copy(alpha = 0.55f)
    val future = colors.onSurfaceVariant.copy(alpha = 0.35f)
    val nightBand = colors.onSurface.copy(alpha = 0.06f)
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(84.dp),
        ) {
            val pad = 10.dp.toPx()
            val horizon = size.height * 0.64f
            val up = samples.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
            val down = (-(samples.minOrNull() ?: -1f)).coerceAtLeast(0.01f)
            fun y(v: Float) = if (v >= 0) horizon - v / up * (horizon - pad) else horizon + (-v) / down * (size.height - pad / 2 - horizon)
            fun x(fraction: Float) = fraction * size.width
            val curve = Path().apply {
                samples.forEachIndexed { i, v ->
                    val px = x(i / (samples.size - 1f))
                    if (i == 0) moveTo(px, y(v)) else lineTo(px, y(v))
                }
            }
            shade?.let { band ->
                val from = x(band.start.coerceIn(0f, 1f))
                val to = x(band.endInclusive.coerceIn(0f, 1f))
                if (to > from) drawRect(nightBand, topLeft = Offset(from, 0f), size = Size(to - from, size.height))
            }
            val stroke = 3.dp.toPx()
            // Lo que falta: punteado y suave.
            drawPath(curve, future, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))))
            drawLine(colors.onSurfaceVariant.copy(alpha = 0.4f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
            // Lo que ya pasó: de color arriba del horizonte y gris abajo. Crece al aparecer.
            val shown = (now * grow).coerceIn(0f, 1f)
            clipRect(right = x(shown)) {
                clipRect(bottom = horizon) {
                    if (body == null) {
                        drawPath(curve, Brush.horizontalGradient(dayColors), style = Stroke(stroke, cap = StrokeCap.Round))
                    } else {
                        drawPath(curve, moonColor, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                clipRect(top = horizon) { drawPath(curve, below, style = Stroke(stroke, cap = StrokeCap.Round)) }
            }
            // El sol o la luna donde está ahora; bajo el horizonte, apagado.
            val index = shown * (samples.size - 1)
            val i0 = index.toInt().coerceIn(0, samples.size - 1)
            val i1 = (i0 + 1).coerceAtMost(samples.size - 1)
            val value = samples[i0] + (samples[i1] - samples[i0]) * (index - i0)
            val position = Offset(x(shown), y(value))
            val visible = value >= 0
            val r = 9.dp.toPx()
            if (body == null) {
                drawCircle(Color(0xFFFFD54F).copy(alpha = if (visible) 0.35f else 0.12f), r * 1.6f, position)
                drawCircle(Color(0xFFFFC107).copy(alpha = if (visible) 1f else 0.4f), r, position)
            } else {
                drawCircle(moonColor.copy(alpha = if (visible) 0.3f else 0.1f), r * 1.5f, position)
                drawMoonDisk(position, r, body.age, body.southern, alpha = if (visible) 1f else 0.45f)
            }
        }
        if (left != null || right != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(left.orEmpty(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(right.orEmpty(), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Línea chica de una serie (sensación, presión), con un punto en [nowIndex]. */
@Composable
private fun Sparkline(values: List<Double>, nowIndex: Int) {
    val colors = MaterialTheme.colorScheme
    val grow by growth()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(36.dp),
    ) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0.001 } ?: 1.0
        val pad = 4.dp.toPx()
        fun point(i: Int) = Offset(
            i * size.width / (values.size - 1),
            (size.height - pad - ((values[i] - min) / span).toFloat() * (size.height - 2 * pad)),
        )
        val path = Path().apply {
            values.indices.forEach { i -> point(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        clipRect(right = size.width * grow) {
            drawPath(path, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        val now = point(nowIndex.coerceIn(0, values.lastIndex))
        drawCircle(colors.surface, 5.dp.toPx(), now)
        drawCircle(colors.primary, 3.5.dp.toPx(), now)
    }
}

/** Barra de las ráfagas: la parte llena es el viento medio. */
@Composable
private fun GustBar(fraction: Float) {
    val colors = MaterialTheme.colorScheme
    val grow by growth()
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(8.dp),
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(colors.primary.copy(alpha = 0.25f), cornerRadius = r)
        drawRoundRect(colors.primary, size = Size(size.width * fraction.coerceIn(0.05f, 1f) * grow, size.height), cornerRadius = r)
    }
}

/**
 * Lluvia de las próximas 24 horas: una barra por hora, alta según la probabilidad y más intensa
 * cuantos más milímetros. Abajo, la hora cada 6.
 */
@Composable
private fun RainBars(rain: List<Pair<Int?, Double?>>, labels: List<String>) {
    val colors = MaterialTheme.colorScheme
    val base = colors.tertiary
    val grow by growth()
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp),
        ) {
            val slot = size.width / rain.size
            val bar = slot * 0.6f
            drawLine(colors.onSurfaceVariant.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            rain.forEachIndexed { i, (probability, mm) ->
                val p = (probability ?: 0) / 100f
                if (p <= 0.02f) return@forEachIndexed
                val h = size.height * p * grow
                val strength = ((mm ?: 0.0) / 4.0).coerceIn(0.0, 1.0).toFloat()
                drawRoundRect(
                    base.copy(alpha = 0.35f + 0.65f * strength),
                    topLeft = Offset(slot * i + (slot - bar) / 2, size.height - h),
                    size = Size(bar, h),
                    cornerRadius = CornerRadius(bar / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            labels.forEachIndexed { i, label ->
                if (i % 6 == 0) {
                    Text(
                        if (i == 0) tr("Ahora", "Now") else label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(6f),
                    )
                }
            }
        }
    }
}
