package com.mnavarrosqa.clima.data

import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private const val TILE = 256
    /** Una imagen más vieja que esto ya no dice "ahora". */
    private const val MAX_AGE_SECONDS = 30 * 60L
    /** Medio lado del cuadrado que se mira alrededor del lugar: 2 es 5×5 píxeles, unos 5 km. */
    private const val REACH = 2
    /**
     * Píxeles con eco que hacen falta de los 25. Con 3 alcanzaba un eco suelto (ruido del radar
     * cerca de la antena, lluvia que no llega al suelo) para pasar un día despejado a lluvia; con
     * 6, casi una cuarta parte del cuadrado tiene que estar cubierta.
     */
    private const val MIN_WET_PIXELS = 6
    /** Cada pedido: conectar y leer. Son dos pedidos chicos; si tardan más, el radar no anda bien. */
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 3_000
    /** Lo más que espera quien pregunta, pase lo que pase con la red. */
    private const val TOTAL_TIMEOUT_MS = 7_000L
    /** Si la lista de imágenes falló, no se vuelve a pedir enseguida por cada ciudad. */
    private const val RETRY_AFTER_SECONDS = 60L

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Maps(val host: String, val radar: RadarFrames)

    @Serializable
    private data class RadarFrames(val past: List<Frame> = emptyList())

    @Serializable
    private data class Frame(val time: Long, val path: String)

    /** Última lista de imágenes, para no pedirla por cada ciudad de la misma corrida. */
    @Volatile private var maps: Pair<Long, Maps>? = null
    @Volatile private var mapsFailedAt = 0L
    /** Varias ciudades preguntan a la vez: la lista la baja la primera y las demás la esperan. */
    private val mapsLock = Mutex()

    /**
     * Los pedidos corren acá y no en la corrutina de quien pregunta: leer de la red bloquea el
     * hilo y un `withTimeout` no lo corta. Así el que espera se va a tiempo y el pedido termina
     * solo, con sus propios tiempos de conexión y lectura.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** true si el radar ve lluvia en el lugar; false si no, o si no se pudo saber a tiempo. */
    suspend fun raining(latitude: Double, longitude: Double): Boolean {
        val answer = scope.async { runCatching { check(latitude, longitude) }.getOrDefault(false) }
        return withTimeoutOrNull(TOTAL_TIMEOUT_MS) { answer.await() } ?: false
    }

    private suspend fun frames(now: Long): Maps? = mapsLock.withLock {
        maps?.takeIf { now - it.first < 5 * 60 }?.let { return@withLock it.second }
        if (now - mapsFailedAt < RETRY_AFTER_SECONDS) return@withLock null
        runCatching { json.decodeFromString(Maps.serializer(), read(MAPS_URL).decodeToString()) }
            .onSuccess { maps = now to it }
            .onFailure { mapsFailedAt = now }
            .getOrNull()
    }

    private suspend fun check(latitude: Double, longitude: Double): Boolean {
        val now = System.currentTimeMillis() / 1000
        val list = frames(now) ?: return false
        val frame = list.radar.past.maxByOrNull { it.time } ?: return false
        if (now - frame.time > MAX_AGE_SECONDS) return false

        // Posición del lugar en la tesela (proyección de los mapas web).
        val n = (1 shl ZOOM).toDouble()
        val worldX = (longitude + 180.0) / 360.0 * n
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val worldY = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
        val tileX = floor(worldX).toInt()
        val tileY = floor(worldY).toInt()
        // Cerca del borde de la tesela el cuadrado se corre hacia adentro (2 km como mucho) para
        // mirar siempre los 25 píxeles: recortado, la misma cantidad pedía mucha más cobertura.
        val px = ((worldX - tileX) * TILE).toInt().coerceIn(REACH, TILE - 1 - REACH)
        val py = ((worldY - tileY) * TILE).toInt().coerceIn(REACH, TILE - 1 - REACH)

        // Color 2 ("universal"), sin suavizado ni nieve aparte: píxeles crudos del radar.
        val url = "${list.host}${frame.path}/$TILE/$ZOOM/$tileX/$tileY/2/0_0.png"
        val bytes = read(url)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        try {
            // Una imagen de otro tamaño no es lo que se pidió: mejor no sacar conclusiones.
            if (bitmap.width != TILE || bitmap.height != TILE) return false
            var wet = 0
            for (dy in -REACH..REACH) for (dx in -REACH..REACH) {
                if ((bitmap.getPixel(px + dx, py + dy) ushr 24) > 0) wet++
            }
            return wet >= MIN_WET_PIXELS
        } finally {
            bitmap.recycle()
        }
    }

    private fun read(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "Climatin")
        try {
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
