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
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.data.Hour
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.ui.theme.DarkColors
import com.mnavarrosqa.clima.ui.theme.LightColors

/** Los widgets no pueden dibujar: el gráfico se arma como imagen con los colores del sistema. */
object ChartBitmap {

    private data class Palette(val line: Int, val text: Int, val subtle: Int)

    private fun palette(context: Context, onTransparent: Boolean): Palette {
        if (onTransparent) return Palette(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xDDFFFFFF.toInt())
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Palette(
                line = context.getColor(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600),
                text = context.getColor(if (night) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900),
                subtle = context.getColor(if (night) android.R.color.system_neutral2_200 else android.R.color.system_neutral2_700),
            )
        } else {
            val scheme = if (night) DarkColors else LightColors
            Palette(scheme.primary.toArgb(), scheme.onSurface.toArgb(), scheme.onSurfaceVariant.toArgb())
        }
    }

    fun render(context: Context, hours: List<Hour>, widthPx: Int, heightPx: Int, onTransparent: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        if (hours.size < 2) return bitmap
        val canvas = Canvas(bitmap)
        val colors = palette(context, onTransparent)
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val textSize = 12f * density * context.resources.configuration.fontScale

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.text
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.subtle
            this.textSize = textSize * 0.92f
            textAlign = Paint.Align.CENTER
        }

        val top = textSize * 1.8f
        val bottom = heightPx - textSize * 1.9f
        val slot = widthPx.toFloat() / hours.size
        val min = hours.minOf { it.temperature }
        val max = hours.maxOf { it.temperature }
        val span = (max - min).coerceAtLeast(1.0)
        val xs = hours.indices.map { slot * it + slot / 2 }
        val ys = hours.map { (bottom - ((it.temperature - min) / span).toFloat() * (bottom - top)) }

        val line = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until hours.size) {
                val midX = (xs[i - 1] + xs[i]) / 2
                cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i])
            }
        }
        val fill = Path(line).apply {
            lineTo(xs.last(), bottom + textSize * 0.4f)
            lineTo(xs.first(), bottom + textSize * 0.4f)
            close()
        }
        canvas.drawPath(
            fill,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, top, 0f, bottom,
                    (colors.line and 0x00FFFFFF) or 0x66000000,
                    colors.line and 0x00FFFFFF,
                    Shader.TileMode.CLAMP,
                )
            },
        )
        canvas.drawPath(
            line,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.line
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
                strokeCap = Paint.Cap.ROUND
            },
        )

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.line }
        // Con muchas horas se rotula una sí y una no para que no se pisen.
        val step = if (slot < textSize * 2.6f) 2 else 1
        hours.forEachIndexed { i, hour ->
            canvas.drawCircle(xs[i], ys[i], 3.5f * density, dot)
            if (i % step == 0) {
                canvas.drawText(hour.temperature.degrees(), xs[i], ys[i] - textSize * 0.6f, labelPaint)
                val label = if (i == 0) "Ahora" else shortHour(hour.dateTime)
                canvas.drawText(label, xs[i], heightPx - textSize * 0.3f, hourPaint)
            }
        }
        return bitmap
    }

    /**
     * Arco del sol de amanecer a atardecer con la posición actual. [progress] va de 0 (amanecer)
     * a 1 (atardecer); fuera de ese rango es de noche y el sol no se dibuja.
     */
    fun renderSunArc(context: Context, progress: Float, widthPx: Int, heightPx: Int, onTransparent: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = palette(context, onTransparent)
        val density = context.resources.displayMetrics.density
        val margin = 10f * density
        val horizon = heightPx - 4f * density
        val oval = android.graphics.RectF(margin, margin, widthPx - margin, horizon * 2 - margin)

        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.subtle
            alpha = 120
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f * density, 6f * density), 0f)
        }
        canvas.drawArc(oval, 180f, 180f, false, arcPaint)
        canvas.drawLine(
            0f, horizon, widthPx.toFloat(), horizon,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.subtle
                alpha = 160
                strokeWidth = 1.5f * density
            },
        )
        if (progress in 0f..1f) {
            val done = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.line
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(oval, 180f, 180f * progress, false, done)
            val angle = Math.toRadians((180.0 + 180.0 * progress))
            val cx = oval.centerX() + (oval.width() / 2) * kotlin.math.cos(angle).toFloat()
            val cy = oval.centerY() + (oval.height() / 2) * kotlin.math.sin(angle).toFloat()
            canvas.drawCircle(cx, cy, 11f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFC107 })
            canvas.drawCircle(cx, cy, 7f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB300.toInt() })
        }
        return bitmap
    }
}
