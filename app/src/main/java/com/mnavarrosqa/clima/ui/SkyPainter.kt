package com.mnavarrosqa.clima.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.mnavarrosqa.clima.data.WeatherKind
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dibuja el cielo animado en un Canvas de Android. Lo usan la app (desde Compose) y el fondo de
 * pantalla animado.
 *
 * [clock] va de 0 a 1 en un minuto; cada elemento lo multiplica por un entero para que la
 * animación no salte al reiniciarse. Las nubes, la nieve, la niebla y los brillos son "puffs":
 * círculos con degradado radial que se desvanece, así los bordes quedan suaves.
 */
class SkyPainter {

    private class Particle(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

    private class Puff(val dx: Float, val dy: Float, val radius: Float)

    private fun particles(count: Int, seed: Int): List<Particle> {
        val random = Random(seed)
        return List(count) {
            Particle(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat())
        }
    }

    private val stars = particles(90, 1)
    private val farDrops = particles(70, 2)
    private val nearDrops = particles(50, 5)
    private val flakes = particles(80, 3)
    private val clouds = particles(6, 4)

    /** Cada nube son seis puffs en arco, un poco al azar. */
    private val cloudShapes = clouds.indices.map { i ->
        val random = Random(10 + i)
        List(6) { k ->
            val t = k / 5f
            val arc = sin(t * PI.toFloat())
            Puff(
                dx = (t - 0.5f) * 0.9f + (random.nextFloat() - 0.5f) * 0.1f,
                dy = -arc * 0.28f + (random.nextFloat() - 0.5f) * 0.06f,
                radius = 0.22f + 0.18f * arc + random.nextFloat() * 0.05f,
            )
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val tau = 2 * PI.toFloat()

    /** Degradados radiales de radio 1 por color; se escalan con el canvas en vez de crear uno por puff. */
    private val puffShaders = HashMap<Int, Shader>()

    private fun puffShader(rgb: Int): Shader = puffShaders.getOrPut(rgb) {
        val solid = rgb or 0xFF000000.toInt()
        val half = (rgb and 0x00FFFFFF) or 0x8C000000.toInt()
        val clear = rgb and 0x00FFFFFF
        RadialGradient(0f, 0f, 1f, intArrayOf(solid, half, clear), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    private val rayShader = LinearGradient(0f, 0f, 1f, 0f, 0x14FFF5D2, 0x00FFF5D2, Shader.TileMode.CLAMP)
    private val rayPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(1f, -0.067f)
        lineTo(1f, 0.067f)
        close()
    }

    // El degradado del fondo se reusa mientras no cambien el alto ni los colores: antes se creaba
    // uno nuevo en cada cuadro, 60 veces por segundo.
    private var backgroundKey: Pair<Float, SkyGradient>? = null
    private var backgroundShaderCache: Shader? = null

    private fun backgroundShader(height: Float, colors: SkyGradient): Shader {
        val key = height to colors
        backgroundShaderCache?.takeIf { backgroundKey == key }?.let { return it }
        return LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(colors.top, colors.middle, colors.bottom),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        ).also {
            backgroundKey = key
            backgroundShaderCache = it
        }
    }

    private fun puff(canvas: Canvas, x: Float, y: Float, radiusX: Float, radiusY: Float, alpha: Float, rgb: Int) {
        if (alpha <= 0f || radiusX <= 0f || radiusY <= 0f) return
        paint.shader = puffShader(rgb)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(radiusX, radiusY)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
        paint.shader = null
        paint.alpha = 255
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        clock: Float,
        kind: WeatherKind,
        isDay: Boolean,
        colors: SkyGradient,
        background: Boolean = true,
        celestial: Boolean = true,
        cloudAlpha: Float = 1f,
    ) {
        // Los fondos de pantalla "Formas" y "Oscuro" ponen su propio fondo y usan solo algunas capas.
        if (background) {
            paint.shader = backgroundShader(height, colors)
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
        }

        val clearish = kind == WeatherKind.CLEAR || kind == WeatherKind.PARTLY
        if (clearish && celestial) {
            if (isDay) drawSun(canvas, width, height, clock) else drawNight(canvas, width, height, density, clock)
        }
        if (kind != WeatherKind.CLEAR && cloudAlpha > 0f) drawClouds(canvas, width, height, clock, kind, isDay, cloudAlpha)
        when (kind) {
            WeatherKind.DRIZZLE -> drawRain(canvas, width, height, density, clock, farDrops, 12f, 0.35f, 40, 1f)
            WeatherKind.RAIN, WeatherKind.STORM -> {
                drawRain(canvas, width, height, density, clock, farDrops, 16f, 0.35f, 50, 1f)
                drawRain(canvas, width, height, density, clock, nearDrops, 30f, 0.6f, 70, 1.6f)
            }
            WeatherKind.SNOW -> drawSnow(canvas, width, height, density, clock)
            WeatherKind.FOG -> drawFog(canvas, width, height, clock)
            else -> Unit
        }
        if (kind == WeatherKind.STORM) drawLightning(canvas, width, height, clock)
    }

    /** Sol con brillo que respira y rayos suaves que giran una vuelta por minuto. */
    private fun drawSun(canvas: Canvas, width: Float, height: Float, clock: Float) {
        val cx = width * 0.8f
        val cy = height * 0.1f
        val base = min(width, height)
        val breathe = 1f + 0.05f * sin(clock * tau * 3)
        puff(canvas, cx, cy, base * 0.9f * breathe, base * 0.9f * breathe, 0.30f, 0xFFECAA)

        paint.shader = rayShader
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(clock * 360f)
        val length = base * 0.75f
        repeat(12) {
            canvas.rotate(30f)
            canvas.save()
            canvas.scale(length, length)
            canvas.drawPath(rayPath, paint)
            canvas.restore()
        }
        canvas.restore()
        paint.shader = null

        puff(canvas, cx, cy, base * 0.16f, base * 0.16f, 0.95f, 0xFFFAE1)
    }

    /** Estrellas que titilan a distinto ritmo, una estrella fugaz por minuto y la luna con halo. */
    private fun drawNight(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float) {
        stars.forEach { s ->
            val speed = 2 + (s.speed * 4).toInt()
            val twinkle = 0.25f + 0.75f * (sin((clock * speed + s.phase) * tau) + 1f) / 2f
            paint.color = Color.argb((255 * 0.85f * twinkle * (0.4f + 0.6f * s.size)).toInt(), 255, 255, 255)
            canvas.drawCircle(s.x * width, s.y * height * 0.65f, (0.6f + s.size * 1.1f) * density, paint)
        }

        val t = (clock - 0.42f) / 0.03f
        if (t in 0f..1f) {
            val x0 = width * 0.15f + t * width * 0.5f
            val y0 = height * 0.08f + t * height * 0.18f
            val x1 = x0 - 60f * density
            val y1 = y0 - 22f * density
            linePaint.shader = LinearGradient(
                x0, y0, x1, y1,
                Color.argb((230 * (1f - t)).toInt(), 255, 255, 255), Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            linePaint.strokeWidth = 1.6f * density
            canvas.drawLine(x0, y0, x1, y1, linePaint)
            linePaint.shader = null
        }

        val mx = width * 0.78f
        val my = height * 0.13f
        val mr = min(width, height) * 0.07f
        puff(canvas, mx, my, mr * 4, mr * 4, 0.18f, 0xC8D7FF)
        paint.color = 0xFFF2F5FF.toInt()
        canvas.drawCircle(mx, my, mr, paint)
    }

    /** Dos capas de nubes: las de atrás más grandes, tenues y lentas. */
    private fun drawClouds(canvas: Canvas, width: Float, height: Float, clock: Float, kind: WeatherKind, isDay: Boolean, strength: Float) {
        val dark = kind == WeatherKind.STORM || !isDay
        val rgb = if (dark) 0x96A2B6 else 0xFFFFFF
        val layerAlpha = when (kind) {
            WeatherKind.PARTLY -> 0.75f
            WeatherKind.FOG -> 0.45f
            else -> 0.6f
        }
        clouds.forEachIndexed { i, c ->
            val far = i < 3
            val laps = if (far) 1 else 2
            val cloudWidth = width * (if (far) 0.75f else 0.6f) * (0.8f + 0.4f * c.size)
            val travel = width + cloudWidth * 1.2f
            val x = ((c.x + clock * laps) % 1f) * travel - cloudWidth * 0.6f
            val y = height * (if (far) 0.06f + 0.12f * c.y else 0.16f + 0.2f * c.y)
            val alpha = layerAlpha * strength * if (far) 0.55f else 0.9f
            cloudShapes[i].forEach { p ->
                val r = p.radius * cloudWidth
                puff(canvas, x + p.dx * cloudWidth, y + p.dy * cloudWidth, r, r, alpha, rgb)
            }
        }
    }

    /** Gotas en tres tramos de opacidad creciente: estela que se desvanece sin crear degradados. */
    private fun drawRain(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        clock: Float,
        list: List<Particle>,
        length: Float,
        alpha: Float,
        falls: Int,
        strokeWidth: Float,
    ) {
        linePaint.strokeWidth = strokeWidth * density
        list.forEach { p ->
            val f = falls + (p.speed * falls * 0.5f).toInt()
            val len = length * density * (0.7f + 0.6f * p.size)
            val y = ((p.y + clock * f) % 1f) * (height + len) - len
            val x = p.x * width
            val dx = -0.18f * len
            for (k in 0 until 3) {
                linePaint.color = Color.argb((255 * alpha * (k + 1) / 3f).toInt(), 210, 228, 255)
                canvas.drawLine(x + dx * k / 3f, y + len * k / 3f, x + dx * (k + 1) / 3f, y + len * (k + 1) / 3f, linePaint)
            }
        }
    }

    private fun drawSnow(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float) {
        flakes.forEach { f ->
            val near = f.size > 0.6f
            val falls = if (near) 4 else 2
            val y = ((f.y + clock * falls) % 1f) * (height + 20f) - 10f
            val sway = sin((clock * 6 + f.phase) * tau) * 12f * density
            val r = (if (near) 3.2f else 1.8f) * density * (0.8f + f.size * 0.6f)
            puff(canvas, f.x * width + sway, y, r, r, if (near) 0.95f else 0.6f, 0xFFFFFF)
        }
    }

    private fun drawFog(canvas: Canvas, width: Float, height: Float, clock: Float) {
        repeat(5) { i ->
            val y = height * (0.25f + i * 0.15f)
            val shift = sin((clock * (1 + i % 2) + i * 0.2f) * tau) * width * 0.12f
            puff(canvas, width / 2 + shift, y, width * 0.9f, height * 0.06f, 0.22f, 0xFFFFFF)
        }
    }

    /** Relámpago doble con caída exponencial, más natural que un destello parejo. */
    private fun drawLightning(canvas: Canvas, width: Float, height: Float, clock: Float) {
        val flash = listOf(0.3f, 0.315f, 0.72f).maxOf { start ->
            val t = clock - start
            if (t in 0f..0.02f) exp(-t * 250f) else 0f
        }
        val alpha = flash * 0.55f
        if (alpha > 0.01f) {
            paint.color = Color.argb((255 * alpha).toInt(), 225, 232, 255)
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }
}

/** Tres colores del cielo de arriba hacia abajo. */
data class SkyGradient(val top: Int, val middle: Int, val bottom: Int)
