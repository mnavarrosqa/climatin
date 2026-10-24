package com.mnavarrosqa.clima.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Lo que guardamos en caché y muestran la app y los widgets. */
@Serializable
data class WeatherSnapshot(
    val place: String?,
    val latitude: Double,
    val longitude: Double,
    val utcOffsetSeconds: Int,
    val fetchedAt: Long,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
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
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
}
