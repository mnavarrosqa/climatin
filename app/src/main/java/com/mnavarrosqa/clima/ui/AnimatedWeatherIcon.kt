package com.mnavarrosqa.clima.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ícono del clima con vida propia según el tipo, más allá de mecerse: el sol con un resplandor
 * que respira, la luna con estrellas que titilan, gotas o copos que caen debajo de la nube y la
 * tormenta con destellos. Un ciclo dura 24 s y todo se lee al dibujar: no se recompone.
 */
@Composable
fun AnimatedWeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val kind = WeatherCodes.kind(code)
    val clock by rememberInfiniteTransition(label = "icono").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "reloj",
    )
    val tau = 2 * PI.toFloat()
    Box(modifier.size(size)) {
        // Detrás del ícono: resplandor del sol o de la luna, y el destello de la tormenta.
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(this.size.width / 2, this.size.height * 0.45f)
            val radius = this.size.minDimension / 2
            when {
                kind == WeatherKind.CLEAR && isDay -> {
                    val breathe = 0.85f + 0.15f * sin(clock * tau * 4)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color(0xFFFFD54F).copy(alpha = 0.45f * breathe), Color(0x00FFD54F)),
                            center = center,
                            radius = radius * 1.05f * breathe,
                        ),
                        radius = radius * 1.05f * breathe,
                        center = center,
                    )
                }
                kind == WeatherKind.CLEAR || (kind == WeatherKind.PARTLY && !isDay) -> {
                    drawCircle(
                        Brush.radialGradient(listOf(Color(0x55C8D7FF), Color(0x00C8D7FF)), center = center, radius = radius),
                        radius = radius,
                        center = center,
                    )
                    // Tres estrellitas alrededor que titilan a destiempo.
                    STARS.forEachIndexed { i, (x, y) ->
                        val twinkle = (sin((clock * (5 + i) + i * 0.3f) * tau) + 1f) / 2f
                        drawCircle(
                            Color.White.copy(alpha = 0.25f + 0.65f * twinkle),
                            radius = radius * (0.035f + 0.02f * twinkle),
                            center = Offset(this.size.width * x, this.size.height * y),
                        )
                    }
                }
                kind == WeatherKind.STORM -> {
                    // Dos destellos seguidos por vuelta: la nube se ilumina desde adentro.
                    val phase = (clock * 4) % 1f
                    val flash = if (phase < 0.03f || phase in 0.05f..0.07f) 1f else 0f
                    if (flash > 0f) {
                        drawCircle(
                            Brush.radialGradient(listOf(Color(0x99E1E8FF), Color(0x00E1E8FF)), center = center, radius = radius * 1.1f),
                            radius = radius * 1.1f,
                            center = center,
                        )
                    }
                }
            }
        }
        Image(
            painter = painterResource(WeatherCodes.icon(code, isDay)),
            contentDescription = WeatherCodes.description(code),
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val wave = sin(clock * tau * 3)
                    when (kind) {
                        WeatherKind.CLEAR -> if (isDay) {
                            // Vaivén leve en lugar de girar: se siente vivo sin marear.
                            rotationZ = wave * 6f
                            val pulse = 1f + 0.03f * sin(clock * tau * 2)
                            scaleX = pulse
                            scaleY = pulse
                        } else {
                            val pulse = 1f + 0.04f * wave
                            scaleX = pulse
                            scaleY = pulse
                        }
                        WeatherKind.PARTLY, WeatherKind.CLOUDY, WeatherKind.FOG ->
                            translationX = wave * 4.dp.toPx()
                        WeatherKind.DRIZZLE, WeatherKind.RAIN, WeatherKind.SNOW, WeatherKind.STORM ->
                            translationY = wave * 2.dp.toPx()
                    }
                },
        )
        // Delante, debajo de la nube: gotas o copos que caen sin parar.
        if (kind == WeatherKind.RAIN || kind == WeatherKind.DRIZZLE || kind == WeatherKind.STORM || kind == WeatherKind.SNOW) {
            Canvas(Modifier.matchParentSize()) {
                val w = this.size.width
                val h = this.size.height
                val snow = kind == WeatherKind.SNOW
                val count = if (kind == WeatherKind.DRIZZLE) 4 else 6
                repeat(count) { i ->
                    val lane = (i + 0.5f) / count
                    // Cada una a su ritmo; caen desde abajo de la nube hasta el borde.
                    val speed = if (snow) 6 + i % 3 else 14 + (i * 5) % 7
                    val t = (clock * speed + i * 0.37f) % 1f
                    val x = w * (0.22f + 0.56f * lane) + if (snow) sin((clock * 9 + i) * tau) * w * 0.03f else -t * w * 0.05f
                    val y = h * (0.62f + 0.36f * t)
                    val fade = if (t < 0.15f) t / 0.15f else if (t > 0.8f) (1f - t) / 0.2f else 1f
                    if (snow) {
                        drawCircle(Color.White.copy(alpha = 0.9f * fade), radius = w * 0.028f, center = Offset(x, y))
                    } else {
                        val length = h * if (kind == WeatherKind.DRIZZLE) 0.05f else 0.08f
                        drawLine(
                            Color(0xFF64B5F6).copy(alpha = 0.85f * fade),
                            start = Offset(x, y),
                            end = Offset(x - length * 0.25f, y + length),
                            strokeWidth = w * 0.025f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

/** Posición (en fracción del ícono) de las estrellitas de la noche despejada. */
private val STARS = listOf(0.18f to 0.2f, 0.85f to 0.32f, 0.72f to 0.82f)
