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
import androidx.compose.runtime.remember
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
)

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
        today?.let {
            add(
                Detail(
                    tr("Sol", "Sun"),
                    timeOf(it.sunrise) + " – " + timeOf(it.sunset),
                    daylightText(it.sunrise, it.sunset),
                    wide = true,
                ) { SunArc(it.sunrise, it.sunset, snapshot.nowAtPlace()) },
            )
        }
        (current.uvIndex ?: today?.uvIndex)?.let {
            add(
                Detail(tr("Índice UV", "UV index"), "${it.roundToInt()}", WeatherCodes.uvLabel(it).replaceFirstChar { c -> c.uppercase() }) {
                    ScaleBar((it / 11).toFloat(), UvColors)
                },
            )
        }
        add(
            Detail(
                tr("Viento", "Wind"),
                windText(current.windSpeed),
                current.windDirection?.let { tr("Del", "From") + " " + windFrom(it) },
                visual = current.windDirection?.let { direction -> @Composable { WindCompass(direction.toFloat()) } },
            ),
        )
        add(Detail(tr("Humedad", "Humidity"), "${current.humidity}%") { RingGauge(current.humidity / 100f) })
        snapshot.airQuality?.europeanAqi?.let {
            add(
                Detail(tr("Calidad del aire", "Air quality"), WeatherCodes.airQualityLabel(it), tr("Índice", "Index") + " $it") {
                    ScaleBar(it / 100f, AqiColors)
                },
            )
        }
        val rain = today?.precipitationProbability ?: 0
        add(Detail(tr("Lluvia hoy", "Rain today"), "$rain%", today?.precipitationSum?.takeIf { it > 0 }?.let { "${"%.1f".format(it)} mm" }) {
            RingGauge(rain / 100f, MaterialTheme.colorScheme.tertiary)
        })
        add(Detail(tr("Luna", "Moon"), Moon.phaseName(date)) { MoonPhase(Moon.age(date).toFloat()) })
        current.pressure?.let { add(Detail(tr("Presión", "Pressure"), pressureText(it))) }
        current.visibility?.let {
            val km = it / 1000
            add(Detail(tr("Visibilidad", "Visibility"), if (km >= 10) "+10 km" else "${km.roundToInt()} km"))
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = listOfNotNull(detail.label, detail.value, detail.note).joinToString(", ")
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

/** Avance de 0 a 1 que crece una vez al aparecer el dibujo. */
@Composable
private fun growth(): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, delayMillis = 200, easing = EaseOutCubic)) }
    return progress.value
}

/** Barra con la escala de colores y un punto en el valor actual. */
@Composable
private fun ScaleBar(fraction: Float, colors: List<Color>) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val knob = MaterialTheme.colorScheme.onSurface
    val grow = growth()
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
    val grow = growth()
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
    val grow = growth()
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
private fun MoonPhase(age: Float) {
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
        val waxing = age < 0.5f
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
    val start = LocalDateTime.parse(sunrise)
    val end = LocalDateTime.parse(sunset)
    val total = Duration.between(start, end).toMinutes().coerceAtLeast(1)
    val elapsed = Duration.between(start, now).toMinutes()
    val fraction = (elapsed.toFloat() / total).coerceIn(0f, 1f)
    val isDay = elapsed in 0..total
    val grow = growth()
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
