package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.ui.theme.LocalDarkTheme
import com.mnavarrosqa.clima.data.tr
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.mnavarrosqa.clima.data.windText
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import java.time.LocalDateTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.Hour
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.shortHour

private val SLOT = 60.dp

/** Curva de temperatura por hora con íconos y probabilidad de lluvia, desplazable de costado. */
@Composable
fun HourlyChart(hours: List<Hour>, modifier: Modifier = Modifier, sunEvents: List<SunEvent> = emptyList()) {
    if (hours.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    // Una entrada de caché por hora: si no, al dibujar la curva se vuelven a medir los 24 textos en cada cuadro.
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface)
    val min = hours.minOf { it.temperature }
    val max = hours.maxOf { it.temperature }
    val span = (max - min).coerceAtLeast(1.0)
    // La curva se dibuja de izquierda a derecha al aparecer y cuando llegan datos nuevos.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(hours.first().time, hours.size) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1100, easing = EaseOutCubic))
    }

    var selected by rememberSaveable(hours.first().time) { mutableStateOf<Int?>(null) }
    val view = LocalView.current
    val select: (Int) -> Unit = { index ->
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        selected = if (selected == index) null else index
    }
    val scroll = rememberScrollState()
    val dark = LocalDarkTheme.current
    val lineColors = remember(hours, dark) {
        hours.map { TempColors.forTheme(TempColors.of(it.temperature), dark) }.let { if (it.size < 2) it + it else it }
    }
    val highlight = colors.primary.copy(alpha = 0.12f)

    Column(modifier) {
    Column(
        Modifier
            .fadingEdges(fadeStart = scroll.value > 0, fadeEnd = scroll.canScrollForward)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp),
    ) {
        Canvas(
            Modifier
                .width(SLOT * hours.size)
                .height(84.dp)
                .pointerInput(hours.size) {
                    detectTapGestures { tap -> select((tap.x / (size.width / hours.size)).toInt().coerceIn(0, hours.lastIndex)) }
                },
        ) {
            val slot = size.width / hours.size
            selected?.let { index ->
                // La columna de la hora tocada se resalta; abajo sigue en la fila de íconos.
                val corner = CornerRadius(16.dp.toPx())
                drawPath(
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                Rect(slot * index + 4.dp.toPx(), 0f, slot * (index + 1) - 4.dp.toPx(), size.height),
                                topLeft = corner,
                                topRight = corner,
                            ),
                        )
                    },
                    highlight,
                )
            }
            val top = 30.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            val points = hours.mapIndexed { i, h ->
                val fraction = ((h.temperature - min) / span).toFloat()
                Offset(slot * i + slot / 2, bottom - fraction * (bottom - top))
            }
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val cur = points[i]
                    val midX = (prev.x + cur.x) / 2
                    cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            val visibleWidth = size.width * reveal.value
            clipRect(right = visibleWidth) {
                drawPath(
                    fill,
                    Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.35f), colors.primary.copy(alpha = 0f))),
                )
                // La curva cambia de color con la temperatura, como en Mercury Weather.
                drawPath(
                    line,
                    Brush.horizontalGradient(lineColors, startX = points.first().x, endX = points.last().x),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            points.forEachIndexed { i, p ->
                // Cada punto aparece cuando la curva llega hasta él.
                if (p.x > visibleWidth + slot / 2) return@forEachIndexed
                val big = i == selected
                drawCircle(colors.surface, radius = (if (big) 7f else 5f).dp.toPx(), center = p)
                drawCircle(lineColors[i], radius = (if (big) 5f else 3.5f).dp.toPx(), center = p)
                val text = measurer.measure(hours[i].temperature.degrees(), labelStyle)
                drawText(
                    text,
                    topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height - 6.dp.toPx()),
                )
            }
        }
        Row {
            hours.forEachIndexed { index, hour ->
                val probability = hour.precipitationProbability ?: 0
                val time = if (index == 0) tr("Ahora", "Now") else shortHour(hour.dateTime)
                Column(
                    // La temperatura está dibujada en el gráfico: TalkBack la lee desde acá.
                    Modifier
                        .width(SLOT)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                        .then(if (index == selected) Modifier.background(highlight) else Modifier)
                        .clickable { select(index) }
                        .clearAndSetSemantics {
                            contentDescription = "$time: ${hour.temperature.degrees()}, ${WeatherCodes.description(hour.code)}" +
                                if (probability >= 10) ", " + tr("lluvia", "rain") + " $probability%" else ""
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(WeatherCodes.icon(hour.code, hour.isDay)),
                        contentDescription = WeatherCodes.description(hour.code),
                        modifier = Modifier.size(30.dp),
                    )
                    Text(
                        if (probability >= 10) "$probability%" else " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.tertiary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (index == 0) tr("Ahora", "Now") else shortHour(hour.dateTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Si el sol sale o se pone en esta hora, se marca debajo, como en el Clima de Apple.
                    sunEvents.firstOrNull { !it.time.isBefore(hour.dateTime) && it.time.isBefore(hour.dateTime.plusHours(1)) }?.let { event ->
                        Text(
                            (if (event.sunrise) "↑ " else "↓ ") + shortHour(event.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dark) Color(0xFFFFB74D) else Color(0xFFC25100),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
    AnimatedVisibility(
        visible = selected != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        selected?.let { hours.getOrNull(it) }?.let { hour -> HourDetail(hour, isNow = selected == 0) }
    }
    }
}

/** Lo que pasa en la hora tocada: estado, sensación térmica, viento y lluvia. */
@Composable
private fun HourDetail(hour: Hour, isNow: Boolean) {
    val colors = MaterialTheme.colorScheme
    val time = if (isNow) tr("Ahora", "Now") else shortHour(hour.dateTime)
    val facts = listOfNotNull(
        hour.feelsLike?.let { tr("Sensación", "Feels like") + " ${it.degrees()}" },
        hour.windSpeed?.let { tr("Viento", "Wind") + " ${windText(it)}" },
        hour.precipitationProbability?.takeIf { it > 0 }?.let { tr("Lluvia", "Rain") + " $it%" },
        hour.precipitation?.takeIf { it > 0 }?.let { "${"%.1f".format(it)} mm" },
    )
    Surface(
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(WeatherCodes.icon(hour.code, hour.isDay)),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "$time · ${hour.temperature.degrees()} · ${WeatherCodes.description(hour.code)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (facts.isNotEmpty()) {
                    Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Desvanece los bordes de una fila desplazable para que se note que sigue. */
private fun Modifier.fadingEdges(fadeStart: Boolean, fadeEnd: Boolean): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val edge = (32.dp.toPx() / size.width).coerceIn(0f, 0.5f)
            drawRect(
                Brush.horizontalGradient(
                    0f to if (fadeStart) Color.Transparent else Color.Black,
                    edge to Color.Black,
                    1f - edge to Color.Black,
                    1f to if (fadeEnd) Color.Transparent else Color.Black,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/** Amanecer o atardecer, para marcarlo en la fila de horas. */
data class SunEvent(val time: LocalDateTime, val sunrise: Boolean)

fun sunEventsOf(days: List<com.mnavarrosqa.clima.data.Day>): List<SunEvent> = days.flatMap { day ->
    listOfNotNull(
        runCatching { SunEvent(LocalDateTime.parse(day.sunrise), true) }.getOrNull(),
        runCatching { SunEvent(LocalDateTime.parse(day.sunset), false) }.getOrNull(),
    )
}
