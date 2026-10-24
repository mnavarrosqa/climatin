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
) {
    /** Hora actual en el lugar del pronóstico, no en el teléfono. */
    fun nowAtPlace(): LocalDateTime =
        LocalDateTime.now(ZoneOffset.ofTotalSeconds(utcOffsetSeconds))

    /** Horas desde la actual. La caché puede tener un rato, así que se recorta al mostrar. */
    fun upcomingHours(count: Int): List<Hour> {
        val from = nowAtPlace().truncatedTo(ChronoUnit.HOURS)
        return hours.filter { !it.dateTime.isBefore(from) }.take(count)
    }

    fun upcomingDays(count: Int): List<Day> {
        val today = nowAtPlace().toLocalDate()
        return days.filter { !it.localDate.isBefore(today) }.take(count)
    }

    fun today(): Day? = upcomingDays(1).firstOrNull()

    /** Horas de un día; para hoy, solo desde la hora actual. */
    fun hoursOf(date: LocalDate): List<Hour> {
        val from = nowAtPlace().truncatedTo(ChronoUnit.HOURS)
        return hours.filter { it.dateTime.toLocalDate() == date && !it.dateTime.isBefore(from) }
    }

    /**
     * Minutos hasta que empiece a llover, si va a llover en las próximas dos horas y ahora no llueve.
     * Usa el pronóstico cada 15 minutos y, si no hay, la probabilidad por hora.
     */
    fun minutesUntilRain(): Int? {
        if (current.precipitation >= 0.1 || isRainy(current.code)) return null
        val now = nowAtPlace()
        val limit = now.plusHours(2)
        val fromMinutely = minutely.firstOrNull {
            it.precipitation >= 0.1 && it.dateTime.isAfter(now.minusMinutes(15)) && it.dateTime.isBefore(limit)
        }
        if (fromMinutely != null) {
            return Duration.between(now, fromMinutely.dateTime).toMinutes().toInt().coerceAtLeast(5)
        }
        val fromHourly = upcomingHours(3).drop(1).firstOrNull {
            (it.precipitationProbability ?: 0) >= 60 && it.dateTime.isBefore(limit)
        } ?: return null
        return Duration.between(now, fromHourly.dateTime).toMinutes().toInt().coerceAtLeast(5)
    }

    private fun isRainy(code: Int) = WeatherCodes.kind(code).let {
        it == WeatherKind.RAIN || it == WeatherKind.STORM || it == WeatherKind.SNOW
    }
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
)

@Serializable
data class Hour(
    val time: String,
    val temperature: Double,
    val code: Int,
    val precipitationProbability: Int?,
    val isDay: Boolean,
) {
    val dateTime: LocalDateTime get() = LocalDateTime.parse(time)
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
    val localDate: LocalDate get() = LocalDate.parse(date)
}

@Serializable
data class Minutely(val time: String, val precipitation: Double) {
    val dateTime: LocalDateTime get() = LocalDateTime.parse(time)
}

@Serializable
data class AirQuality(val europeanAqi: Int?, val pm25: Double?)
