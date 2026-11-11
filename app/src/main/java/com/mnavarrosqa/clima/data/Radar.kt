package com.mnavarrosqa.clima.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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

/** Una imagen del radar: cuándo se midió y la ruta de sus teselas en el servidor. */
class RadarImage(val epochSeconds: Long, val path: String)

/** Las imágenes del radar de las últimas horas, de la más vieja a la más nueva. */
class RadarList(val host: String, val frames: List<RadarImage>)

/**
 * Mapa de lluvia. Dos fuentes: el radar de RainViewer (lo que se midió en las últimas dos horas,
 * donde hay cobertura) y la precipitación por hora de Open-Meteo en una grilla de puntos (lo que
 * calcula el modelo: cubre todo el mundo y llega al futuro). Ninguna pide clave. El fondo es el
 * mapa gris de Esri.
 */
object RainMap {
    const val MIN_ZOOM = 4
    const val MAX_ZOOM = 9
    // Open-Meteo cuenta cada punto como una consulta y limita a 600 por minuto: con 12x16 (192)
    // entran varias cargas por minuto mientras se recorre el mapa.
    const val COLUMNS = 12
    const val ROWS = 16

    /** Open-Meteo devolvió 429: se pasó el límite de consultas por minuto. */
    class TooManyRequests : Exception("HTTP 429")

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
    suspend fun load(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        zoom: Int,
        columns: Int = COLUMNS,
        rows: Int = ROWS,
    ): List<RainFrame> =
        withContext(Dispatchers.IO) {
            val lats = mutableListOf<String>()
            val lons = mutableListOf<String>()
            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val tx = x0 + (col + 0.5) / columns * (x1 - x0)
                    val ty = y0 + (row + 0.5) / rows * (y1 - y0)
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
    fun overlay(frame: RainFrame, columns: Int = COLUMNS, rows: Int = ROWS): ImageBitmap {
        val bitmap = Bitmap.createBitmap(columns, rows, Bitmap.Config.ARGB_8888)
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                bitmap.setPixel(col, row, rainColor(frame.values[row * columns + col]))
            }
        }
        return bitmap.asImageBitmap()
    }

    /** Vista previa de la tarjeta: el mapa alrededor de un lugar y la lluvia de esta hora. */
    /**
     * [nearby]: llueve a unos 40 km; [inArea]: en algún lugar del mapita (unos 300 km).
     * [rainKnown] es falso si la lluvia no se pudo bajar: entonces no se dice que no llueve.
     */
    class Preview(
        val tiles: List<Pair<IntArray, ImageBitmap>>,
        val x0: Double,
        val y0: Double,
        val rain: ImageBitmap?,
        val nearby: Boolean,
        val inArea: Boolean,
        val rainKnown: Boolean,
    )

    const val PREVIEW_ZOOM = 7
    private const val PREVIEW_GRID = 8
    // Una por lugar y por 15 minutos: la tarjeta entra y sale de la pantalla al desplazarse.
    private val previews = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Preview>>()

    /**
     * Arma la vista previa: cuatro teselas del mapa (un ancho de tesela centrado en el lugar) y
     * una grilla chica de lluvia (64 puntos, una sola consulta) para la hora actual.
     */
    suspend fun preview(latitude: Double, longitude: Double, dark: Boolean): Preview? {
        val key = String.format(Locale.US, "%.2f,%.2f,%b", latitude, longitude, dark)
        previews[key]?.let { (at, preview) -> if (System.currentTimeMillis() - at < 15 * 60_000L) return preview }
        val cx = Geo.tileX(longitude, PREVIEW_ZOOM)
        val cy = Geo.tileY(latitude, PREVIEW_ZOOM)
        val x0 = cx - 0.5
        val y0 = cy - 0.5
        val max = (1 shl PREVIEW_ZOOM) - 1
        val tiles = buildList {
            for (tx in kotlin.math.floor(x0).toInt()..kotlin.math.floor(x0 + 1).toInt()) {
                for (ty in kotlin.math.floor(y0).toInt()..kotlin.math.floor(y0 + 1).toInt()) {
                    if (ty !in 0..max) continue
                    tile(baseTileUrl(dark, PREVIEW_ZOOM, Math.floorMod(tx, 1 shl PREVIEW_ZOOM), ty))?.let { add(intArrayOf(tx, ty) to it) }
                }
            }
        }
        if (tiles.isEmpty()) return null
        val frames = runCatching { load(x0, y0, x0 + 1, y0 + 1, PREVIEW_ZOOM, PREVIEW_GRID, PREVIEW_GRID) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull()
        val now = System.currentTimeMillis() / 1000
        // Cada valor es la lluvia de la hora anterior a su marca: la hora en curso es la próxima marca.
        val frame = frames?.firstOrNull { it.epochSeconds > now } ?: frames?.lastOrNull()
        val preview = Preview(
            tiles,
            x0,
            y0,
            frame?.let { overlay(it, PREVIEW_GRID, PREVIEW_GRID) },
            // Las 4 celdas del medio son unos 40 km alrededor del lugar.
            nearby = frame?.values?.withIndex()?.any { (i, v) -> v >= 0.1f && i / PREVIEW_GRID in 3..4 && i % PREVIEW_GRID in 3..4 } == true,
            inArea = frame?.values?.any { it >= 0.1f } == true,
            rainKnown = frame != null,
        )
        // Si faltó la lluvia o alguna tesela, no se guarda: la próxima vez se vuelve a probar.
        val expected = (kotlin.math.floor(x0 + 1).toInt() - kotlin.math.floor(x0).toInt() + 1) *
            (kotlin.math.floor(y0 + 1).toInt() - kotlin.math.floor(y0).toInt() + 1)
        if (frame != null && tiles.size >= expected) previews[key] = System.currentTimeMillis() to preview
        return preview
    }

    // ---- Radar (RainViewer) ----

    /** Las teselas gratuitas de RainViewer llegan hasta este zoom; más cerca se estira esta. */
    const val RADAR_MAX_ZOOM = 7
    private const val RADAR_MAPS_URL = "https://api.rainviewer.com/public/weather-maps.json"
    /** RainViewer publica una imagen cada 10 minutos: antes de esto la lista no cambió. */
    private const val RADAR_LIST_SECONDS = 5 * 60L

    @Serializable
    private data class RadarMaps(val host: String, val radar: RadarPast)

    @Serializable
    private data class RadarPast(val past: List<RadarPath> = emptyList())

    @Serializable
    private data class RadarPath(val time: Long, val path: String)

    @Volatile private var radarListCache: Pair<Long, RadarList>? = null

    /** Si RainViewer contesta 429, no se le pide nada más hasta esta hora (ms). */
    @Volatile private var radarPausedUntil = 0L

    /**
     * Teselas del radar ya decodificadas, acotadas por memoria (unos 32 MB: cada una de 256 px
     * ocupa 256 KB). Viven con el proceso: al volver a la pestaña del mapa no se bajan de nuevo.
     */
    private val radarTiles = object : LruCache<String, ImageBitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    /** La misma para todas las teselas sin nada (sin lluvia o sin cobertura): no ocupan memoria. */
    private val blankTile: ImageBitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap() }

    /** true si la tesela del radar vino toda transparente. */
    fun isBlank(tile: ImageBitmap): Boolean = tile === blankTile

    /** La lista de imágenes del radar, o null si no se pudo bajar (entonces se usa solo el modelo). */
    suspend fun radarList(): RadarList? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        radarListCache?.takeIf { now - it.first < RADAR_LIST_SECONDS }?.second ?: runCatching {
            val maps = json.decodeFromString(RadarMaps.serializer(), read(RADAR_MAPS_URL).decodeToString())
            val frames = maps.radar.past
                .filter { it.path.isNotBlank() }
                .sortedBy { it.time }
                .map { RadarImage(it.time, it.path) }
            if (maps.host.isBlank() || frames.isEmpty()) null else RadarList(maps.host, frames).also { radarListCache = now to it }
        }.getOrNull()
    }

    /** Tesela del radar: color 2 ("universal"), suavizada y con la nieve aparte. */
    fun radarTileUrl(host: String, path: String, z: Int, x: Int, y: Int): String = "$host$path/256/$z/$x/$y/2/1_1.png"

    /** La tesela si ya está en memoria. */
    fun radarCached(url: String): ImageBitmap? = radarTiles.get(url)

    /** Baja y decodifica una tesela del radar. null si falló: no se guarda y se puede volver a pedir. */
    suspend fun radarTile(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        radarTiles.get(url)?.let { return@withContext it }
        if (System.currentTimeMillis() < radarPausedUntil) return@withContext null
        runCatching<ImageBitmap?> {
            val bytes = read(url)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val tile = if (pixels.any { it ushr 24 != 0 }) {
                bitmap.asImageBitmap()
            } else {
                bitmap.recycle()
                blankTile
            }
            radarTiles.put(url, tile)
            tile
        }.onFailure {
            if (it is TooManyRequests) radarPausedUntil = System.currentTimeMillis() + 60_000L
        }.getOrNull()
    }

    /** Si el radar marca algo en ese píxel de la tesela ([fx] y [fy] de 0 a 1 dentro de ella). */
    fun radarWet(tile: ImageBitmap, fx: Double, fy: Double): Boolean {
        if (isBlank(tile)) return false
        val x = (fx * tile.width).toInt().coerceIn(0, tile.width - 1)
        val y = (fy * tile.height).toInt().coerceIn(0, tile.height - 1)
        return runCatching { tile.asAndroidBitmap().getPixel(x, y) ushr 24 != 0 }.getOrDefault(false)
    }

    /**
     * Celeste para llovizna, azul para lluvia, amarillo y rojo para lluvia fuerte. Más opacos que
     * antes: sobre el mapa claro, la lluvia débil casi no se distinguía.
     */
    private fun rainColor(mm: Float): Int = when {
        mm < 0.1f -> 0x00000000
        mm < 0.5f -> 0x9981D4FA.toInt()
        mm < 1.5f -> 0xB342A5F5.toInt()
        mm < 4f -> 0xCC1E88E5.toInt()
        mm < 8f -> 0xD9FFC107.toInt()
        mm < 15f -> 0xE0FF7043.toInt()
        else -> 0xE6E53935.toInt()
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
            val code = connection.responseCode
            if (code == 429) throw TooManyRequests()
            if (code !in 200..299) error("HTTP $code")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
