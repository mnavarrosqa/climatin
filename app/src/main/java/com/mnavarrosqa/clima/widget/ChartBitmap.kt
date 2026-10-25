package com.mnavarrosqa.clima.widget

import com.mnavarrosqa.clima.data.tr
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
import com.mnavarrosqa.clima.data.Units
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.data.Hour
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.ui.theme.DarkColors
import com.mnavarrosqa.clima.ui.theme.LightColors

/** Los widgets no pueden dibujar: el gráfico se arma como imagen con los colores del sistema. */
object ChartBitmap {

    private data class Palette(val line: Int, val text: Int, val subtle: Int)

    /** [accent] es el color fijo elegido al personalizar el widget (null: el del sistema). */
    private fun palette(context: Context, onTransparent: Boolean, accent: Int? = null): Palette {
        if (onTransparent) return Palette(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xDDFFFFFF.toInt())
        if (accent != null) return palette(context, false, null).copy(line = accent)
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

    /**
     * Las imágenes ya dibujadas, por todo lo que cambia el dibujo (datos, tamaño, colores, tema,
     * unidades e idioma). Cada actualización repinta los widgets dos veces y casi siempre con lo
     * mismo: así no se arma de nuevo una imagen de hasta 1,5 MB.
     */
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private fun cached(context: Context, key: String, draw: () -> Bitmap): Bitmap {
        val config = context.resources.configuration
        val full = listOf(
            key,
            config.uiMode and Configuration.UI_MODE_NIGHT_MASK,
            config.fontScale,
            Units.current.hashCode(),
            java.util.Locale.getDefault().language,
        ).joinToString("|")
        cache.get(full)?.let { return it }
        return draw().also { cache.put(full, it) }
    }

    fun render(context: Context, hours: List<Hour>, widthPx: Int, heightPx: Int, onTransparent: Boolean = false, accent: Int? = null): Bitmap =
        cached(context, "horas|${hours.joinToString(",")}|$widthPx|$heightPx|$onTransparent|$accent") {
            drawHours(context, hours, widthPx, heightPx, onTransparent, accent)
        }

    private fun drawHours(context: Context, hours: List<Hour>, widthPx: Int, heightPx: Int, onTransparent: Boolean, accent: Int?): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        if (hours.size < 2) return bitmap
        val canvas = Canvas(bitmap)
        val colors = palette(context, onTransparent, accent)
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

        // Barras de probabilidad de lluvia sobre la línea de las horas.
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (colors.line and 0x00FFFFFF) or 0x40000000 }
        val barBase = bottom + textSize * 0.4f
        val barMax = (bottom - top) * 0.45f
        val barWidth = (slot * 0.42f).coerceAtMost(14f * density)
        val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.subtle
            this.textSize = textSize * 0.8f
            textAlign = Paint.Align.CENTER
        }
        hours.forEachIndexed { i, hour ->
            val probability = (hour.precipitationProbability ?: 0).coerceIn(0, 100)
            if (probability < 10) return@forEachIndexed
            val h = barMax * probability / 100f
            canvas.drawRoundRect(
                xs[i] - barWidth / 2, barBase - h, xs[i] + barWidth / 2, barBase,
                barWidth / 2, barWidth / 2, barPaint,
            )
            if (probability >= 30 && h > rainPaint.textSize * 1.4f) {
                canvas.drawText("$probability%", xs[i], barBase - h - rainPaint.textSize * 0.3f, rainPaint)
            }
        }

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.line }
        // Con muchas horas se rotula una sí y una no para que no se pisen.
        val step = if (slot < textSize * 2.6f) 2 else 1
        hours.forEachIndexed { i, hour ->
            canvas.drawCircle(xs[i], ys[i], 3.5f * density, dot)
            if (i % step == 0) {
                canvas.drawText(hour.temperature.degrees(), xs[i], ys[i] - textSize * 0.6f, labelPaint)
                val label = if (i == 0) tr("Ahora", "Now") else shortHour(hour.dateTime)
                canvas.drawText(label, xs[i], heightPx - textSize * 0.3f, hourPaint)
            }
        }
        return bitmap
    }

    /**
     * Arco del sol de amanecer a atardecer con la posición actual. [progress] va de 0 (amanecer)
     * a 1 (atardecer); fuera de ese rango es de noche y el sol no se dibuja.
     */
    fun renderSunArc(context: Context, progress: Float, widthPx: Int, heightPx: Int, onTransparent: Boolean = false, accent: Int? = null): Bitmap =
        // El sol avanza de a poco: con dos decimales alcanza y la imagen se reusa varios minutos.
        cached(context, "sol|${"%.2f".format(java.util.Locale.ROOT, progress)}|$widthPx|$heightPx|$onTransparent|$accent") {
            drawSunArc(context, progress, widthPx, heightPx, onTransparent, accent)
        }

    private fun drawSunArc(context: Context, progress: Float, widthPx: Int, heightPx: Int, onTransparent: Boolean, accent: Int?): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = palette(context, onTransparent, accent)
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

    /** Barras de lluvia cada 15 minutos (o por hora) para el widget "Próxima hora". */
    fun renderRainBars(
        context: Context,
        values: List<Pair<String, Float>>,
        widthPx: Int,
        heightPx: Int,
        onTransparent: Boolean,
        accent: Int? = null,
    ): Bitmap = cached(context, "lluvia|${values.joinToString(",") { it.first + "=" + it.second }}|$widthPx|$heightPx|$onTransparent|$accent") {
        drawRainBars(context, values, widthPx, heightPx, onTransparent, accent)
    }

    private fun drawRainBars(
        context: Context,
        values: List<Pair<String, Float>>,
        widthPx: Int,
        heightPx: Int,
        onTransparent: Boolean,
        accent: Int?,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        if (values.isEmpty()) return bitmap
        val canvas = Canvas(bitmap)
        val colors = palette(context, onTransparent, accent)
        val density = context.resources.displayMetrics.density
        val textSize = 11f * density * context.resources.configuration.fontScale
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.subtle
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
        }
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (colors.subtle and 0x00FFFFFF) or 0x26000000 }
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.line }
        val bottom = heightPx - textSize * 1.6f
        val slot = widthPx.toFloat() / values.size
        val width = slot * 0.55f
        values.forEachIndexed { i, (label, fraction) ->
            val cx = slot * i + slot / 2
            canvas.drawRoundRect(cx - width / 2, 0f, cx + width / 2, bottom, width / 2, width / 2, track)
            val h = bottom * fraction.coerceIn(0f, 1f)
            if (h > 1f) canvas.drawRoundRect(cx - width / 2, bottom - h, cx + width / 2, bottom, width / 2, width / 2, bar)
            // Se rotula una de cada dos para que no se pisen.
            if (i % 2 == 0) canvas.drawText(label, cx, heightPx - textSize * 0.3f, labelPaint)
        }
        return bitmap
    }

    /** Medidor circular (arco de 270°) para el widget Indicador. */
    fun renderGauge(context: Context, fraction: Float, accent: Int, sizePx: Int, onTransparent: Boolean): Bitmap =
        cached(context, "medidor|${(fraction * 200).toInt()}|$accent|$sizePx|$onTransparent") { drawGauge(context, fraction, accent, sizePx, onTransparent) }

    private fun drawGauge(context: Context, fraction: Float, accent: Int, sizePx: Int, onTransparent: Boolean): Bitmap {
        val size = sizePx.coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = palette(context, onTransparent)
        val density = context.resources.displayMetrics.density
        val stroke = (size * 0.09f).coerceAtLeast(4f * density)
        val oval = android.graphics.RectF(stroke, stroke, size - stroke, size - stroke)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = (colors.subtle and 0x00FFFFFF) or 0x33000000
        canvas.drawArc(oval, 135f, 270f, false, paint)
        paint.color = accent
        canvas.drawArc(oval, 135f, 270f * fraction.coerceIn(0.02f, 1f), false, paint)
        return bitmap
    }

    /** Brújula con una flecha que apunta hacia donde va el viento. */
    fun renderCompass(context: Context, fromDegrees: Float, accent: Int, sizePx: Int, onTransparent: Boolean): Bitmap =
        cached(context, "brujula|${fromDegrees.toInt()}|$accent|$sizePx|$onTransparent") { drawCompass(context, fromDegrees, accent, sizePx, onTransparent) }

    private fun drawCompass(context: Context, fromDegrees: Float, accent: Int, sizePx: Int, onTransparent: Boolean): Bitmap {
        val size = sizePx.coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = palette(context, onTransparent)
        val density = context.resources.displayMetrics.density
        val c = size / 2f
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (size * 0.05f).coerceAtLeast(2f * density)
            color = (colors.subtle and 0x00FFFFFF) or 0x40000000
        }
        canvas.drawCircle(c, c, c - ring.strokeWidth, ring)
        // El viento "viene de" fromDegrees: la flecha apunta al lado opuesto.
        canvas.save()
        canvas.rotate(fromDegrees + 180f, c, c)
        val arrow = Path().apply {
            moveTo(c, size * 0.12f)
            lineTo(c + size * 0.13f, c + size * 0.12f)
            lineTo(c, c + size * 0.02f)
            lineTo(c - size * 0.13f, c + size * 0.12f)
            close()
        }
        canvas.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        canvas.restore()
        return bitmap
    }
}
