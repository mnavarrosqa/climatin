package com.mnavarrosqa.clima.widget

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

    private val cache = LruCache<String, Bitmap>(24)

    private fun cached(key: String, create: () -> Bitmap): Bitmap =
        cache.get(key) ?: create().also { cache.put(key, it) }

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
