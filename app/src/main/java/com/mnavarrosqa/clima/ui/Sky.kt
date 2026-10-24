package com.mnavarrosqa.clima.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mnavarrosqa.clima.data.WeatherKind
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Colores de cielo arriba y abajo según el clima y si es de día. */
fun skyColors(kind: WeatherKind, isDay: Boolean): Pair<Color, Color> = when (kind) {
    WeatherKind.CLEAR, WeatherKind.PARTLY ->
        if (isDay) Color(0xFF2F6FD0) to Color(0xFF8CC4FF) else Color(0xFF0A1430) to Color(0xFF2A3B66)
    WeatherKind.CLOUDY ->
        if (isDay) Color(0xFF55708E) to Color(0xFFA3B6CA) else Color(0xFF1A2230) to Color(0xFF384457)
    WeatherKind.FOG ->
        if (isDay) Color(0xFF7D8B9B) to Color(0xFFC3CBD4) else Color(0xFF262C35) to Color(0xFF4A525E)
    WeatherKind.DRIZZLE, WeatherKind.RAIN ->
        if (isDay) Color(0xFF3B4F66) to Color(0xFF71879D) else Color(0xFF111924) to Color(0xFF2B3747)
    WeatherKind.SNOW ->
        if (isDay) Color(0xFF6F86A2) to Color(0xFFD0DCEA) else Color(0xFF1C2433) to Color(0xFF465469)
    WeatherKind.STORM ->
        if (isDay) Color(0xFF283041) to Color(0xFF4F586B) else Color(0xFF0D1119) to Color(0xFF262D3B)
}

private class Particle(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

private fun particles(count: Int, seed: Int): List<Particle> {
    val random = Random(seed)
    return List(count) {
        Particle(
            x = random.nextFloat(),
            y = random.nextFloat(),
            size = 0.5f + random.nextFloat(),
            speed = 0.7f + random.nextFloat() * 0.6f,
            phase = random.nextFloat(),
        )
    }
}

/**
 * Fondo animado de la app. Usa un solo reloj de 0 a 1 en 20 s; cada elemento lo multiplica por un
 * entero, así la animación no salta al reiniciarse.
 */
@Composable
fun AnimatedSky(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier) {
    val (top, bottom) = skyColors(kind, isDay)
    val topColor by animateColorAsState(top, tween(1200), label = "cielo arriba")
    val bottomColor by animateColorAsState(bottom, tween(1200), label = "cielo abajo")
    val clock by rememberInfiniteTransition(label = "cielo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "reloj",
    )
    val stars = remember { particles(60, 1) }
    val drops = remember { particles(90, 2) }
    val flakes = remember { particles(70, 3) }
    val clouds = remember { particles(5, 4) }

    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(topColor, bottomColor)))
        val cloudy = kind != WeatherKind.CLEAR
        if (!isDay && kind in setOf(WeatherKind.CLEAR, WeatherKind.PARTLY)) drawStars(stars, clock)
        if (isDay && kind in setOf(WeatherKind.CLEAR, WeatherKind.PARTLY)) drawSun(clock)
        if (cloudy) {
            val alpha = when (kind) {
                WeatherKind.PARTLY -> 0.55f
                WeatherKind.FOG -> 0.35f
                else -> 0.4f
            }
            drawClouds(clouds, clock, alpha, dark = kind == WeatherKind.STORM || !isDay)
        }
        when (kind) {
            WeatherKind.DRIZZLE -> drawRain(drops.take(45), clock, length = 14f)
            WeatherKind.RAIN -> drawRain(drops, clock, length = 26f)
            WeatherKind.STORM -> {
                drawRain(drops, clock, length = 30f)
                drawLightning(clock)
            }
            WeatherKind.SNOW -> drawSnow(flakes, clock)
            WeatherKind.FOG -> drawFog(clock)
            else -> Unit
        }
    }
}

private fun DrawScope.drawSun(clock: Float) {
    val center = Offset(size.width * 0.82f, size.height * 0.1f)
    val pulse = 1f + 0.06f * sin(clock * 2 * PI.toFloat() * 4)
    val radius = size.minDimension * 0.55f * pulse
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0x66FFE59A), Color(0x22FFD54F), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    drawCircle(Color(0x55FFF3C4), radius = size.minDimension * 0.08f, center = center)
}

private fun DrawScope.drawStars(stars: List<Particle>, clock: Float) {
    stars.forEach { s ->
        val twinkle = 0.35f + 0.65f * ((sin((clock * 6 + s.phase) * 2 * PI.toFloat()) + 1f) / 2f)
        drawCircle(
            Color.White.copy(alpha = 0.7f * twinkle),
            radius = 1.2f * s.size * density,
            center = Offset(s.x * size.width, s.y * size.height * 0.6f),
        )
    }
}

private fun DrawScope.drawClouds(clouds: List<Particle>, clock: Float, alpha: Float, dark: Boolean) {
    val color = (if (dark) Color(0xFF9AA6B8) else Color.White).copy(alpha = alpha)
    clouds.forEachIndexed { index, c ->
        val width = size.width * (0.45f + 0.25f * c.size)
        val travel = size.width + width
        // Las nubes pares van más lento que las impares para dar profundidad.
        val laps = if (index % 2 == 0) 1 else 2
        val x = ((c.x + clock * laps) % 1f) * travel - width
        val y = size.height * (0.05f + 0.3f * c.y)
        drawCloud(Offset(x, y), width, color)
    }
}

private fun DrawScope.drawCloud(topLeft: Offset, width: Float, color: Color) {
    val h = width * 0.32f
    drawOval(color, topLeft = Offset(topLeft.x, topLeft.y + h * 0.35f), size = Size(width, h * 0.65f))
    drawCircle(color, radius = h * 0.42f, center = Offset(topLeft.x + width * 0.35f, topLeft.y + h * 0.42f))
    drawCircle(color, radius = h * 0.32f, center = Offset(topLeft.x + width * 0.62f, topLeft.y + h * 0.5f))
}

private fun DrawScope.drawRain(drops: List<Particle>, clock: Float, length: Float) {
    val color = Color(0x99CFE3FF)
    drops.forEach { d ->
        val falls = (20 * d.speed).toInt().coerceAtLeast(10)
        val y = ((d.y + clock * falls) % 1f) * (size.height + length * density) - length * density
        val x = d.x * size.width
        drawLine(
            color,
            start = Offset(x, y),
            end = Offset(x - 3f * density, y + length * density * d.size),
            strokeWidth = 1.5f * density,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnow(flakes: List<Particle>, clock: Float) {
    flakes.forEach { f ->
        val falls = (6 * f.speed).toInt().coerceAtLeast(3)
        val y = ((f.y + clock * falls) % 1f) * size.height
        val sway = sin((clock * 8 + f.phase) * 2 * PI.toFloat()) * 10f * density
        drawCircle(
            Color.White.copy(alpha = 0.85f),
            radius = 2f * f.size * density,
            center = Offset(f.x * size.width + sway, y),
        )
    }
}

private fun DrawScope.drawFog(clock: Float) {
    repeat(4) { i ->
        val y = size.height * (0.2f + i * 0.18f)
        val shift = sin((clock * 2 + i * 0.25f) * 2 * PI.toFloat()) * size.width * 0.08f
        drawRoundRect(
            Color.White.copy(alpha = 0.12f),
            topLeft = Offset(-size.width * 0.1f + shift, y),
            size = Size(size.width * 1.2f, size.height * 0.06f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.03f),
        )
    }
}

/** Dos relámpagos por vuelta del reloj: un destello corto de toda la pantalla. */
private fun DrawScope.drawLightning(clock: Float) {
    val flashes = listOf(0.31f, 0.78f)
    val alpha = flashes.maxOf { start ->
        val t = clock - start
        if (t in 0f..0.012f) 0.35f * (1f - t / 0.012f) else 0f
    }
    if (alpha > 0f) drawRect(Color.White.copy(alpha = alpha))
}
