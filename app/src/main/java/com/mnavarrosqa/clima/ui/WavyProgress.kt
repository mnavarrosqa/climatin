package com.mnavarrosqa.clima.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Barra de avance ondulada, al estilo de Material 3 Expressive: la parte hecha es una onda que se
 * desplaza y lo que falta es una línea recta. Propia porque la de la librería todavía no es pública.
 */
@Composable
fun WavyProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val phase by rememberInfiniteTransition(label = "onda").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "fase",
    )
    Canvas(
        modifier
            .height(12.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f) },
    ) {
        val stroke = 4.dp.toPx()
        val amplitude = 3.dp.toPx()
        val wavelength = 24.dp.toPx()
        val y = size.height / 2
        val done = size.width * progress().coerceIn(0f, 1f)
        val gap = 6.dp.toPx()
        if (done + gap < size.width) {
            drawLine(colors.secondaryContainer, Offset(done + gap, y), Offset(size.width, y), stroke, StrokeCap.Round)
        }
        if (done > 0f) {
            val wave = Path()
            var x = 0f
            while (x <= done) {
                val wy = y + amplitude * sin(((x / wavelength) - phase) * 2 * PI).toFloat()
                if (x == 0f) wave.moveTo(x, wy) else wave.lineTo(x, wy)
                x += 2f
            }
            drawPath(wave, colors.primary, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}
