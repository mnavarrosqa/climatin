package com.mnavarrosqa.clima.widget

import kotlin.math.roundToInt
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.ui.skyColors
import com.mnavarrosqa.clima.ui.theme.DarkColors
import com.mnavarrosqa.clima.ui.theme.LightColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Imágenes para los widgets (Glance no puede dibujar degradados ni formas). Se guardan en caché
 * porque los widgets se recomponen seguido y las imágenes no cambian entre una vez y otra.
 */
object WidgetBitmaps {

    // Por bytes y no por cantidad: una galletita grande pesa más de 1 MB.
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun cached(key: String, create: () -> Bitmap): Bitmap =
        cache.get(key) ?: create().also { cache.put(key, it) }

    /**
     * El cielo del clima de verdad para el fondo del widget, como los del Clima de Apple: nubes,
     * estrellas, resplandor del sol y los colores del amanecer y el atardecer. Se dibuja quieto,
     * a 360 px de ancho (se estira bien: el cielo es difuso) y con la proporción del widget, con
     * un velo oscuro arriba para que el texto blanco se lea.
     */
    fun skyScene(snapshot: com.mnavarrosqa.clima.data.WeatherSnapshot?, kind: WeatherKind, isDay: Boolean, widthDp: Float, heightDp: Float): Bitmap {
        val aspect = ((heightDp / widthDp.coerceAtLeast(1f)).coerceIn(0.2f, 2.5f) * 8).roundToInt() / 8f
        val colors = com.mnavarrosqa.clima.ui.SkyTime.gradient(kind, isDay, snapshot)
        val southern = (snapshot?.latitude ?: 0.0) < 0
        return cached("escena|$kind|$isDay|$aspect|${colors.hashCode()}|$southern") {
            val width = 360
            val height = (width * aspect).roundToInt().coerceAtLeast(60)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            com.mnavarrosqa.clima.ui.SkyPainter().apply {
                this.southern = southern
                // El ícono del widget ya es el sol o la luna: queda el resplandor.
                showSunMoon = false
                useSprites = false
            }.draw(
                canvas,
                width.toFloat(),
                height.toFloat(),
                width / widthDp.coerceAtLeast(1f),
                com.mnavarrosqa.clima.ui.STILL_CLOCK,
                kind,
                isDay,
                colors,
            )
            Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, height.toFloat(), 0x40000000, 0x14000000, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), this)
            }
            bitmap
        }
    }

    /**
     * Barra de mínima a máxima de un día dentro del rango de la semana, con el color de la
     * temperatura (como la del pronóstico de la app). [now], si va, es un punto en la temperatura actual.
     */
    fun rangeBar(widthPx: Int, heightPx: Int, weekMin: Double, weekMax: Double, min: Double, max: Double, now: Double?): Bitmap =
        cached("rango|$widthPx|$heightPx|$weekMin|$weekMax|$min|$max|$now") {
            val w = widthPx.coerceAtLeast(8)
            val h = heightPx.coerceAtLeast(4)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val r = h / 2f
            paint.color = 0x33FFFFFF
            canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, paint)
            val span = (weekMax - weekMin).coerceAtLeast(1.0)
            val x0 = (((min - weekMin) / span) * w).toFloat().coerceIn(0f, w - h.toFloat())
            val x1 = (((max - weekMin) / span) * w).toFloat().coerceIn(x0 + h, w.toFloat())
            paint.shader = LinearGradient(
                x0, 0f, x1, 0f,
                com.mnavarrosqa.clima.ui.TempColors.of(min).toArgb(),
                com.mnavarrosqa.clima.ui.TempColors.of(max).toArgb(),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(x0, 0f, x1, h.toFloat(), r, r, paint)
            paint.shader = null
            now?.let {
                val x = (((it - weekMin) / span) * w).toFloat().coerceIn(r, w - r)
                paint.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(x, r, r * 1.1f, paint)
                paint.color = 0xFF1B1D21.toInt()
                canvas.drawCircle(x, r, r * 0.6f, paint)
            }
            bitmap
        }

    /** Degradado vertical del cielo; chico, porque se estira sin perder calidad. */
    fun sky(kind: WeatherKind, isDay: Boolean): Bitmap = cached("cielo-$kind-$isDay") {
        val colors = skyColors(kind, isDay).map { it.toArgb() }.toIntArray()
        Bitmap.createBitmap(8, 128, Bitmap.Config.ARGB_8888).also { bitmap ->
            Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, 128f, colors, floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                Canvas(bitmap).drawRect(0f, 0f, 8f, 128f, this)
            }
        }
    }

    /**
     * Forma "galletita" de Material 3 Expressive: un círculo con borde ondulado de 9 lóbulos.
     * [colors] es un color liso o un degradado vertical.
     */
    fun cookie(sizePx: Int, colors: IntArray): Bitmap = cached("galletita-$sizePx-${colors.joinToString()}") {
        val size = sizePx.coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val center = size / 2f
        val radius = center * 0.96f
        val path = Path()
        val steps = 180
        for (i in 0..steps) {
            val angle = i / steps.toFloat() * 2 * PI.toFloat()
            val r = radius * (0.92f + 0.08f * cos(9 * angle))
            val x = center + r * cos(angle)
            val y = center + r * sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (colors.size == 1) {
            paint.color = colors[0]
        } else {
            paint.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), colors, null, Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawPath(path, paint)
        bitmap
    }

    private fun isNight(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Color de relleno de un estilo, para dibujarlo en una imagen (Sistema o Color). */
    fun fill(context: Context, style: WidgetStyle): Int {
        val night = isNight(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val id = when (style) {
                WidgetStyle.COLOR -> if (night) android.R.color.system_accent1_700 else android.R.color.system_accent1_100
                else -> if (night) android.R.color.system_neutral1_800 else android.R.color.system_accent2_50
            }
            return context.getColor(id)
        }
        val scheme = if (night) DarkColors else LightColors
        return (if (style == WidgetStyle.COLOR) scheme.primaryContainer else scheme.surfaceContainer).toArgb()
    }
}
