package com.mnavarrosqa.clima.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Foto libre (Wikimedia Commons) que representa un lugar, ya bajada a la caché. [artist] puede
 * venir vacío (dominio público sin autor): el crédito muestra solo la licencia.
 */
data class PlacePhoto(
    val file: File,
    val title: String,
    val artist: String,
    val license: String,
    val descriptionUrl: String,
)

/**
 * Busca, baja y guarda la foto del fondo del pronóstico. Wikipedia elige la imagen principal de
 * los artículos cercanos (suele ser la postal del lugar) y Commons dice la licencia y el autor:
 * solo pasan las libres, porque el crédito tiene que poder mostrarse tal cual.
 */
object PlacePhotos {
    // Wikimedia pide un User-Agent que identifique la app y cómo contactar: a los genéricos les
    // responde 429 (demasiados pedidos).
    private const val UA = "Climatin/1.38 (https://github.com/mnavarrosqa/climatin; app de clima para Android)"
    private const val DIR = "fotos"

    /** Grilla de la caché y de lo que se envía: unos 5 km, ni la ubicación exacta ni una foto por cuadra. */
    private const val GRID = 0.05

    private const val KEEP_MS = 30L * 24 * 60 * 60_000
    /** Sin foto en la zona: no vuelve a preguntar hasta mañana. */
    private const val EMPTY_MS = 24L * 60 * 60_000
    /** Sin red o con Wikimedia limitando pedidos: se reintenta antes que un "no hay", pero no en cada vuelta. */
    private const val FAILED_MS = 60L * 60_000
    private const val MAX_PHOTOS = 20
    private const val MIN_WIDTH = 800
    private const val MAX_CANDIDATES = 8
    /** Hasta dónde un artículo con el nombre del lugar se toma como ese lugar (ciudades grandes, el centro lejos). */
    private const val NAMED_KM = 25.0

    private val json = Json { ignoreUnknownKeys = true }

    /** Mapas, escudos y banderas son la imagen principal de muchos artículos de ciudades: no sirven de fondo. */
    private val notPhotos = listOf(
        "mapa", "map", "escudo", "coat", "bandera", "flag", "logo", "locator", "location",
        "ubicacion", "plano", "diagram", "seal", "sello",
    )

    /** Una ciudad a la vez: al abrir la app, varias páginas piden su foto juntas. */
    private val lock = Mutex()

    /**
     * La foto de este lugar, de la caché o recién bajada. Null si no hay una libre cerca, sin red
     * o ante cualquier error: el pronóstico sigue con el cielo animado.
     */
    suspend fun photoFor(context: Context, latitude: Double, longitude: Double, placeName: String?): PlacePhoto? =
        withContext(Dispatchers.IO) {
            try {
                lock.withLock { find(context, latitude, longitude, placeName) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                null
            }
        }

    /**
     * Decodifica la foto achicada (inSampleSize) hasta lo justo para cubrir [width] x [height]:
     * con el desenfoque encima no hace falta más y ocupa menos memoria.
     */
    suspend fun bitmap(photo: PlacePhoto, width: Int, height: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photo.file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
            BitmapFactory.decodeFile(photo.file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            null
        }
    }

    private fun find(context: Context, latitude: Double, longitude: Double, placeName: String?): PlacePhoto? {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val row = (latitude / GRID).roundToInt()
        val column = (longitude / GRID).roundToInt()
        val key = "f_${row}_$column".replace('-', 'm')
        val meta = File(dir, "$key.json")
        val image = File(dir, "$key.jpg")
        val now = System.currentTimeMillis()
        readMeta(meta, image, now)?.let { return it.photo }

        // Se envía el centro de la celda, no la ubicación: es lo mismo que se guarda.
        val lat = row * GRID
        val lon = column * GRID
        val found = try {
            val name = placeName?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
            // Primero el artículo del lugar, en español o en inglés: en Madrid, el de la Wikipedia
            // en español tiene la bandera de imagen principal y el inglés una foto.
            val named = name?.let { byName("es", it, lat, lon) ?: byName("en", it, lat, lon) }
            named?.let { resolve(listOf(it)) } ?: nearby("es", lat, lon, name) ?: nearby("en", lat, lon, name)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            writeEmpty(meta, now, FAILED_MS)
            return null
        }
        if (found == null) {
            writeEmpty(meta, now, EMPTY_MS)
            return null
        }
        val part = File(dir, "$key.part")
        try {
            download(found.url, part)
            // Que de verdad sea una imagen antes de reemplazar la anterior.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(part.path, bounds)
            if (bounds.outWidth <= 0) error("no es una imagen")
            image.delete()
            if (!part.renameTo(image)) error("no se pudo guardar")
        } catch (cancel: CancellationException) {
            part.delete()
            throw cancel
        } catch (_: Exception) {
            part.delete()
            writeEmpty(meta, now, FAILED_MS)
            return null
        }
        val photo = PlacePhoto(image, found.title, found.artist, found.license, found.descriptionUrl)
        val saved = buildJsonObject {
            put("hasta", now + KEEP_MS)
            put("titulo", photo.title)
            put("autor", photo.artist)
            put("licencia", photo.license)
            put("pagina", photo.descriptionUrl)
        }
        meta.writeText(saved.toString())
        trim(dir)
        return photo
    }

    /** Lo guardado para esta celda, si sigue vigente: una foto o un "no hay" (photo null). */
    private class Cached(val photo: PlacePhoto?)

    private fun readMeta(meta: File, image: File, now: Long): Cached? {
        if (!meta.exists()) return null
        val obj = runCatching { json.parseToJsonElement(meta.readText()) as? JsonObject }.getOrNull() ?: return null
        val until = obj.string("hasta")?.toLongOrNull() ?: return null
        if (now > until) return null
        if (obj.string("titulo") == null) return Cached(null)
        if (!image.exists()) return null
        return Cached(
            PlacePhoto(
                file = image,
                title = obj.string("titulo").orEmpty(),
                artist = obj.string("autor").orEmpty(),
                license = obj.string("licencia").orEmpty(),
                descriptionUrl = obj.string("pagina").orEmpty(),
            ),
        )
    }

    private fun writeEmpty(meta: File, now: Long, keep: Long) {
        runCatching { meta.writeText(buildJsonObject { put("hasta", now + keep) }.toString()) }
    }

    /** Deja las [MAX_PHOTOS] fotos más nuevas y borra lo vencido: es caché, no una galería. */
    private fun trim(dir: File) {
        val now = System.currentTimeMillis()
        var photos = 0
        dir.listFiles { file -> file.name.endsWith(".json") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .forEach { meta ->
                val image = File(dir, meta.name.removeSuffix(".json") + ".jpg")
                val hasPhoto = image.exists()
                if (hasPhoto) photos++
                if (now - meta.lastModified() > KEEP_MS || (hasPhoto && photos > MAX_PHOTOS)) {
                    meta.delete()
                    image.delete()
                }
            }
    }

    private class Found(val title: String, val url: String, val artist: String, val license: String, val descriptionUrl: String)

    private class Candidate(val title: String, val file: String, val matchesPlace: Boolean, val index: Int)

    /**
     * Artículos de [wiki] a menos de 10 km con imagen principal: uno que se llame como el lugar
     * (por si no tiene coordenadas propias), si no el más cercano con una foto que sirva.
     */
    private fun nearby(wiki: String, lat: Double, lon: Double, name: String?): Found? {
        val coord = String.format(Locale.US, "%.2f|%.2f", lat, lon)
        val pages = getJson(
            "https://$wiki.wikipedia.org/w/api.php?action=query&format=json&generator=geosearch" +
                "&ggscoord=${enc(coord)}&ggsradius=10000&ggslimit=50&prop=pageimages" +
                "&piprop=${enc("thumbnail|name")}&pithumbsize=1280",
        ).obj("query")?.obj("pages") ?: return null
        val wanted = name?.let(::plain)
        val candidates = pages.values.mapNotNull { element ->
            val page = element as? JsonObject ?: return@mapNotNull null
            val candidate = candidateOf(page) ?: return@mapNotNull null
            if (wanted != null && plain(candidate.title) == wanted) Candidate(candidate.title, candidate.file, true, candidate.index) else candidate
        }
            .sortedWith(compareBy<Candidate> { !it.matchesPlace }.thenBy { it.index })
            .distinctBy { it.file }
            .take(MAX_CANDIDATES)
        return resolve(candidates)
    }

    /** La primera de [candidates] (en orden) que está en Commons con licencia libre y autor. */
    private fun resolve(candidates: List<Candidate>): Found? {
        if (candidates.isEmpty()) return null

        // Todas en un solo pedido a Commons. Las que no están ahí (subidas a una Wikipedia, casi
        // siempre de uso legítimo y no libres) vuelven como "missing" y quedan afuera.
        val titles = candidates.joinToString("|") { "File:" + it.file }
        val infoPages = getJson(
            "https://commons.wikimedia.org/w/api.php?action=query&format=json&titles=${enc(titles)}" +
                "&prop=imageinfo&iiprop=${enc("extmetadata|url")}" +
                "&iiextmetadatafilter=${enc("Artist|LicenseShortName|UsageTerms")}&iiurlwidth=1280",
        ).obj("query")?.obj("pages") ?: return null
        val infoByFile = infoPages.values.mapNotNull { element ->
            val page = element as? JsonObject ?: return@mapNotNull null
            if (page.containsKey("missing")) return@mapNotNull null
            val info = (page["imageinfo"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@mapNotNull null
            val name = page.string("title")?.substringAfter(':') ?: return@mapNotNull null
            plain(name) to info
        }.toMap()
        for (candidate in candidates) {
            val info = infoByFile[plain(candidate.file)] ?: continue
            val url = info.string("thumburl") ?: continue
            val metadata = info.obj("extmetadata")
            val license = metadata?.obj("LicenseShortName")?.string("value")?.let(::stripHtml).orEmpty()
            if (!isFree(license)) continue
            val artist = metadata?.obj("Artist")?.string("value")?.let(::stripHtml).orEmpty().take(80)
            // CC y GFDL piden nombrar al autor: sin autor, solo vale si no hace falta.
            if (artist.isEmpty() && !license.lowercase(Locale.ROOT).let { it.startsWith("public domain") || it.startsWith("pd") || it.startsWith("cc0") }) continue
            val page = info.string("descriptionurl") ?: continue
            return Found(candidate.title, url, artist, license, page)
        }
        return null
    }

    /** Un artículo con una imagen principal que sirve de fondo, o null. */
    private fun candidateOf(page: JsonObject): Candidate? {
        val title = page.string("title") ?: return null
        val file = page.string("pageimage") ?: return null
        val width = page.obj("thumbnail")?.string("width")?.toIntOrNull() ?: return null
        if (!looksLikePhoto(file) || width < MIN_WIDTH) return null
        return Candidate(title, file, false, page.string("index")?.toIntOrNull() ?: Int.MAX_VALUE)
    }

    /**
     * El artículo llamado [name] si está a menos de [NAMED_KM] del punto: "Córdoba" en la
     * Wikipedia en español es una desambiguación sin coordenadas y en otras es la de España: ninguna
     * tiene que salir para la de Argentina. Una falla
     * acá no corta la búsqueda: quedan los cercanos.
     */
    private fun byName(wiki: String, name: String, lat: Double, lon: Double): Candidate? = try {
        val pages = getJson(
            "https://$wiki.wikipedia.org/w/api.php?action=query&format=json&redirects=1&titles=${enc(name)}" +
                "&prop=${enc("pageimages|coordinates")}&piprop=${enc("thumbnail|name")}&pithumbsize=1280",
        ).obj("query")?.obj("pages")
        pages?.values.orEmpty().firstNotNullOfOrNull { element ->
            val page = element as? JsonObject ?: return@firstNotNullOfOrNull null
            val point = (page["coordinates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@firstNotNullOfOrNull null
            val pageLat = point.string("lat")?.toDoubleOrNull() ?: return@firstNotNullOfOrNull null
            val pageLon = point.string("lon")?.toDoubleOrNull() ?: return@firstNotNullOfOrNull null
            if (km(lat, lon, pageLat, pageLon) > NAMED_KM) return@firstNotNullOfOrNull null
            candidateOf(page)?.let { Candidate(it.title, it.file, true, -1) }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        null
    }

    /** Distancia aproximada (equirrectangular): alcanza para decir "cerca" o "en otro país". */
    private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x = Math.toRadians(lon2 - lon1) * kotlin.math.cos(Math.toRadians((lat1 + lat2) / 2))
        val y = Math.toRadians(lat2 - lat1)
        return kotlin.math.sqrt(x * x + y * y) * 6371.0
    }

    private fun looksLikePhoto(file: String): Boolean {
        val name = plain(file)
        val extension = name.substringAfterLast('.', "")
        if (extension != "jpg" && extension != "jpeg") return false
        return notPhotos.none { it in name }
    }

    /** Solo licencias libres: dominio público, CC0, CC BY, CC BY-SA y GFDL. Nada "no comercial" ni "sin derivadas". */
    internal fun isFree(license: String): Boolean {
        val value = license.lowercase(Locale.ROOT).trim()
        if (value.isEmpty()) return false
        val words = value.split(' ', '-', '_')
        if ("nc" in words || "nd" in words) return false
        return listOf("public domain", "pd", "cc0", "cc by", "cc-by", "gfdl").any { value.startsWith(it) }
    }

    /** Sin tildes, sin mayúsculas, sin lo que va entre paréntesis: "Córdoba (Argentina)" es "cordoba". */
    internal fun plain(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace('_', ' ')
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** El autor viene en HTML (enlaces a su página): queda solo el texto. */
    internal fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun getJson(url: String): JsonObject {
        val connection = open(url)
        try {
            val code = connection.responseCode
            // 429 (demasiados pedidos) devuelve texto, no JSON: cuenta como falla, no como "no hay".
            if (code !in 200..299) error("HTTP $code")
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            return json.parseToJsonElement(text) as? JsonObject ?: error("respuesta inesperada")
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File) {
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("User-Agent", UA)
        }

    private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

    /** Texto o número como texto: "index" y "width" vienen como números. */
    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.content
}
