package com.mnavarrosqa.clima.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.RainOutlook
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.rainOutlook
import com.mnavarrosqa.clima.data.rainOutlookText
import com.mnavarrosqa.clima.data.tr
import java.time.Duration

/** Los próximos 8 cuartos de hora con lluvia (cada valor es de los 15 minutos anteriores a su hora). */
fun nextHourRain(snapshot: WeatherSnapshot): List<Double>? {
    val now = snapshot.nowAtPlace()
    val slots = snapshot.minutely.filter { it.dateTime.isAfter(now) }.take(8)
    if (slots.size < 4 || slots.none { it.precipitation >= 0.1 }) return null
    return slots.map { it.precipitation }
}

/**
 * Las barras para la tarjeta propia de la próxima hora, o null si no va. Solo cuando la lluvia
 * empieza dentro de 60 minutos: en un teléfono chico ocupaba la primera pantalla por una lluvia de
 * dentro de dos horas, que ya cuenta la frase de "Próximas horas".
 */
fun nextHourRainForCard(snapshot: WeatherSnapshot): List<Double>? {
    if (snapshot.rainOutlook(withinMinutes = 60) !is RainOutlook.Starts) return null
    return nextHourRain(snapshot)
}

/** Tarjeta de la próxima hora, como la del Pixel: cuándo empieza o para y barras cada 15 minutos. */
@Composable
fun NextHourRainCard(snapshot: WeatherSnapshot, values: List<Double>) {
    val outlook = snapshot.rainOutlook(withinMinutes = 120)
    val title = outlook?.let(::rainOutlookText) ?: tr("Lluvia en las próximas dos horas", "Rain in the next two hours")
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    GlassCard(tr("Próxima hora", "Next hour")) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .clearAndSetSemantics { contentDescription = title },
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = 12.dp),
            ) {
                val slot = size.width / values.size
                val width = slot * 0.62f
                values.forEachIndexed { i, mm ->
                    val x = slot * i + (slot - width) / 2
                    drawRoundRect(track, Offset(x, 0f), Size(width, size.height), CornerRadius(width / 2))
                    // 2 mm en 15 minutos ya es lluvia fuerte: llena la barra.
                    val h = size.height * (mm / 2.0).coerceIn(0.0, 1.0).toFloat()
                    if (h > 0f) drawRoundRect(bar, Offset(x, size.height - h.coerceAtLeast(width)), Size(width, h.coerceAtLeast(width)), CornerRadius(width / 2))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val now = snapshot.nowAtPlace()
                val first = snapshot.minutely.firstOrNull { it.dateTime.isAfter(now) }
                values.indices.forEach { i ->
                    val label = when {
                        i == 0 -> tr("Ahora", "Now")
                        i % 2 == 0 && first != null ->
                            "${Duration.between(now, first.dateTime.minusMinutes(15).plusMinutes(15L * i)).toMinutes().coerceAtLeast(0).let { (it + 7) / 15 * 15 }}'"
                        else -> ""
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
