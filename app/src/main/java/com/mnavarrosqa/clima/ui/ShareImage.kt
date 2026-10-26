package com.mnavarrosqa.clima.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.longDate
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * El pronóstico como imagen para compartir: el cielo del momento, el lugar, la temperatura grande,
 * el estado, la máxima y mínima y las próximas 6 horas. Se dibuja con el Canvas de Android (no con
 * Compose) para poder hacerlo fuera del hilo principal y sin que la imagen dependa de la pantalla.
 */
object ShareImage {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350

    /** Arma la imagen, la guarda en la caché y abre el selector de Android para compartirla. */
    suspend fun share(context: Context, snapshot: WeatherSnapshot) {
        val app = context.applicationContext
        val uri = withContext(Dispatchers.IO) {
            val bitmap = render(app, snapshot)
            try {
                save(app, bitmap)
            } finally {
                bitmap.recycle()
            }
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Con el ClipData el selector también puede leer la imagen y mostrarla de vista previa.
        send.clipData = ClipData.newRawUri("", uri)
        val chooser = Intent.createChooser(send, tr("Compartir el pronóstico", "Share the forecast"))
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Va a la carpeta "compartir/" de la caché, que el FileProvider de las actualizaciones también
     * expone (ver res/xml/update_paths.xml). Siempre el mismo archivo: no se acumulan imágenes.
     */
    private fun save(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "compartir").apply { mkdirs() }
        val file = File(dir, "clima.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    }

    fun render(context: Context, source: WeatherSnapshot): Bitmap {
        // Si el pronóstico tiene un rato, "ahora" sale de la hora actual, como en la pantalla.
        val snapshot = source.withCurrentFromHours()
        val current = snapshot.current
        val kind = WeatherCodes.kind(current.code)
        val isDay = current.isDay
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = WIDTH.toFloat()
        val h = HEIGHT.toFloat()

        // El mismo cielo de la app, quieto. La "densidad" se elige para que las nubes y las gotas
        // tengan el tamaño que tienen en un teléfono de unos 400 dp de ancho.
        SkyPainter().apply {
            southern = snapshot.latitude < 0
            // El ícono grande ya es el sol o la luna: sin esto se verían dos.
            showSunMoon = false
            // Se dibuja una sola vez: las imágenes de nubes no se llegarían a usar y sacarían de
            // la caché las de la app.
            useSprites = false
            // Con el tinte del amanecer y el atardecer, igual que la app y los widgets.
        }.draw(canvas, w, h, w / 400f, STILL_CLOCK, kind, isDay, SkyTime.gradient(kind, isDay, snapshot))
        // Un velo oscuro abajo para que las horas se lean sobre cielos claros.
        Paint().apply {
            shader = LinearGradient(0f, h * 0.45f, 0f, h, 0x00000000, 0x59000000, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, h * 0.45f, w, h, this)
        }

        val margin = 80f
        val shadow = 0x66000000
        fun text(size: Float, family: String, alpha: Int = 255) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(alpha, 255, 255, 255)
            textSize = size
            typeface = Typeface.create(family, Typeface.NORMAL)
            setShadowLayer(14f, 0f, 3f, shadow)
        }

        // Ícono del estado arriba a la derecha, donde en la app estaría el sol.
        val iconSize = 300
        drawIcon(context, canvas, WeatherCodes.icon(current.code, isDay), (w - margin - iconSize).toInt(), 70, iconSize)

        // Lugar y fecha, recortados para no meterse debajo del ícono.
        val titlePaint = text(64f, "sans-serif-medium")
        val place = snapshot.place ?: tr("Mi ubicación", "My location")
        val titleWidth = w - margin * 2 - iconSize * 0.8f
        val title = TextUtils.ellipsize(place, titlePaint, titleWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, margin, 170f, titlePaint)
        val datePaint = text(38f, "sans-serif", 230)
        canvas.drawText(longDate(snapshot.nowAtPlace().toLocalDate()), margin, 230f, datePaint)

        // Temperatura grande.
        val tempPaint = text(300f, "sans-serif-light")
        canvas.drawText(current.temperature.degrees(), margin - 12f, 540f, tempPaint)

        // Estado y máxima / mínima.
        val conditionPaint = text(56f, "sans-serif-medium")
        val condition = TextUtils.ellipsize(WeatherCodes.description(current.code), conditionPaint, w - margin * 2, TextUtils.TruncateAt.END).toString()
        canvas.drawText(condition, margin, 630f, conditionPaint)
        val today = snapshot.today()
        if (today != null) {
            val rangePaint = text(44f, "sans-serif", 235)
            canvas.drawText(
                tr("Máx. ${today.max.degrees()} · Mín. ${today.min.degrees()}", "H ${today.max.degrees()} · L ${today.min.degrees()}"),
                margin,
                700f,
                rangePaint,
            )
        }

        // Próximas horas en una franja translúcida, como las tarjetas de vidrio de la app.
        val hours = snapshot.upcomingHours(7).drop(1).take(6)
        if (hours.isNotEmpty()) {
            val panel = RectF(margin - 24f, 820f, w - margin + 24f, 1160f)
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(46, 255, 255, 255)
                canvas.drawRoundRect(panel, 48f, 48f, this)
            }
            val headerPaint = text(34f, "sans-serif-medium", 220)
            canvas.drawText(tr("Próximas horas", "Next hours"), panel.left + 40f, panel.top + 70f, headerPaint)
            val hourPaint = text(36f, "sans-serif", 230).apply { textAlign = Paint.Align.CENTER }
            val hourTempPaint = text(48f, "sans-serif-medium").apply { textAlign = Paint.Align.CENTER }
            val column = (panel.width() - 40f) / hours.size
            hours.forEachIndexed { index, hour ->
                val cx = panel.left + 20f + column * (index + 0.5f)
                canvas.drawText(shortHour(hour.dateTime), cx, panel.top + 140f, hourPaint)
                val size = 88
                drawIcon(context, canvas, WeatherCodes.icon(hour.code, hour.isDay), (cx - size / 2f).toInt(), (panel.top + 160f).toInt(), size)
                canvas.drawText(hour.temperature.degrees(), cx, panel.top + 310f, hourTempPaint)
            }
        }

        // Firma.
        val brandPaint = text(44f, "sans-serif-medium")
        canvas.drawText("Climatin", margin, h - 80f, brandPaint)
        val sourcePaint = text(30f, "sans-serif", 200).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(tr("Datos: Open-Meteo", "Data: Open-Meteo"), w - margin, h - 82f, sourcePaint)
        return bitmap
    }

    /** Íconos del clima (vectores) dibujados a [size] px en ([left], [top]). */
    private fun drawIcon(context: Context, canvas: Canvas, res: Int, left: Int, top: Int, size: Int) {
        val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        drawable.setBounds(left, top, left + size, top + size)
        drawable.draw(canvas)
    }
}
