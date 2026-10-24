package com.mnavarrosqa.clima.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

/** Alerta del SMN con las zonas que cubre y el enlace de donde salió. */
@Serializable
data class CapAlert(
    val alert: WeatherAlert,
    val polygons: List<List<Pair<Double, Double>>>,
    val link: String = "",
) {
    fun covers(latitude: Double, longitude: Double) = polygons.any { Geo.contains(it, latitude, longitude) }
}

/**
 * Alertas del Servicio Meteorológico Nacional en formato CAP. El canal RSS lista una entrada por
 * alerta y cada una enlaza al XML con el evento, la severidad y los polígonos afectados.
 */
object SmnAlerts {
    private const val FEED = "https://ssl.smn.gob.ar/CAP/AR.php"
    private const val MAX_ALERTS = 60

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(CapAlert.serializer())

    /**
     * Cada alerta CAP no cambia una vez publicada: se guardan en disco y solo se descargan las
     * nuevas. Así cada actualización baja el canal RSS y, normalmente, nada más.
     */
    suspend fun fetchAll(cacheDir: File): List<CapAlert> = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "smn_alertas.json")
        val known = runCatching { json.decodeFromString(listSerializer, file.readText()) }
            .getOrDefault(emptyList())
            .associateBy { it.link }
        val links = get(FEED) { parseFeedLinks(it) }.take(MAX_ALERTS)
        val alerts = coroutineScope {
            links.map { link ->
                async { known[link] ?: runCatching { get(link) { parseCap(it, link) } }.getOrNull() }
            }.awaitAll().filterNotNull()
        }
        runCatching { file.writeText(json.encodeToString(listSerializer, alerts)) }
        alerts.filter { it.isActive() }
    }

    private fun CapAlert.isActive(): Boolean {
        val expires = alert.expires ?: return true
        return runCatching { OffsetDateTime.parse(expires).isAfter(OffsetDateTime.now()) }.getOrDefault(true)
    }

    private fun <T> get(url: String, read: (InputStream) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Climatin (Android)")
            if (connection.responseCode !in 200..299) error("SMN respondió ${connection.responseCode}")
            return connection.inputStream.use(read)
        } finally {
            connection.disconnect()
        }
    }

    private fun parser(input: InputStream): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }

    /** Sin namespaces, una etiqueta puede venir como "cap:info": se usa solo el nombre local. */
    private val XmlPullParser.local: String get() = name.substringAfter(':')

    private fun parseFeedLinks(input: InputStream): List<String> {
        val p = parser(input)
        val links = mutableListOf<String>()
        var inItem = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.local) {
                    "item", "entry" -> inItem = true
                    "link" -> if (inItem) {
                        // RSS trae el enlace como texto; Atom, en el atributo href.
                        val href = p.getAttributeValue(null, "href")
                        val text = href ?: p.nextText()
                        if (text.isNotBlank()) links += text.trim()
                    }
                }
                XmlPullParser.END_TAG -> if (p.local == "item" || p.local == "entry") inItem = false
            }
        }
        return links.distinct()
    }

    private fun parseCap(input: InputStream, link: String): CapAlert? {
        val p = parser(input)
        var identifier = link
        var event: String? = null
        var severity: String? = null
        var headline: String? = null
        var description: String? = null
        var onset: String? = null
        var expires: String? = null
        val polygons = mutableListOf<List<Pair<Double, Double>>>()
        var infoCount = 0
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.local) {
                "identifier" -> identifier = p.nextText().trim()
                "info" -> infoCount++
                // Si hay varios bloques <info> (idiomas), alcanza con el primero.
                "event" -> if (infoCount <= 1) event = p.nextText().trim()
                "severity" -> if (infoCount <= 1) severity = p.nextText().trim()
                "headline" -> if (infoCount <= 1) headline = p.nextText().trim()
                "description" -> if (infoCount <= 1) description = p.nextText().trim()
                "onset" -> if (infoCount <= 1) onset = p.nextText().trim()
                "expires" -> if (infoCount <= 1) expires = p.nextText().trim()
                "polygon" -> Geo.parsePolygon(p.nextText()).takeIf { it.size >= 3 }?.let(polygons::add)
            }
        }
        val name = event ?: return null
        return CapAlert(
            WeatherAlert(identifier, name, severity, headline, description, onset, expires),
            polygons,
            link,
        )
    }
}
