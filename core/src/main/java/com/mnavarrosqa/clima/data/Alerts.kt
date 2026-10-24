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
