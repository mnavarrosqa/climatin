package com.mnavarrosqa.clima.ui

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mnavarrosqa.clima.data.Insights
import com.mnavarrosqa.clima.data.PlacePhoto
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.longDate
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Formato de la imagen: publicación (4:5, la de los feeds) o historia (9:16, con los próximos días). */
enum class ShareFormat(val width: Int, val height: Int, private val es: String, private val en: String) {
    POST(1080, 1350, "Publicación", "Post"),
    STORY(1080, 1920, "Historia", "Story"),
    ;

    val label: String get() = tr(es, en)
}

/** Qué lleva la imagen. [photo] null: el cielo de la app. */
data class ShareOptions(val format: ShareFormat = ShareFormat.POST, val photo: PlacePhoto? = null)

/**
 * El pronóstico como imagen para compartir: el cielo del momento (o la foto de la capital), el
 * lugar, la temperatura grande, el estado, la máxima y mínima, la frase del día, las próximas 6
 * horas y, en la historia, los próximos 4 días. Se dibuja con el Canvas de Android (no con Compose)
 * para poder hacerlo fuera del hilo principal y sin que la imagen dependa de la pantalla.
 */
object ShareImage {

    /** Arma la imagen, la guarda en la caché y abre el selector de Android para compartirla. */
    suspend fun share(context: Context, snapshot: WeatherSnapshot, options: ShareOptions = ShareOptions()) {
        val app = context.applicationContext
        val uri = withContext(Dispatchers.IO) {
            val bitmap = render(app, snapshot, options)
            try {
                saveToCache(app, bitmap, options.photo != null)
            } finally {
                bitmap.recycle()
            }
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType(if (options.photo != null) "image/jpeg" else "image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            // Las apps de mensajes lo usan de epígrafe: el resumen y dónde bajar la app.
            .putExtra(Intent.EXTRA_TEXT, summary(context, snapshot))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Con el ClipData el selector también puede leer la imagen y mostrarla de vista previa.
        send.clipData = ClipData.newRawUri("", uri)
        start(context, Intent.createChooser(send, tr("Compartir el pronóstico", "Share the forecast")))
    }

    /** Solo el texto, para quien prefiere mandar un mensaje sin imagen. */
    fun shareText(context: Context, snapshot: WeatherSnapshot) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, summary(context, snapshot))
        start(context, Intent.createChooser(send, tr("Compartir el pronóstico", "Share the forecast")))
    }

    /** Guardar en la galería solo existe sin permisos desde Android 10. */
    val canSave: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Guarda la imagen en Imágenes/Climatin. false si no se pudo. */
    suspend fun saveToGallery(context: Context, snapshot: WeatherSnapshot, options: ShareOptions): Boolean = withContext(Dispatchers.IO) {
        if (!canSave) return@withContext false
        val app = context.applicationContext
        val jpeg = options.photo != null
        val bitmap = render(app, snapshot, options)
        try {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                val place = (snapshot.place ?: "clima").replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')
                put(MediaStore.Images.Media.DISPLAY_NAME, "Climatin-$place-${System.currentTimeMillis() / 1000}.${if (jpeg) "jpg" else "png"}")
                put(MediaStore.Images.Media.MIME_TYPE, if (jpeg) "image/jpeg" else "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Climatin")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
            try {
                resolver.openOutputStream(uri)?.use { compress(bitmap, jpeg, it) } ?: error("sin salida")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                // Que no quede un archivo vacío en la galería.
                runCatching { resolver.delete(uri, null, null) }
                false
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * El pronóstico en texto: "Córdoba ahora: 27°, nublado. Máx. 27° · Mín. 20°. Lluvia en unas 2
     * horas." y el link a la app (Play o GitHub, según de dónde se instaló).
     */
    fun summary(context: Context, source: WeatherSnapshot): String {
        val snapshot = source.withCurrentFromHours()
        val current = snapshot.current
        val place = snapshot.place ?: tr("Acá", "Here")
        val condition = WeatherCodes.description(current.code).lowercase(com.mnavarrosqa.clima.data.Lang.locale)
        val parts = mutableListOf(tr("$place ahora: ${current.temperature.degrees()}, $condition.", "$place now: ${current.temperature.degrees()}, $condition."))
        snapshot.today()?.let {
            parts += tr("Máx. ${it.max.degrees()} · Mín. ${it.min.degrees()}.", "High ${it.max.degrees()} · Low ${it.min.degrees()}.")
        }
        Insights.headline(snapshot)?.let { parts += it.trimEnd('.') + "." }
        val link = if (AppShare.isPlayBuild(context)) AppShare.PLAY_URL else AppShare.RELEASES_URL
        return parts.joinToString(" ") + "\n" + tr("Visto en Climatin: $link", "Seen on Climatin: $link")
    }

    /**
     * Va a la carpeta "compartir/" de la caché, que el FileProvider de las actualizaciones también
     * expone (ver res/xml/update_paths.xml). Siempre el mismo archivo: no se acumulan imágenes.
     */
    private fun saveToCache(context: Context, bitmap: Bitmap, jpeg: Boolean): Uri {
        val dir = File(context.cacheDir, "compartir").apply { mkdirs() }
        dir.listFiles { file -> file.name.startsWith("clima.") }?.forEach { it.delete() }
        val file = File(dir, if (jpeg) "clima.jpg" else "clima.png")
        FileOutputStream(file).use { compress(bitmap, jpeg, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    }

    /** Con foto, JPEG: en PNG pesaba varios MB y algunas apps la rechazaban o la recomprimían mal. */
    private fun compress(bitmap: Bitmap, jpeg: Boolean, out: java.io.OutputStream) {
        if (jpeg) bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) else bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }

    private fun start(context: Context, chooser: Intent) {
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun render(context: Context, source: WeatherSnapshot, options: ShareOptions = ShareOptions()): Bitmap {
        // Si el pronóstico tiene un rato, "ahora" sale de la hora actual, como en la pantalla.
        val snapshot = source.withCurrentFromHours()
        val current = snapshot.current
        val kind = WeatherCodes.kind(current.code)
        val isDay = current.isDay
        val format = options.format
        val bitmap = Bitmap.createBitmap(format.width, format.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = format.width.toFloat()
        val h = format.height.toFloat()

        val photo = options.photo?.let { decodePhoto(it, format.width, format.height) }
        if (photo != null) {
            // La foto recortada al centro y oscurecida: arriba (lugar y temperatura) y abajo
            // (horas y días) más, como en la app.
            val scale = max(w / photo.width, h / photo.height)
            val sw = (w / scale).toInt()
            val sh = (h / scale).toInt()
            val left = (photo.width - sw) / 2
            val top = (photo.height - sh) / 2
            canvas.drawBitmap(photo, Rect(left, top, left + sw, top + sh), RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            photo.recycle()
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, h,
                    intArrayOf(0x8C000000.toInt(), 0x52000000, 0x8C000000.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, this)
            }
        } else {
            // El mismo cielo de la app, quieto. La "densidad" se elige para que las nubes y las
            // gotas tengan el tamaño que tienen en un teléfono de unos 400 dp de ancho.
            SkyPainter().apply {
                southern = snapshot.latitude < 0
                // El ícono grande ya es el sol o la luna: sin esto se verían dos.
                showSunMoon = false
                // Se dibuja una sola vez: las imágenes de nubes no se llegarían a usar y sacarían
                // de la caché las de la app.
                useSprites = false
                // Con el tinte del amanecer y el atardecer, igual que la app y los widgets.
            }.draw(canvas, w, h, w / 400f, STILL_CLOCK, kind, isDay, SkyTime.gradient(kind, isDay, snapshot))
            // Un velo oscuro abajo para que las horas se lean sobre cielos claros.
            Paint().apply {
                shader = LinearGradient(0f, h * 0.45f, 0f, h, 0x00000000, 0x59000000, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, h * 0.45f, w, h, this)
            }
        }

        val margin = 80f
        val shadow = 0x66000000
        fun text(size: Float, family: String, alpha: Int = 255) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(alpha, 255, 255, 255)
            textSize = size
            typeface = Typeface.create(family, Typeface.NORMAL)
            setShadowLayer(14f, 0f, 3f, shadow)
        }
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // Sobre la foto el vidrio es oscuro, como las tarjetas de la app; sobre el cielo, claro.
            color = if (photo != null) android.graphics.Color.argb(120, 11, 18, 32) else android.graphics.Color.argb(46, 255, 255, 255)
        }

        // En la historia, todo un poco más abajo: Instagram y WhatsApp tapan la parte de arriba.
        val y0 = if (format == ShareFormat.STORY) 150f else 0f

        // Ícono del estado arriba a la derecha, donde en la app estaría el sol.
        val iconSize = 300
        drawIcon(context, canvas, WeatherCodes.icon(current.code, isDay), (w - margin - iconSize).toInt(), (70 + y0).toInt(), iconSize)

        // Lugar y fecha, recortados para no meterse debajo del ícono.
        val titlePaint = text(64f, "sans-serif-medium")
        val place = snapshot.place ?: tr("Mi ubicación", "My location")
        val titleWidth = w - margin * 2 - iconSize * 0.8f
        canvas.drawText(TextUtils.ellipsize(place, titlePaint, titleWidth, TextUtils.TruncateAt.END).toString(), margin, 170f + y0, titlePaint)
        canvas.drawText(longDate(snapshot.nowAtPlace().toLocalDate()), margin, 230f + y0, text(38f, "sans-serif", 230))

        // Temperatura grande.
        canvas.drawText(current.temperature.degrees(), margin - 12f, 540f + y0, text(300f, "sans-serif-light"))

        // Estado y máxima / mínima.
        val conditionPaint = text(56f, "sans-serif-medium")
        val condition = TextUtils.ellipsize(WeatherCodes.description(current.code), conditionPaint, w - margin * 2, TextUtils.TruncateAt.END).toString()
        canvas.drawText(condition, margin, 630f + y0, conditionPaint)
        snapshot.today()?.let { today ->
            canvas.drawText(
                tr("Máx. ${today.max.degrees()} · Mín. ${today.min.degrees()}", "H ${today.max.degrees()} · L ${today.min.degrees()}"),
                margin,
                700f + y0,
                text(44f, "sans-serif", 235),
            )
        }

        // La frase del día (alerta, lluvia que viene, UV, cambio de mañana...), como en la app.
        Insights.headline(snapshot)?.let { headline ->
            val paint = text(40f, "sans-serif-medium", 245)
            canvas.drawText(TextUtils.ellipsize(headline, paint, w - margin * 2, TextUtils.TruncateAt.END).toString(), margin, 775f + y0, paint)
        }

        // Próximas horas en una franja de vidrio, como las tarjetas de la app.
        var bottom = 820f + y0
        val hours = snapshot.upcomingHours(7).drop(1).take(6)
        if (hours.isNotEmpty()) {
            val panel = RectF(margin - 24f, bottom + 10f, w - margin + 24f, bottom + 340f)
            canvas.drawRoundRect(panel, 48f, 48f, glass)
            canvas.drawText(tr("Próximas horas", "Next hours"), panel.left + 40f, panel.top + 70f, text(34f, "sans-serif-medium", 220))
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
            bottom = panel.bottom
        }

        // En la historia, los próximos 4 días (hoy ya está arriba).
        if (format == ShareFormat.STORY) {
            val today = snapshot.nowAtPlace().toLocalDate()
            val days = snapshot.upcomingDays(5).drop(1).take(4)
            if (days.isNotEmpty()) {
                val rowHeight = 86f
                val panel = RectF(margin - 24f, bottom + 30f, w - margin + 24f, bottom + 30f + 90f + rowHeight * days.size)
                canvas.drawRoundRect(panel, 48f, 48f, glass)
                canvas.drawText(tr("Próximos días", "Next days"), panel.left + 40f, panel.top + 70f, text(34f, "sans-serif-medium", 220))
                val dayPaint = text(44f, "sans-serif-medium")
                val rainPaint = text(36f, "sans-serif-medium").apply { color = 0xFFA6D2FF.toInt() }
                val minPaint = text(44f, "sans-serif", 200).apply { textAlign = Paint.Align.RIGHT }
                val maxPaint = text(44f, "sans-serif-medium").apply { textAlign = Paint.Align.RIGHT }
                days.forEachIndexed { index, day ->
                    val rowTop = panel.top + 90f + rowHeight * index
                    val baseline = rowTop + rowHeight / 2f + 15f
                    canvas.drawText(dayLabel(day.localDate, today), panel.left + 40f, baseline, dayPaint)
                    val size = 68
                    drawIcon(context, canvas, WeatherCodes.icon(day.code, true), (panel.left + 330f).toInt(), (rowTop + (rowHeight - size) / 2f).toInt(), size)
                    // La probabilidad de lluvia solo si vale la pena mirarla.
                    day.precipitationProbability?.takeIf { it >= 20 }?.let {
                        canvas.drawText("$it%", panel.left + 420f, baseline - 2f, rainPaint)
                    }
                    canvas.drawText(day.min.degrees(), panel.right - 170f, baseline, minPaint)
                    canvas.drawText(day.max.degrees(), panel.right - 40f, baseline, maxPaint)
                }
            }
        }

        // Firma y fuentes. Con foto, el crédito que pide la licencia.
        canvas.drawText("Climatin", margin, h - 80f, text(44f, "sans-serif-medium"))
        val sourcePaint = text(30f, "sans-serif", 200).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(tr("Datos: Open-Meteo", "Data: Open-Meteo"), w - margin, h - 82f, sourcePaint)
        options.photo?.takeIf { photo != null }?.let { credit ->
            val creditPaint = text(26f, "sans-serif", 200).apply { textAlign = Paint.Align.RIGHT }
            val line = listOfNotNull(
                credit.title.takeIf { it.isNotBlank() },
                credit.artist.takeIf { it.isNotBlank() },
                credit.license.takeIf { it.isNotBlank() },
                "Wikimedia Commons",
            ).joinToString(" · ")
            canvas.drawText(
                TextUtils.ellipsize(tr("Foto: ", "Photo: ") + line, creditPaint, w - margin * 2, TextUtils.TruncateAt.END).toString(),
                w - margin,
                h - 130f,
                creditPaint,
            )
        }
        return bitmap
    }

    /** La foto guardada, reducida a lo justo para cubrir [width]×[height]. Null si no se puede leer. */
    private fun decodePhoto(photo: PlacePhoto, width: Int, height: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        BitmapFactory.decodeFile(photo.file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** Íconos del clima (vectores) dibujados a [size] px en ([left], [top]). */
    private fun drawIcon(context: Context, canvas: Canvas, res: Int, left: Int, top: Int, size: Int) {
        val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        drawable.setBounds(left, top, left + size, top + size)
        drawable.draw(canvas)
    }
}
