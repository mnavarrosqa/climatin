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
 * Busca, baja y guarda la foto del fondo del pronóstico: siempre de la capital de la provincia (o
 * estado) donde cae el lugar y, si no se encuentra, de la capital del país. OpenStreetMap dice en
 * qué provincia cae el punto, Wikidata cuál es su capital y la foto que la representa, y Commons
 * la licencia y el autor: solo pasan las libres, porque el crédito tiene que poder mostrarse tal cual.
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
    private const val MAX_CANDIDATES = 8

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
        val now = System.currentTimeMillis()
        readMeta(dir, meta, now)?.let { return it.photo }

        // Se envía el centro de la celda, no la ubicación: es lo mismo que se guarda.
        val lat = row * GRID
        val lon = column * GRID
        val found = try {
            capitalPhoto(lat, lon)
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
        // Una foto por capital, no por celda: todas las celdas de la provincia comparten el archivo.
        val image = File(dir, "${found.capital}.jpg")
        if (!image.exists() || now - image.lastModified() > KEEP_MS) {
            val part = File(dir, "${found.capital}.part")
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
        }
        val photo = PlacePhoto(image, found.title, found.artist, found.license, found.descriptionUrl)
        val saved = buildJsonObject {
            put("hasta", now + KEEP_MS)
            put("imagen", image.name)
            put("titulo", photo.title)
            put("autor", photo.artist)
            put("licencia", photo.license)
            put("pagina", photo.descriptionUrl)
        }
        meta.writeText(saved.toString())
        trim(dir)
        return photo
    }

    /**
     * La foto de la capital de la provincia donde cae el punto; si no hay (o no es libre), la de
     * la capital del país. Null si ninguna sirve. Una falla de red sube como excepción.
     */
    private fun capitalPhoto(lat: Double, lon: Double): Found? {
        val coord = String.format(Locale.US, "lat=%.2f&lon=%.2f", lat, lon)
        // zoom 5 = provincia o estado; en países sin provincias (o en el mar) no viene: se usa el país.
        val region = reverse(coord, zoom = 5)
        val regionId = if (region != null && region.string("addresstype") in setOf("state", "province", "region")) {
            region.obj("extratags")?.string("wikidata")
        } else {
            null
        }
        val regionEntity = regionId?.let { entities(listOf(it))[it] }
        val countryId = regionEntity?.let { firstId(it, "P17") }
            ?: reverse(coord, zoom = 3)?.obj("extratags")?.string("wikidata")
            ?: return null
        val capitals = listOfNotNull(
            regionEntity?.let { firstId(it, "P36") },
            entities(listOf(countryId))[countryId]?.let { firstId(it, "P36") },
        ).distinct()
        if (capitals.isEmpty()) return null
        val byId = entities(capitals)
        for (capital in capitals) {
            val entity = byId[capital] ?: continue
            // P18: la imagen que Wikidata eligió para la ciudad (suele ser su postal más conocida).
            val files = claimValues(entity, "P18").filter(::looksLikePhoto)
            if (files.isEmpty()) continue
            val name = label(entity) ?: capital
            resolve(files.map { Candidate(name, it, true, 0) })?.let { return it.copy(capital = capital) }
        }
        return null
    }

    /** Nominatim (OpenStreetMap) al revés: qué hay en estas coordenadas al nivel [zoom]. */
    private fun reverse(coord: String, zoom: Int): JsonObject? = try {
        getJson(
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&$coord&zoom=$zoom" +
                "&extratags=1&accept-language=${Locale.getDefault().language}",
        ).takeIf { !it.containsKey("error") }
    } catch (cancel: CancellationException) {
        throw cancel
    }

    /** Entidades de Wikidata por id, con sus propiedades y el nombre en español e inglés. */
    private fun entities(ids: List<String>): Map<String, JsonObject> {
        if (ids.isEmpty()) return emptyMap()
        val all = getJson(
            "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&ids=${enc(ids.joinToString("|"))}" +
                "&props=${enc("claims|labels")}&languages=${enc("es|en")}",
        ).obj("entities") ?: return emptyMap()
        return all.mapNotNull { (id, value) -> (value as? JsonObject)?.let { id to it } }.toMap()
    }

    private fun claimValues(entity: JsonObject, property: String): List<String> =
        (entity.obj("claims")?.get(property) as? JsonArray).orEmpty().mapNotNull { claim ->
            val value = (claim as? JsonObject)?.obj("mainsnak")?.obj("datavalue")?.get("value")
            when (value) {
                is JsonPrimitive -> value.content
                is JsonObject -> value.string("id")
                else -> null
            }
        }

    private fun firstId(entity: JsonObject, property: String): String? =
        claimValues(entity, property).firstOrNull { it.startsWith("Q") }

    private fun label(entity: JsonObject): String? {
        val labels = entity.obj("labels") ?: return null
        val language = if (Locale.getDefault().language == "es") "es" else "en"
        return labels.obj(language)?.string("value") ?: labels.obj("es")?.string("value") ?: labels.obj("en")?.string("value")
    }

    /** Lo guardado para esta celda, si sigue vigente: una foto o un "no hay" (photo null). */
    private class Cached(val photo: PlacePhoto?)

    private fun readMeta(dir: File, meta: File, now: Long): Cached? {
        if (!meta.exists()) return null
        val obj = runCatching { json.parseToJsonElement(meta.readText()) as? JsonObject }.getOrNull() ?: return null
        val until = obj.string("hasta")?.toLongOrNull() ?: return null
        if (now > until) return null
        if (obj.string("titulo") == null) return Cached(null)
        // Las guardadas por la versión anterior (foto del lugar, sin "imagen") se vuelven a buscar.
        val image = obj.string("imagen")?.let { File(dir, it) } ?: return null
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

    /** Borra lo vencido y deja las [MAX_PHOTOS] fotos más nuevas: es caché, no una galería. */
    private fun trim(dir: File) {
        val now = System.currentTimeMillis()
        val metas = dir.listFiles { file -> file.name.endsWith(".json") }.orEmpty()
        metas.filter { now - it.lastModified() > KEEP_MS }.forEach { it.delete() }
        dir.listFiles { file -> file.name.endsWith(".jpg") || file.name.endsWith(".part") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .forEachIndexed { index, file ->
                if (index >= MAX_PHOTOS || now - file.lastModified() > KEEP_MS || file.name.endsWith(".part")) file.delete()
            }
    }

    private data class Found(val title: String, val url: String, val artist: String, val license: String, val descriptionUrl: String, val capital: String = "")

    private class Candidate(val title: String, val file: String, val matchesPlace: Boolean, val index: Int)

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
