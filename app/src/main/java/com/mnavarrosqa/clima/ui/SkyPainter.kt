package com.mnavarrosqa.clima.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.mnavarrosqa.clima.data.WeatherKind
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dibuja el cielo animado en un Canvas de Android. Lo usan la app (desde Compose) y el fondo de
 * pantalla animado. [clock] va de 0 a 1 en 20 s; cada elemento lo multiplica por un entero para
 * que la animación no salte al reiniciarse.
 */
class SkyPainter {

    private class Particle(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

    private fun particles(count: Int, seed: Int): List<Particle> {
        val random = Random(seed)
        return List(count) {
            Particle(random.nextFloat(), random.nextFloat(), 0.5f + random.nextFloat(), 0.7f + random.nextFloat() * 0.6f, random.nextFloat())
        }
    }

    private val stars = particles(60, 1)
    private val drops = particles(90, 2)
    private val flakes = particles(70, 3)
    private val clouds = particles(5, 4)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tau = 2 * PI.toFloat()

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        clock: Float,
        kind: WeatherKind,
        isDay: Boolean,
        topColor: Int,
        bottomColor: Int,
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(0f, 0f, 0f, height, topColor, bottomColor, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null

        val clearish = kind == WeatherKind.CLEAR || kind == WeatherKind.PARTLY
        if (!isDay && clearish) drawStars(canvas, width, height, density, clock)
        if (isDay && clearish) drawSun(canvas, width, height, clock)
        if (kind != WeatherKind.CLEAR) {
            val alpha = when (kind) {
                WeatherKind.PARTLY -> 0.55f
                WeatherKind.FOG -> 0.35f
                else -> 0.4f
            }
            drawClouds(canvas, width, height, clock, alpha, dark = kind == WeatherKind.STORM || !isDay)
        }
        when (kind) {
            WeatherKind.DRIZZLE -> drawRain(canvas, width, height, density, clock, drops.take(45), 14f)
            WeatherKind.RAIN -> drawRain(canvas, width, height, density, clock, drops, 26f)
            WeatherKind.STORM -> {
                drawRain(canvas, width, height, density, clock, drops, 30f)
                drawLightning(canvas, width, height, clock)
            }
            WeatherKind.SNOW -> drawSnow(canvas, width, height, density, clock)
            WeatherKind.FOG -> drawFog(canvas, width, height, clock)
            else -> Unit
        }
    }

    private fun color(alpha: Float, rgb: Int) = Color.argb((alpha * 255).toInt().coerceIn(0, 255), Color.red(rgb), Color.green(rgb), Color.blue(rgb))

    private fun drawSun(canvas: Canvas, width: Float, height: Float, clock: Float) {
        val cx = width * 0.82f
        val cy = height * 0.1f
        val pulse = 1f + 0.06f * sin(clock * tau * 4)
        val radius = min(width, height) * 0.55f * pulse
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(0x66FFE59A, 0x22FFD54F, 0x00FFD54F),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.color = 0x55FFF3C4
        canvas.drawCircle(cx, cy, min(width, height) * 0.08f, paint)
    }

    private fun drawStars(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float) {
        stars.forEach { s ->
            val twinkle = 0.35f + 0.65f * ((sin((clock * 6 + s.phase) * tau) + 1f) / 2f)
            paint.color = color(0.7f * twinkle, Color.WHITE)
            canvas.drawCircle(s.x * width, s.y * height * 0.6f, 1.2f * s.size * density, paint)
        }
    }

    private fun drawClouds(canvas: Canvas, width: Float, height: Float, clock: Float, alpha: Float, dark: Boolean) {
        paint.color = color(alpha, if (dark) 0xFF9AA6B8.toInt() else Color.WHITE)
        clouds.forEachIndexed { index, c ->
            val cloudWidth = width * (0.45f + 0.25f * c.size)
            val travel = width + cloudWidth
            // Las nubes pares van más lento que las impares para dar profundidad.
            val laps = if (index % 2 == 0) 1 else 2
            val x = ((c.x + clock * laps) % 1f) * travel - cloudWidth
            val y = height * (0.05f + 0.3f * c.y)
            val h = cloudWidth * 0.32f
            rect.set(x, y + h * 0.35f, x + cloudWidth, y + h)
            canvas.drawOval(rect, paint)
            canvas.drawCircle(x + cloudWidth * 0.35f, y + h * 0.42f, h * 0.42f, paint)
            canvas.drawCircle(x + cloudWidth * 0.62f, y + h * 0.5f, h * 0.32f, paint)
        }
    }

    private fun drawRain(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        clock: Float,
        list: List<Particle>,
        length: Float,
    ) {
        paint.color = 0x99CFE3FF.toInt()
        paint.strokeWidth = 1.5f * density
        paint.strokeCap = Paint.Cap.ROUND
        val lengthPx = length * density
        list.forEach { d ->
            val falls = (20 * d.speed).toInt().coerceAtLeast(10)
            val y = ((d.y + clock * falls) % 1f) * (height + lengthPx) - lengthPx
            val x = d.x * width
            canvas.drawLine(x, y, x - 3f * density, y + lengthPx * d.size, paint)
        }
    }

    private fun drawSnow(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float) {
        paint.color = color(0.85f, Color.WHITE)
        flakes.forEach { f ->
            val falls = (6 * f.speed).toInt().coerceAtLeast(3)
            val y = ((f.y + clock * falls) % 1f) * height
            val sway = sin((clock * 8 + f.phase) * tau) * 10f * density
            canvas.drawCircle(f.x * width + sway, y, 2f * f.size * density, paint)
        }
    }

    private fun drawFog(canvas: Canvas, width: Float, height: Float, clock: Float) {
        paint.color = color(0.12f, Color.WHITE)
        repeat(4) { i ->
            val y = height * (0.2f + i * 0.18f)
            val shift = sin((clock * 2 + i * 0.25f) * tau) * width * 0.08f
            rect.set(-width * 0.1f + shift, y, width * 1.1f + shift, y + height * 0.06f)
            canvas.drawRoundRect(rect, height * 0.03f, height * 0.03f, paint)
        }
    }

    /** Dos relámpagos por vuelta del reloj: un destello corto de toda la pantalla. */
    private fun drawLightning(canvas: Canvas, width: Float, height: Float, clock: Float) {
        val alpha = listOf(0.31f, 0.78f).maxOf { start ->
            val t = clock - start
            if (t in 0f..0.012f) 0.35f * (1f - t / 0.012f) else 0f
        }
        if (alpha > 0f) {
            paint.color = color(alpha, Color.WHITE)
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }
}
