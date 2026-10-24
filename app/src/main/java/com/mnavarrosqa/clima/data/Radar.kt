package com.mnavarrosqa.clima.data

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class RadarMaps(val host: String, val radar: RadarFrames)

@Serializable
data class RadarFrames(val past: List<RadarFrame> = emptyList(), val nowcast: List<RadarFrame> = emptyList())

/** Un cuadro del radar: [time] en segundos Unix. */
@Serializable
data class RadarFrame(val time: Long, val path: String)

/**
 * Radar de lluvia de RainViewer sobre un mapa de CARTO (datos de OpenStreetMap). RainViewer
 * limita el zoom a 7 en su API gratuita.
 */
object Radar {
    const val MAX_ZOOM = 7
    const val MIN_ZOOM = 3

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun frames(): Pair<String, List<RadarFrame>> = withContext(Dispatchers.IO) {
        val body = read("https://api.rainviewer.com/public/weather-maps.json").decodeToString()
        val maps = json.decodeFromString(RadarMaps.serializer(), body)
        maps.host to (maps.radar.past + maps.radar.nowcast)
    }

    fun radarTileUrl(host: String, frame: RadarFrame, z: Int, x: Int, y: Int) =
        "$host${frame.path}/256/$z/$x/$y/2/1_1.png"

    fun baseTileUrl(dark: Boolean, z: Int, x: Int, y: Int) =
        "https://basemaps.cartocdn.com/${if (dark) "dark_all" else "light_all"}/$z/$x/$y.png"

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
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Climatin (Android)")
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
