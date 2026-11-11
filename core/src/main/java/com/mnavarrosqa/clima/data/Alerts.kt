package com.mnavarrosqa.clima.data

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/** Alerta oficial (CAP) que afecta a un lugar. */
@Serializable
data class WeatherAlert(
    val id: String,
    val event: String,
    val severity: String?,
    val headline: String?,
    val description: String?,
    val onset: String?,
    val expires: String?,
) {
    /** "Extreme" y "Severe" se muestran como peligro; el resto como aviso. */
    val isSevere: Boolean get() = severity.equals("Extreme", true) || severity.equals("Severe", true)

    /** Ya venció (si el SMN no responde, las guardadas pueden quedar viejas). Sin fecha, no vence. */
    fun isExpired(now: java.time.OffsetDateTime = java.time.OffsetDateTime.now()): Boolean =
        expires?.let { runCatching { java.time.OffsetDateTime.parse(it).isBefore(now) }.getOrNull() } ?: false

    /**
     * Lo que dice la alerta, sin su id: el SMN manda una por zona, con ids distintos y el mismo
     * texto. Sirve para no repetirlas y para seguirlas aunque cambie cuál de las dos queda.
     */
    val textSignature: String
        get() = listOf(event.trim().lowercase(), severity.orEmpty(), headline?.trim().orEmpty(), description?.trim().orEmpty())
            .joinToString("\u0001")
}

/**
 * Saca las alertas repetidas (mismo texto en dos zonas). De cada grupo queda la que vence más
 * tarde, y entre iguales la de id menor: así no depende del orden en que las manda el SMN.
 */
fun List<WeatherAlert>.withoutRepeats(): List<WeatherAlert> {
    fun end(alert: WeatherAlert): Long = alert.expires
        ?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
        ?: Long.MAX_VALUE
    val best = groupBy { it.textSignature }.mapValues { (_, same) ->
        same.sortedWith(compareByDescending<WeatherAlert> { end(it) }.thenBy { it.id }).first()
    }
    // En el orden en que llegaron.
    return map { it.textSignature }.distinct().mapNotNull { best[it] }
}

object Geo {
    /** Polígono CAP: "lat,lon lat,lon ..." */
    fun parsePolygon(text: String): List<Pair<Double, Double>> =
        text.trim().split(Regex("\\s+")).mapNotNull { pair ->
            val parts = pair.split(',')
            if (parts.size < 2) return@mapNotNull null
            val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            lat to lon
        }

    /** Punto dentro de polígono por cruce de rayos. */
    fun contains(polygon: List<Pair<Double, Double>>, latitude: Double, longitude: Double): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val (latI, lonI) = polygon[i]
            val (latJ, lonJ) = polygon[j]
            val crosses = (latI > latitude) != (latJ > latitude) &&
                longitude < (lonJ - lonI) * (latitude - latI) / (latJ - latI) + lonI
            if (crosses) inside = !inside
            j = i
        }
        return inside
    }

    fun isInArgentina(latitude: Double, longitude: Double): Boolean =
        latitude in -56.0..-21.0 && longitude in -74.0..-53.0

    /** Coordenadas de tesela (Web Mercator) con fracción, para los mapas del radar. */
    fun tileX(longitude: Double, zoom: Int): Double = (longitude + 180.0) / 360.0 * (1 shl zoom)

    fun tileY(latitude: Double, zoom: Int): Double {
        val rad = latitude * PI / 180.0
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * (1 shl zoom)
    }
}
