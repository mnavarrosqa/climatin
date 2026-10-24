package com.mnavarrosqa.clima.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val spanish: Locale = Locale.forLanguageTag("es-AR")
private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val hourMinute12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", spanish)

private fun timeFormatter(): DateTimeFormatter = if (Units.current.use24h) hourMinute else hourMinute12

fun shortHour(dateTime: LocalDateTime): String = dateTime.format(timeFormatter())

fun timeOf(isoDateTime: String): String = shortHour(LocalDateTime.parse(isoDateTime))

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy"
    today.plusDays(1) -> "Mañana"
    else -> weekday(date.dayOfWeek)
}

/** "Sáb", "Dom"... sin el punto de la abreviatura. */
fun weekday(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, spanish)
        .trimEnd('.')
        .replaceFirstChar { it.titlecase(spanish) }

/** "Sábado 24 de oct." como el "De un vistazo" del Pixel. */
fun longDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMM", spanish))
        .replaceFirstChar { it.titlecase(spanish) }

fun rainSoonText(minutes: Int): String = when {
    minutes <= 20 -> "Lluvia en unos minutos"
    minutes < 60 -> "Lluvia en unos ${(minutes + 4) / 5 * 5} min"
    minutes < 90 -> "Lluvia en la próxima hora"
    else -> "Lluvia en menos de 2 horas"
}

fun updatedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter())
