package com.mnavarrosqa.clima.data

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Cuentas de las tarjetas de detalle: lo que muestra cada una además del dato de ahora. Viven en
 * el núcleo para poder probarlas sin Android.
 */

/** Franja con UV de 3 o más: [from] a [until] (fin de la última hora); [ongoing] si ya empezó. */
data class UvWindow(val from: LocalDateTime, val until: LocalDateTime, val ongoing: Boolean)

/** Cuándo hace falta cuidarse del sol en lo que queda de hoy (UV de [threshold] o más). */
fun WeatherSnapshot.uvWindow(threshold: Double = 3.0): UvWindow? {
    val now = nowAtPlace()
    val today = now.toLocalDate()
    val thisHour = now.truncatedTo(ChronoUnit.HOURS)
    // Solo desde la hora actual: el pronóstico guardado puede traer horas que ya pasaron.
    val strong = hours.filter {
        val time = it.dateTime
        time.toLocalDate() == today && !time.isBefore(thisHour) && (it.uvIndex ?: 0.0) >= threshold
    }
    if (strong.isEmpty()) return null
    val first = strong.first().dateTime
    val last = strong.last().dateTime.plusHours(1)
    return UvWindow(first, last, ongoing = !first.isAfter(thisHour))
}

/** Escala de Beaufort en palabras, a partir del viento en km/h. */
fun beaufortLabel(kmh: Double): String = when {
    kmh < 1 -> tr("Calma", "Calm")
    kmh < 6 -> tr("Ventolina", "Light air")
    kmh < 12 -> tr("Brisa muy débil", "Light breeze")
    kmh < 20 -> tr("Brisa débil", "Gentle breeze")
    kmh < 29 -> tr("Brisa moderada", "Moderate breeze")
    kmh < 39 -> tr("Brisa fresca", "Fresh breeze")
    kmh < 50 -> tr("Brisa fuerte", "Strong breeze")
    kmh < 62 -> tr("Viento fuerte", "Near gale")
    kmh < 75 -> tr("Temporal", "Gale")
    kmh < 89 -> tr("Temporal fuerte", "Strong gale")
    kmh < 103 -> tr("Temporal muy fuerte", "Storm")
    kmh < 118 -> tr("Tempestad", "Violent storm")
    else -> tr("Huracán", "Hurricane")
}

/** Sensación térmica en las próximas [count] horas: la hora de la más baja y de la más alta. */
fun WeatherSnapshot.feelsLikeRange(count: Int = 12): Pair<Hour, Hour>? {
    val upcoming = upcomingHours(count).filter { it.feelsLike != null }
    if (upcoming.size < 2) return null
    return upcoming.minBy { it.feelsLike!! } to upcoming.maxBy { it.feelsLike!! }
}

/** Cómo se siente el aire según el punto de rocío. */
fun humidityFeel(dewPoint: Double): String = when {
    dewPoint < 10 -> tr("Seco", "Dry")
    dewPoint < 16 -> tr("Agradable", "Comfortable")
    dewPoint < 20 -> tr("Pesado", "Muggy")
    else -> tr("Muy húmedo", "Oppressive")
}

/** Rocío o escarcha: el aire llega al punto de rocío esta noche o a la madrugada. */
data class DewForecast(val frost: Boolean, val at: LocalDateTime)

fun WeatherSnapshot.dewTonight(): DewForecast? {
    val now = nowAtPlace()
    // Hasta las 10 de la mañana siguiente (o de hoy, si es de madrugada).
    val limit = (if (now.hour < 10) now.toLocalDate() else now.toLocalDate().plusDays(1)).atTime(10, 0)
    val hour = hours.firstOrNull { h ->
        val time = h.dateTime
        val dew = h.dewPoint ?: return@firstOrNull false
        !time.isBefore(now.truncatedTo(ChronoUnit.HOURS)) && time.isBefore(limit) && !h.isDay && h.temperature - dew <= 1.0 && (h.windSpeed ?: 0.0) < 15
    } ?: return null
    return DewForecast(frost = hour.temperature <= 0.5, at = hour.dateTime)
}

/** Niebla: a qué hora se levanta (si hay ahora) o a qué hora llega en las próximas 12 horas. */
data class FogForecast(val now: Boolean, val at: LocalDateTime?)

fun WeatherSnapshot.fog(): FogForecast? {
    val upcoming = upcomingHours(12)
    val foggyNow = (current.visibility ?: Double.MAX_VALUE) < 1000
    return if (foggyNow) {
        FogForecast(now = true, at = upcoming.drop(1).firstOrNull { (it.visibility ?: 0.0) >= 2000 }?.dateTime)
    } else {
        upcoming.drop(1).firstOrNull { (it.visibility ?: Double.MAX_VALUE) < 1000 }?.let { FogForecast(now = false, at = it.dateTime) }
    }
}

/** Presión de 12 horas atrás a 12 adelante, hora por hora, y en qué posición está ahora. */
fun WeatherSnapshot.pressureSeries(): Pair<List<Double>, Int>? {
    val now = nowAtPlace().truncatedTo(ChronoUnit.HOURS)
    val all = (pastHours + hours).filter { it.pressure != null && !it.dateTime.isBefore(now.minusHours(12)) && !it.dateTime.isAfter(now.plusHours(12)) }
    if (all.size < 4) return null
    val index = all.indexOfFirst { !it.dateTime.isBefore(now) }.takeIf { it >= 0 } ?: return null
    return all.map { it.pressure!! } to index
}

/** Si el índice de calidad del aire de las próximas horas mejora (-1), empeora (1) o sigue igual (0). */
fun AirQuality.trend(): Int? {
    val values = upcomingAqi.filterNotNull()
    if (values.size < 4) return null
    val change = values.takeLast(3).average() - values.first()
    return when {
        change >= 10 -> 1
        change <= -10 -> -1
        else -> 0
    }
}

/** El contaminante que más pesa, comparado con el límite de "moderado" del índice europeo. */
fun AirQuality.mainPollutant(): String? = listOfNotNull(
    pm25?.let { "PM2,5" to it / 25 },
    pm10?.let { "PM10" to it / 50 },
    nitrogenDioxide?.let { "NO₂" to it / 120 },
    ozone?.let { tr("ozono", "ozone") to it / 130 },
).maxByOrNull { it.second }?.first

/** Polen alto o muy alto, en palabras ("pastos: alto"), o null si no hay o no se mide. */
fun AirQuality.pollenText(): String? {
    fun level(value: Double?) = when {
        value == null || value < 20 -> null
        value < 50 -> tr("moderado", "moderate")
        value < 200 -> tr("alto", "high")
        else -> tr("muy alto", "very high")
    }
    return listOfNotNull(
        level(grassPollen)?.let { tr("pastos", "grass") + " " + it },
        level(treePollen)?.let { tr("árboles", "trees") + " " + it },
        level(weedPollen)?.let { tr("malezas", "weeds") + " " + it },
    ).joinToString(", ").ifEmpty { null }
}

/** "¿Qué me pongo?" para las próximas 12 horas: abrigo, paraguas, protector y anteojos. */
fun WeatherSnapshot.outfit(): List<String> {
    val upcoming = upcomingHours(12)
    if (upcoming.isEmpty()) return emptyList()
    val coldest = upcoming.minOf { it.feelsLike ?: it.temperature }
    val warmest = upcoming.maxOf { it.feelsLike ?: it.temperature }
    val rain = upcoming.maxOf { it.precipitationProbability ?: 0 }
    val uv = upcoming.maxOf { it.uvIndex ?: 0.0 }
    val wind = upcoming.maxOf { it.windGusts ?: it.windSpeed ?: 0.0 }
    return buildList {
        add(
            when {
                coldest < 5 -> tr("🧥 Abrigo grueso", "🧥 Heavy coat")
                coldest < 12 -> tr("🧥 Campera", "🧥 Jacket")
                coldest < 18 -> tr("🧶 Un buzo", "🧶 A sweater")
                warmest >= 28 -> tr("🩳 Ropa liviana", "🩳 Light clothes")
                else -> tr("👕 Remera", "👕 T-shirt")
            },
        )
        if (coldest < 18 && warmest >= 24) add(tr("🌡️ Abrigo para sacarse", "🌡️ Layers"))
        if (rain >= 50) add(tr("☂️ Paraguas", "☂️ Umbrella"))
        if (wind >= 50 && rain < 50) add(tr("💨 Rompevientos", "💨 Windbreaker"))
        if (uv >= 6) add(tr("🧴 Protector", "🧴 Sunscreen"))
        if (uv >= 3) add(tr("🕶️ Anteojos", "🕶️ Sunglasses"))
    }
}

/** Diferencia con ayer a la misma hora, en grados (positiva si hoy hace más calor). */
fun WeatherSnapshot.versusYesterday(): Double? {
    val hour = upcomingHours(1).firstOrNull() ?: return null
    val yesterday = yesterdayAt(listOf(hour)).firstOrNull() ?: return null
    return current.temperature - yesterday
}

/** Texto de la comparación con ayer (en las unidades elegidas). */
fun versusYesterdayText(difference: Double): String {
    val shown = difference.degreesDelta()
    return when {
        shown == "0°" -> tr("Igual que ayer a esta hora", "Same as yesterday at this time")
        difference > 0 -> tr("$shown más cálido que ayer", "$shown warmer than yesterday")
        else -> tr("$shown más fresco que ayer", "$shown cooler than yesterday")
    }
}

/** Resumen de un día comparado con el anterior: "Más fresco y con viento". */
fun daySummary(day: Day, previous: Day?): String {
    val parts = mutableListOf<String>()
    previous?.let {
        val change = day.max - it.max
        parts += when {
            change >= 3 -> tr("más cálido", "warmer")
            change <= -3 -> tr("más fresco", "cooler")
            else -> tr("temperatura parecida", "similar temperatures")
        }
    }
    val rain = day.precipitationProbability ?: 0
    when {
        rain >= 60 -> parts += tr("con lluvia", "rainy")
        rain >= 30 -> parts += tr("con chance de lluvia", "chance of rain")
    }
    if ((day.windGustsMax ?: 0.0) >= 50 || (day.windMax ?: 0.0) >= 35) parts += tr("con viento", "windy")
    if ((day.uvIndex ?: 0.0) >= 8) parts += tr("sol fuerte", "strong sun")
    if (parts.isEmpty()) return ""
    val text = if (parts.size > 1) parts.dropLast(1).joinToString(", ") + tr(" y ", " and ") + parts.last() else parts.first()
    val base = if (previous != null) tr("Respecto del día anterior: ", "Compared with the day before: ") else ""
    return base + text.replaceFirstChar { it.uppercase() }
}
