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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.Moon
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.pressureText
import com.mnavarrosqa.clima.data.timeOf
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

@Composable
fun DetailsGrid(snapshot: WeatherSnapshot, modifier: Modifier = Modifier) {
    val current = snapshot.current
    val today = snapshot.today()
    val date = snapshot.nowAtPlace().toLocalDate()
    val details = buildList {
        // En zonas polares (sol de medianoche) Open-Meteo puede no traer horas válidas: sin tarjeta, sin cierre.
        today?.takeIf { day -> runCatching { LocalDateTime.parse(day.sunrise); LocalDateTime.parse(day.sunset) }.isSuccess }?.let {
            add(
                Detail(
                    tr("Sol", "Sun"),
                    timeOf(it.sunrise) + " – " + timeOf(it.sunset),
                    daylightText(it.sunrise, it.sunset),
                    wide = true,
                ) { SunArc(it.sunrise, it.sunset, snapshot.nowAtPlace()) }.apply {
                    info = tr(
                        "La luz más linda es la hora dorada: la primera hora después del amanecer y la última antes del atardecer. Si querés un aviso, prendé \"Hora dorada\" en Ajustes → Notificaciones.",
                        "The nicest light is the golden hour: the first hour after sunrise and the last before sunset. Turn on \"Golden hour\" in Settings → Notifications for a heads-up.",
                    )
                },
            )
        }
        (current.uvIndex ?: today?.uvIndex)?.let {
            add(
                Detail(tr("Índice UV", "UV index"), "${it.roundToInt()}", WeatherCodes.uvLabel(it).replaceFirstChar { c -> c.uppercase() }) {
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
        add(
            Detail(
                tr("Viento", "Wind"),
                windText(current.windSpeed),
                // Las ráfagas solo si suman algo: con viento parejo, la dirección alcanza.
                listOfNotNull(
                    current.windDirection?.let { tr("Del", "From") + " " + windFrom(it) },
                    current.windGusts?.takeIf { it >= current.windSpeed + 10 }?.let { tr("Ráfagas", "Gusts") + " " + windText(it) },
                ).joinToString(" · ").ifEmpty { null },
                visual = current.windDirection?.let { direction -> @Composable { WindCompass(direction.toFloat()) } },
            ).apply {
                info = tr(
                    "Viento medio a 10 metros del suelo; las ráfagas pueden ser bastante más fuertes. La dirección es de dónde viene." +
                        (current.windGusts?.let { gust -> " Ráfagas de hasta ${windText(gust)}." } ?: "") +
                        (today?.windMax?.let { max -> " Hoy, como mucho ${windText(max)}." } ?: ""),
                    "Average wind 10 meters above ground; gusts can be much stronger. The direction is where it comes from." +
                        (current.windGusts?.let { gust -> " Gusts up to ${windText(gust)}." } ?: "") +
                        (today?.windMax?.let { max -> " Today, up to ${windText(max)}." } ?: ""),
                )
            },
        )
        // Sensación térmica: por qué no coincide con la temperatura.
        add(
            Detail(
                tr("Sensación", "Feels like"),
                current.feelsLike.degrees(),
                when {
                    current.feelsLike < current.temperature - 1.5 -> tr("Más fresco por el viento", "Cooler because of the wind")
                    current.feelsLike > current.temperature + 1.5 -> tr("Más calor por la humedad", "Warmer because of humidity")
                    else -> tr("Como la temperatura", "Same as the temperature")
                },
            ).apply {
                info = tr(
                    "Cómo se siente en el cuerpo: con viento y frío se siente más fresco (el viento se lleva el calor de la piel); con calor y humedad, más calor (cuesta transpirar).",
                    "How it feels on your body: wind makes cold feel colder (it carries heat away from your skin); humidity makes heat feel hotter (sweat evaporates less).",
                )
            },
        )
        val dew = dewPoint(current.temperature, current.humidity)
        add(
            Detail(tr("Humedad", "Humidity"), "${current.humidity}%", tr("Punto de rocío", "Dew point") + " ${dew.degrees()}") {
                RingGauge(current.humidity / 100f)
            }.apply {
                info = tr(
                    "El punto de rocío dice mejor que la humedad cuánto se siente el aire: hasta 10° seco, de 10° a 16° agradable, de 16° a 20° pesado y desde 20° muy húmedo. Ahora: ${dew.degrees()}.",
                    "Dew point tells better than humidity how the air feels: up to 10° dry, 10–16° comfortable, 16–20° muggy and above 20° oppressive. Now: ${dew.degrees()}.",
                )
            },
        )
        snapshot.airQuality?.europeanAqi?.let {
            add(
                Detail(tr("Calidad del aire", "Air quality"), WeatherCodes.airQualityLabel(it), tr("Índice", "Index") + " $it") {
                    ScaleBar(it / 100f, AqiColors)
                }.apply {
                    val pm = snapshot.airQuality?.pm25?.let { v -> " PM2,5: ${v.roundToInt()} µg/m³." } ?: ""
                    info = tr(
                        "Índice europeo: hasta 20 bueno, 20–40 aceptable, 40–60 moderado (las personas sensibles pueden notarlo), 60–80 malo (mejor evitar ejercicio fuerte afuera), 80–100 muy malo y más de 100 extremadamente malo.$pm",
                        "European index: up to 20 good, 20–40 fair, 40–60 moderate (sensitive people may notice), 60–80 poor (avoid hard exercise outdoors), 80–100 very poor and above 100 extremely poor.$pm",
                    )
                },
            )
        }
        val rain = today?.precipitationProbability ?: 0
        add(
            Detail(tr("Lluvia hoy", "Rain today"), "$rain%", today?.precipitationSum?.takeIf { it > 0 }?.let { "${"%.1f".format(it)} mm" }) {
                RingGauge(rain / 100f, MaterialTheme.colorScheme.tertiary)
            }.apply {
                info = tr(
                    "La probabilidad más alta de lluvia en el día y cuánto llovería en total. 1 mm es un litro por metro cuadrado: menos de 2 mm es poco, más de 10 mm es bastante y más de 30 mm, mucho.",
                    "The highest chance of rain during the day and the total amount. 1 mm is one liter per square meter: under 2 mm is little, over 10 mm is a lot and over 30 mm is heavy.",
                )
            },
        )
        add(
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
            add(
                Detail(tr("Presión", "Pressure"), pressureText(it), trend).apply {
                    info = tr(
                        "Presión del aire llevada al nivel del mar, para comparar entre lugares. Lo normal ronda los 1013 hPa; si baja rápido suele venir mal tiempo, y si sube, se estabiliza. La flecha compara con 3 horas atrás.",
                        "Air pressure adjusted to sea level, so places can be compared. Around 1013 hPa is normal; a quick drop often means bad weather and a rise means it's settling. The arrow compares with 3 hours ago.",
                    )
                },
            )
        }
        current.visibility?.let {
            val km = it / 1000
            add(
                Detail(tr("Visibilidad", "Visibility"), if (km >= 10) "+10 km" else "${km.roundToInt()} km").apply {
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { detail -> DetailTile(detail, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1 && !row[0].wide) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun daylightText(sunrise: String, sunset: String): String {
    val minutes = Duration.between(LocalDateTime.parse(sunrise), LocalDateTime.parse(sunset)).toMinutes()
    return tr("${minutes / 60} h ${minutes % 60} min de luz", "${minutes / 60} h ${minutes % 60} min of daylight")
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

/** Brújula con una flecha que apunta hacia donde sopla el viento. */
@Composable
private fun WindCompass(fromDegrees: Float) {
    val colors = MaterialTheme.colorScheme
    val grow by growth()
    Canvas(Modifier.size(56.dp)) {
        val radius = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(colors.surfaceContainerHigh, radius = radius, center = center)
        // Marcas de los puntos cardinales.
        repeat(8) { i ->
            val angle = i * PI / 4
            val outer = radius - 4.dp.toPx()
            val inner = outer - if (i % 2 == 0) 5.dp.toPx() else 3.dp.toPx()
            drawLine(
                colors.onSurfaceVariant.copy(alpha = 0.6f),
                Offset(center.x + inner * sin(angle).toFloat(), center.y - inner * cos(angle).toFloat()),
                Offset(center.x + outer * sin(angle).toFloat(), center.y - outer * cos(angle).toFloat()),
                1.5.dp.toPx(),
                StrokeCap.Round,
            )
        }
        // El viento "del norte" va hacia el sur: la flecha gira 180° y entra girando al aparecer.
        rotate(fromDegrees + 180f - (1f - grow) * 90f, center) {
            val length = radius - 12.dp.toPx()
            val arrow = Path().apply {
                moveTo(center.x, center.y - length)
                lineTo(center.x + 6.dp.toPx(), center.y - length + 10.dp.toPx())
                lineTo(center.x - 6.dp.toPx(), center.y - length + 10.dp.toPx())
                close()
            }
            drawLine(colors.primary, Offset(center.x, center.y + length * 0.7f), Offset(center.x, center.y - length + 6.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
            drawPath(arrow, colors.primary)
        }
    }
}

/** La luna con la parte iluminada que corresponde a su edad (0 nueva, 0.5 llena). */
@Composable
private fun MoonPhase(age: Float, southern: Boolean) {
    // La parte en sombra es gris pizarra en los dos temas: sobre una tarjeta clara se distingue.
    val dark = Color(0xFF3A4150)
    val light = Color(0xFFF3EBD2)
    Canvas(Modifier.size(48.dp)) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
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
}

/** Arco del sol entre amanecer y atardecer, con el sol en la hora actual. */
@Composable
private fun SunArc(sunrise: String, sunset: String, now: LocalDateTime) {
    val colors = MaterialTheme.colorScheme
    val start = remember(sunrise) { LocalDateTime.parse(sunrise) }
    val end = remember(sunset) { LocalDateTime.parse(sunset) }
    val total = Duration.between(start, end).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(start, now).toMinutes()
    val fraction = (elapsed.toFloat() / total).coerceIn(0f, 1f)
    val isDay = elapsed in 0..total
    val grow by growth()
    val darkTheme = LocalDarkTheme.current
    Row(verticalAlignment = Alignment.Bottom) {
        Canvas(
            Modifier
                .weight(1f)
                .height(64.dp),
        ) {
            val stroke = 3.dp.toPx()
            val sun = 9.dp.toPx()
            val horizon = size.height - 2.dp.toPx()
            val arcRect = Rect(sun, sun, size.width - sun, horizon + (horizon - sun))
            drawLine(colors.onSurfaceVariant.copy(alpha = 0.4f), Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
            drawArc(
                colors.onSurfaceVariant.copy(alpha = 0.45f),
                180f,
                180f,
                false,
                arcRect.topLeft,
                arcRect.size,
                style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))),
            )
            val shown = fraction * grow
            drawArc(
                Brush.horizontalGradient(if (darkTheme) listOf(Color(0xFFFFB74D), Color(0xFFFFD54F)) else listOf(Color(0xFFF57C00), Color(0xFFFFA000))),
                180f,
                180f * shown,
                false,
                arcRect.topLeft,
                arcRect.size,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (isDay) {
                val angle = PI + PI * shown
                val position = Offset(
                    arcRect.center.x + arcRect.width / 2 * cos(angle).toFloat(),
                    arcRect.center.y + arcRect.height / 2 * sin(angle).toFloat(),
                )
                drawCircle(Color(0xFFFFD54F).copy(alpha = 0.35f), sun * 1.6f, position)
                drawCircle(Color(0xFFFFC107), sun, position)
            }
        }
    }
}
