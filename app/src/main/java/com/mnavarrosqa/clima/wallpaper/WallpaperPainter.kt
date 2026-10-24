package com.mnavarrosqa.clima.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.ui.SkyGradient
import com.mnavarrosqa.clima.ui.SkyPainter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Lo que muestra el fondo: el clima y, si se eligió, la temperatura y el lugar. */
data class WallpaperScene(
    val kind: WeatherKind,
    val isDay: Boolean,
    val colors: SkyGradient,
    val temperature: String? = null,
    val caption: String? = null,
)

/** Un toque en el fondo: una onda que se abre y se apaga. */
class Ripple(val x: Float, val y: Float, val startMs: Long)

const val RIPPLE_MS = 1_400L

/**
 * Dibuja los cuatro diseños del fondo de pantalla. Lo usan el servicio y la vista previa de
 * ajustes, así lo que se ve al elegir es exactamente lo que queda puesto.
 *
 * [parallax] va de 0 a 1 según la pantalla de inicio en la que estás (o el dedo en la vista previa).
 */
class WallpaperPainter {
    private val sky = SkyPainter()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val tau = 2 * PI.toFloat()
    private val blobShaders = HashMap<Int, Shader>()
    private val lights = Random(7).let { r -> List(18) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }

    private val temperaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        clock: Float,
        scene: WallpaperScene,
        style: WallpaperStyle,
        parallax: Float,
        ripples: List<Ripple> = emptyList(),
        nowMs: Long = 0L,
    ) {
        when (style) {
            WallpaperStyle.SKY -> drawSky(canvas, width, height, density, clock, scene, parallax)
            WallpaperStyle.LANDSCAPE -> drawLandscape(canvas, width, height, density, clock, scene, parallax)
            WallpaperStyle.SHAPES -> drawShapes(canvas, width, height, density, clock, scene, parallax)
            WallpaperStyle.DARK -> drawDark(canvas, width, height, density, clock, scene, parallax)
        }
        ripples.forEach { drawRipple(canvas, density, it, nowMs, scene.kind) }
        drawWeather(canvas, width, height, density, scene)
    }

    /** El cielo un poco más ancho que la pantalla, para correrlo al pasar de página. */
    private fun drawSky(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float, scene: WallpaperScene, parallax: Float) {
        val extra = width * 0.15f
        canvas.save()
        canvas.translate(-extra * parallax, 0f)
        sky.draw(canvas, width + extra, height, density, clock, scene.kind, scene.isDay, scene.colors)
        canvas.restore()
    }

    /** Cielo con tres capas de sierras: las cercanas se corren más al pasar de página. */
    private fun drawLandscape(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float, scene: WallpaperScene, parallax: Float) {
        drawSky(canvas, width, height, density, clock, scene, parallax)
        val horizon = scene.colors.bottom
        val shade = if (scene.isDay) 0xFF1E2A3A.toInt() else 0xFF05070C.toInt()
        val snowy = scene.kind == WeatherKind.SNOW
        for (layer in 0 until 3) {
            val base = height * (0.70f + 0.085f * layer)
            val amplitude = height * (0.05f - 0.01f * layer)
            val shift = width * (0.06f + 0.09f * layer) * parallax
            val frequency = 1.4f + 0.7f * layer
            val phase = layer * 1.9f
            path.reset()
            path.moveTo(-1f, height)
            var x = -1f
            val step = 6f * density
            while (x <= width + step) {
                val u = (x + shift) / width
                val y = base - amplitude * (0.6f * sin((u * frequency + phase) * tau) + 0.4f * sin((u * frequency * 2.3f + phase * 1.7f) * tau))
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(width + step, height)
            path.close()
            // Las de atrás se pierden en el horizonte; las de adelante son casi silueta.
            var color = ColorUtils.blendARGB(horizon, shade, 0.35f + 0.25f * layer)
            if (snowy && layer == 0) color = ColorUtils.blendARGB(color, Color.WHITE, 0.55f)
            paint.shader = LinearGradient(
                0f, base - amplitude, 0f, height,
                color, ColorUtils.blendARGB(color, shade, 0.5f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(path, paint)
            paint.shader = null

            // De noche, unas luces de casas en la sierra de adelante.
            if (layer == 2 && !scene.isDay) {
                lights.forEach { (lx, ly, twinkle) ->
                    val px = lx * width
                    val u = (px + shift) / width
                    val top = base - amplitude * (0.6f * sin((u * frequency + phase) * tau) + 0.4f * sin((u * frequency * 2.3f + phase * 1.7f) * tau))
                    val py = top + (height - top) * (0.15f + 0.6f * ly)
                    val glow = 0.6f + 0.4f * sin((clock * 3 + twinkle) * tau)
                    paint.color = Color.argb((200 * glow).toInt(), 255, 205, 130)
                    canvas.drawCircle(px, py, 1.6f * density, paint)
                }
            }
        }
    }

    /** Manchas de color que orbitan despacio y un sol o luna con forma de galleta (Material 3). */
    private fun drawShapes(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float, scene: WallpaperScene, parallax: Float) {
        val colors = scene.colors
        canvas.drawColor(ColorUtils.blendARGB(colors.top, Color.BLACK, 0.25f))
        val accent = accentFor(scene)
        val blobs = listOf(colors.middle, colors.bottom, accent, colors.top)
        val base = min(width, height)
        blobs.forEachIndexed { i, color ->
            val angle = (clock * (1 + i % 2) + i * 0.25f) * tau
            val cx = width * (0.5f + 0.32f * cos(angle)) - width * 0.1f * parallax
            val cy = height * (0.25f + 0.17f * i + 0.08f * sin(angle))
            blob(canvas, cx, cy, base * (0.75f + 0.1f * i), color, 0.75f)
        }
        if (scene.kind == WeatherKind.CLEAR || scene.kind == WeatherKind.PARTLY) {
            val cx = width * 0.72f - width * 0.06f * parallax
            val cy = height * 0.2f
            cookie(canvas, cx, cy, base * 0.2f, clock * 360f / 4f, if (scene.isDay) 0xFFFFD36E.toInt() else 0xFFE8ECFF.toInt())
        }
        sky.draw(
            canvas, width, height, density, clock, scene.kind, scene.isDay, colors,
            background = false,
            celestial = false,
            cloudAlpha = 0.35f,
        )
    }

    /** Negro (pantallas OLED apagan esos píxeles), con un resplandor del color del clima abajo. */
    private fun drawDark(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float, scene: WallpaperScene, parallax: Float) {
        canvas.drawColor(Color.BLACK)
        val breathe = 1f + 0.04f * sin(clock * tau * 2)
        blob(canvas, width * (0.5f - 0.1f * parallax), height * 1.05f, width * 0.95f * breathe, scene.colors.middle, 0.55f)
        canvas.save()
        canvas.translate(-width * 0.08f * parallax, 0f)
        sky.draw(
            canvas, width * 1.08f, height, density, clock, scene.kind, scene.isDay, scene.colors,
            background = false,
            celestial = true,
            cloudAlpha = 0.3f,
        )
        canvas.restore()
    }

    private fun accentFor(scene: WallpaperScene): Int = when (scene.kind) {
        WeatherKind.CLEAR, WeatherKind.PARTLY -> if (scene.isDay) 0xFFFFB86B.toInt() else 0xFF6C5BD4.toInt()
        WeatherKind.RAIN, WeatherKind.DRIZZLE, WeatherKind.STORM -> 0xFF5E8BC9.toInt()
        WeatherKind.SNOW -> 0xFFD8E6FF.toInt()
        else -> 0xFF9AA8BC.toInt()
    }

    /** Círculo de color que se desvanece hacia afuera. */
    private fun blob(canvas: Canvas, x: Float, y: Float, radius: Float, rgb: Int, alpha: Float) {
        paint.shader = blobShaders.getOrPut(rgb) {
            RadialGradient(
                0f, 0f, 1f,
                intArrayOf(rgb or 0xFF000000.toInt(), (rgb and 0x00FFFFFF) or 0x66000000, rgb and 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        paint.alpha = (alpha * 255).toInt()
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(radius, radius)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
        paint.shader = null
        paint.alpha = 255
    }

    /** Círculo festoneado de nueve ondas, la forma "cookie" de Material 3 Expressive. */
    private fun cookie(canvas: Canvas, cx: Float, cy: Float, radius: Float, rotation: Float, color: Int) {
        path.reset()
        val points = 180
        for (i in 0..points) {
            val a = i / points.toFloat() * tau
            val r = radius * (1f + 0.07f * cos(9 * a))
            val x = cx + r * cos(a + rotation / 180f * PI.toFloat())
            val y = cy + r * sin(a + rotation / 180f * PI.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        blob(canvas, cx, cy, radius * 2.2f, color, 0.35f)
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun drawRipple(canvas: Canvas, density: Float, ripple: Ripple, nowMs: Long, kind: WeatherKind) {
        val t = (nowMs - ripple.startMs) / RIPPLE_MS.toFloat()
        if (t !in 0f..1f) return
        val fade = 1f - t
        val radius = (20f + 150f * t) * density
        blob(canvas, ripple.x, ripple.y, radius, 0xFFFFFF, 0.22f * fade)
        // Con lluvia, ondas como en un charco; si no, un solo anillo.
        val rings = if (kind == WeatherKind.RAIN || kind == WeatherKind.DRIZZLE || kind == WeatherKind.STORM) 3 else 1
        strokePaint.strokeWidth = 2f * density
        repeat(rings) { k ->
            val r = radius * (1f - 0.22f * k)
            if (r <= 0f) return@repeat
            strokePaint.color = Color.argb((150 * fade * (1f - 0.3f * k)).toInt(), 255, 255, 255)
            canvas.drawCircle(ripple.x, ripple.y, r, strokePaint)
        }
    }

    /** Temperatura y lugar abajo a la izquierda, con sombra para leerse sobre cualquier cielo. */
    private fun drawWeather(canvas: Canvas, width: Float, height: Float, density: Float, scene: WallpaperScene) {
        val temperature = scene.temperature ?: return
        val left = 28f * density
        val baseline = height * 0.74f
        temperaturePaint.textSize = 72f * density
        temperaturePaint.setShadowLayer(12f * density, 0f, 2f * density, 0x55000000)
        canvas.drawText(temperature, left, baseline, temperaturePaint)
        scene.caption?.let {
            captionPaint.textSize = 16f * density
            captionPaint.setShadowLayer(8f * density, 0f, 1f * density, 0x66000000)
            canvas.drawText(it, left + 4f * density, baseline + 28f * density, captionPaint)
        }
    }
}
