package com.mnavarrosqa.clima.data

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Si el radar ve lluvia ahora en un lugar. Los modelos de pronóstico pierden seguido las
 * tormentas que se arman en poco tiempo: con alerta y lloviendo, el pronóstico podía decir
 * "despejado". El radar mide la lluvia que está cayendo.
 *
 * Usa la API pública de RainViewer (gratuita, sin clave): la última imagen del radar, una tesela
 * de 256 px a zoom 7 (cerca de 1 km por píxel en la Argentina), y mira los píxeles alrededor del
 * lugar. Donde no hay cobertura la imagen es transparente y se toma como "no llueve": solo se
 * usa para corregir hacia lluvia, nunca para sacarla.
 */
object RadarNow {
    private const val MAPS_URL = "https://api.rainviewer.com/public/weather-maps.json"
    private const val ZOOM = 7
    /** Una imagen más vieja que esto ya no dice "ahora". */
    private const val MAX_AGE_SECONDS = 30 * 60L
    /** Píxeles con lluvia que hacen falta en el cuadrado de 5×5 alrededor del lugar (ruido aparte). */
    private const val MIN_WET_PIXELS = 3

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Maps(val host: String, val radar: RadarFrames)

    @Serializable
    private data class RadarFrames(val past: List<Frame> = emptyList())

    @Serializable
    private data class Frame(val time: Long, val path: String)

    /** Última lista de imágenes, para no pedirla por cada ciudad de la misma corrida. */
    @Volatile private var maps: Pair<Long, Maps>? = null

    /** true si el radar ve lluvia en el lugar; false si no, o si no se pudo saber. */
    suspend fun raining(latitude: Double, longitude: Double): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(8_000) { runCatching { check(latitude, longitude) }.getOrNull() } ?: false
    }

    private fun check(latitude: Double, longitude: Double): Boolean {
        val now = System.currentTimeMillis() / 1000
        val list = maps?.takeIf { now - it.first < 5 * 60 }?.second
            ?: json.decodeFromString(Maps.serializer(), read(MAPS_URL).decodeToString()).also { maps = now to it }
        val frame = list.radar.past.maxByOrNull { it.time } ?: return false
        if (now - frame.time > MAX_AGE_SECONDS) return false

        // Posición del lugar en la tesela (proyección de los mapas web).
        val n = (1 shl ZOOM).toDouble()
        val worldX = (longitude + 180.0) / 360.0 * n
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val worldY = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
        val tileX = floor(worldX).toInt()
        val tileY = floor(worldY).toInt()
        val px = ((worldX - tileX) * 256).toInt()
        val py = ((worldY - tileY) * 256).toInt()

        // Color 2 ("universal"), sin suavizado ni nieve aparte: píxeles crudos del radar.
        val url = "${list.host}${frame.path}/256/$ZOOM/$tileX/$tileY/2/0_0.png"
        val bytes = read(url)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        try {
            var wet = 0
            for (dy in -2..2) for (dx in -2..2) {
                val x = px + dx
                val y = py + dy
                if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) continue
                if ((bitmap.getPixel(x, y) ushr 24) > 0) wet++
            }
            return wet >= MIN_WET_PIXELS
        } finally {
            bitmap.recycle()
        }
    }

    private fun read(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 6_000
        connection.setRequestProperty("User-Agent", "Climatin")
        try {
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
