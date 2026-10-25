package com.mnavarrosqa.clima.data

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Lo que guardamos en caché por lugar y muestran la app y los widgets. */
@Serializable
data class WeatherSnapshot(
    val placeId: String = CURRENT_PLACE_ID,
    val place: String?,
    val latitude: Double,
    val longitude: Double,
    val utcOffsetSeconds: Int,
    val fetchedAt: Long,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val minutely: List<Minutely> = emptyList(),
    val airQuality: AirQuality? = null,
    val alerts: List<WeatherAlert> = emptyList(),
    /** Zona horaria IANA del lugar; null en pronósticos guardados por versiones anteriores. */
    val timezone: String? = null,
    /** Horas que ya pasaron desde ayer a las 0 (solo temperatura), para comparar con ayer. */
    val pastHours: List<Hour> = emptyList(),
) {
    /** Temperatura de ayer a la misma hora de cada una de [hours] (null si no se sabe). */
    fun yesterdayAt(hours: List<Hour>): List<Double?> {
        val known = (pastHours + this.hours).associate { it.time to it.temperature }
        return hours.map { known[it.dateTime.minusDays(1).toString()] }
    }

    /**
     * Hora actual en el lugar del pronóstico, no en el teléfono. Con la zona IANA sigue bien
     * aunque cambie el horario de verano después de bajar el pronóstico; si no, el desfase fijo.
     */
    fun nowAtPlace(): LocalDateTime = LocalDateTime.now(zone)

    // Se arma una vez: los widgets y los avisos piden la hora del lugar muchas veces.
    private val zone: java.time.ZoneId by lazy(LazyThreadSafetyMode.PUBLICATION) {
        timezone?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
    }

    /** Horas desde la actual. La caché puede tener un rato, así que se recorta al mostrar. */
    fun upcomingHours(count: Int): List<Hour> {
        val from = nowAtPlace().truncatedTo(ChronoUnit.HOURS)
        // Las horas vienen en orden: se busca la primera y se corta ahí, sin recorrerlas todas.
        val start = hours.indexOfFirst { !it.dateTime.isBefore(from) }
        if (start < 0) return emptyList()
        return hours.subList(start, minOf(hours.size, start + count)).toList()
    }

    /** Presión hace unas [hoursAgo] horas, para saber si sube o baja. */
    fun pressureHoursAgo(hoursAgo: Long = 3): Double? {
        val target = nowAtPlace().truncatedTo(ChronoUnit.HOURS).minusHours(hoursAgo)
        return (pastHours + hours).firstOrNull { it.dateTime == target }?.pressure
    }

    fun upcomingDays(count: Int): List<Day> {
        val today = nowAtPlace().toLocalDate()
        return days.filter { !it.localDate.isBefore(today) }.take(count)
    }

    fun today(): Day? = upcomingDays(1).firstOrNull()

    /** Ayer (Open-Meteo lo manda con past_days=1), para comparar. */
    fun yesterday(): Day? {
        val date = nowAtPlace().toLocalDate().minusDays(1)
        return days.firstOrNull { it.localDate == date }
    }

    /** Horas de un día; para hoy, solo desde la hora actual. */
    fun hoursOf(date: LocalDate): List<Hour> {
        val from = nowAtPlace().truncatedTo(ChronoUnit.HOURS)
        return hours.filter { val time = it.dateTime; time.toLocalDate() == date && !time.isBefore(from) }
    }

    /**
     * Minutos hasta que empiece a llover, si va a llover en las próximas dos horas y ahora no llueve.
     * Usa el pronóstico cada 15 minutos y, si no hay, la probabilidad por hora.
     */
    fun minutesUntilRain(): Int? {
        if (current.precipitation >= 0.1 || isRainy(current.code)) return null
        val now = nowAtPlace()
        val limit = now.plusHours(2)
        // Cada valor es del tramo anterior a su hora: el tramo empieza 15 minutos (o una hora) antes.
        val fromMinutely = minutely.firstOrNull {
            it.precipitation >= 0.1 && it.dateTime.isAfter(now) && it.dateTime.minusMinutes(15).isBefore(limit)
        }
        if (fromMinutely != null) {
            return Duration.between(now, fromMinutely.dateTime.minusMinutes(15)).toMinutes().toInt().coerceAtLeast(5)
        }
        // La probabilidad por hora solo si no hay detalle cada 15 minutos: si lo hay y dice seco,
        // no se contradice con "lluvia en la próxima hora".
        if (minutely.isNotEmpty()) return null
        val fromHourly = upcomingHours(4).drop(1).firstOrNull {
            (it.precipitationProbability ?: 0) >= 60 && it.dateTime.minusHours(1).isBefore(limit)
        } ?: return null
        return Duration.between(now, fromHourly.dateTime.minusHours(1)).toMinutes().toInt().coerceAtLeast(5)
    }

    private fun isRainy(code: Int) = WeatherCodes.kind(code).isWet
}

@Serializable
data class Current(
    val time: String,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val isDay: Boolean,
    val code: Int,
    val windSpeed: Double,
    val precipitation: Double,
    val pressure: Double? = null,
    val visibility: Double? = null,
    val uvIndex: Double? = null,
    val windDirection: Double? = null,
    val windGusts: Double? = null,
)

@Serializable
data class Hour(
    val time: String,
    val temperature: Double,
    val code: Int,
    val precipitationProbability: Int?,
    val isDay: Boolean,
    val feelsLike: Double? = null,
    val windSpeed: Double? = null,
    val precipitation: Double? = null,
    val pressure: Double? = null,
) {
    // Se lee una vez por hora: los gráficos, widgets y avisos la consultan una y otra vez.
    // Al ser delegada no se guarda en el archivo ni cuenta para equals.
    val dateTime: LocalDateTime by lazy(LazyThreadSafetyMode.PUBLICATION) { LocalDateTime.parse(time) }
}

@Serializable
data class Day(
    val date: String,
    val code: Int,
    val max: Double,
    val min: Double,
    val sunrise: String,
    val sunset: String,
    val uvIndex: Double?,
    val precipitationProbability: Int?,
    val precipitationSum: Double? = null,
    val windMax: Double? = null,
) {
    val localDate: LocalDate by lazy(LazyThreadSafetyMode.PUBLICATION) { LocalDate.parse(date) }
}

@Serializable
data class Minutely(val time: String, val precipitation: Double) {
    val dateTime: LocalDateTime by lazy(LazyThreadSafetyMode.PUBLICATION) { LocalDateTime.parse(time) }
}

@Serializable
data class AirQuality(val europeanAqi: Int?, val pm25: Double?)

/** Si llueve, llovizna, hay tormenta o nieva: la misma idea de "mojado" en todos los avisos. */
val WeatherKind.isWet: Boolean
    get() = this == WeatherKind.DRIZZLE || this == WeatherKind.RAIN || this == WeatherKind.STORM || this == WeatherKind.SNOW
