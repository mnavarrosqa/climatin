package com.mnavarrosqa.clima.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh

/** Lluvia en una grilla para una hora: [values] en mm, fila por fila. */
class RainFrame(val epochSeconds: Long, val values: FloatArray)

/**
 * Mapa de lluvia: la precipitación por hora de Open-Meteo (sin clave) en una grilla de puntos sobre
 * el mapa, de unas horas atrás a unas horas adelante. No es un radar: es lo que calcula el modelo
 * meteorológico, pero cubre todo el mundo y no necesita registro. El fondo es el mapa gris de Esri.
 */
object RainMap {
    const val MIN_ZOOM = 4
    const val MAX_ZOOM = 9
    // Más puntos que antes (12x16): la lluvia se ve más fina. Siguen siendo una sola consulta.
    const val COLUMNS = 14
    const val ROWS = 20

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class PointResponse(val hourly: PointHourly)

    @Serializable
    private data class PointHourly(val time: List<String>, val precipitation: List<Double?>)

    private fun longitude(tileX: Double, zoom: Int) = tileX / (1 shl zoom) * 360.0 - 180.0

    private fun latitude(tileY: Double, zoom: Int) =
        Math.toDegrees(atan(sinh(PI * (1 - 2 * tileY / (1 shl zoom)))))

    /**
     * Pide la lluvia de una grilla pareja en coordenadas de tesela (así se dibuja derecha sobre el
     * mapa) entre ([x0], [y0]) y ([x1], [y1]): 3 horas atrás y 12 adelante, en una sola consulta.
     */
    suspend fun load(x0: Double, y0: Double, x1: Double, y1: Double, zoom: Int): List<RainFrame> =
        withContext(Dispatchers.IO) {
            val lats = mutableListOf<String>()
            val lons = mutableListOf<String>()
            for (row in 0 until ROWS) {
                for (col in 0 until COLUMNS) {
                    val tx = x0 + (col + 0.5) / COLUMNS * (x1 - x0)
                    val ty = y0 + (row + 0.5) / ROWS * (y1 - y0)
                    var lon = longitude(tx, zoom)
                    lon = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                    lats += String.format(Locale.US, "%.3f", latitude(ty, zoom).coerceIn(-85.0, 85.0))
                    lons += String.format(Locale.US, "%.3f", lon)
                }
            }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${lats.joinToString(",")}" +
                "&longitude=${lons.joinToString(",")}&hourly=precipitation&past_hours=3&forecast_hours=12&timezone=GMT"
            val body = read(url).decodeToString()
            val points = json.decodeFromString(ListSerializer(PointResponse.serializer()), body)
            val times = points.firstOrNull()?.hourly?.time.orEmpty()
            times.indices.map { t ->
                val values = FloatArray(points.size) { p -> (points[p].hourly.precipitation.getOrNull(t) ?: 0.0).toFloat() }
                RainFrame(LocalDateTime.parse(times[t]).toEpochSecond(ZoneOffset.UTC), values)
            }
        }

    /**
     * Imagen chica (una celda por punto) con colores de lluvia. Al estirarla con filtrado suave
     * queda un degradado continuo, como los mapas de lluvia de las apps de clima.
     */
    fun overlay(frame: RainFrame): ImageBitmap {
        val bitmap = Bitmap.createBitmap(COLUMNS, ROWS, Bitmap.Config.ARGB_8888)
        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) {
                bitmap.setPixel(col, row, rainColor(frame.values[row * COLUMNS + col]))
            }
        }
        return bitmap.asImageBitmap()
    }

    /** Celeste para llovizna, azul para lluvia, amarillo y rojo para lluvia fuerte. */
    private fun rainColor(mm: Float): Int = when {
        mm < 0.1f -> 0x00000000
        mm < 0.5f -> 0x6681D4FA
        mm < 1.5f -> 0x9942A5F5.toInt()
        mm < 4f -> 0xB31E88E5.toInt()
        mm < 8f -> 0xC0FFC107.toInt()
        mm < 15f -> 0xCCFF7043.toInt()
        else -> 0xD9E53935.toInt()
    }

    /**
     * Mapa base gris de Esri (claro u oscuro): no pide clave y, al ser neutro, deja ver bien la
     * lluvia encima. Ojo con el orden: Esri usa z/y/x. CARTO pasó a pedir clave en 2026.
     */
    fun baseTileUrl(dark: Boolean, z: Int, x: Int, y: Int): String {
        val layer = if (dark) "World_Dark_Gray_Base" else "World_Light_Gray_Base"
        return "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/$layer/MapServer/tile/$z/$y/$x"
    }

    suspend fun tile(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = read(url)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    private fun read(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "Climatin (Android)")
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
