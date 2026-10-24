package com.mnavarrosqa.clima.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
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
fun HourlyChart(hours: List<Hour>, modifier: Modifier = Modifier) {
    if (hours.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface)
    val min = hours.minOf { it.temperature }
    val max = hours.maxOf { it.temperature }
    val span = (max - min).coerceAtLeast(1.0)

    Column(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        Canvas(
            Modifier
                .width(SLOT * hours.size)
                .height(84.dp),
        ) {
            val slot = size.width / hours.size
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
            drawPath(
                fill,
                Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.35f), colors.primary.copy(alpha = 0f))),
            )
            drawPath(line, colors.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { i, p ->
                drawCircle(colors.surface, radius = 5.dp.toPx(), center = p)
                drawCircle(colors.primary, radius = 3.5.dp.toPx(), center = p)
                val text = measurer.measure(hours[i].temperature.degrees(), labelStyle)
                drawText(
                    text,
                    topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height - 6.dp.toPx()),
                )
            }
        }
        Row {
            hours.forEachIndexed { index, hour ->
                Column(
                    Modifier.width(SLOT),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(WeatherCodes.icon(hour.code, hour.isDay)),
                        contentDescription = WeatherCodes.description(hour.code),
                        modifier = Modifier.size(30.dp),
                    )
                    val probability = hour.precipitationProbability ?: 0
                    Text(
                        if (probability >= 10) "$probability%" else " ",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.tertiary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (index == 0) "Ahora" else shortHour(hour.dateTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

