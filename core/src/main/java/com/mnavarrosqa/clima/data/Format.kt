package com.mnavarrosqa.clima.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val hourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun timeFormatter(): DateTimeFormatter =
    if (Units.current.use24h) hourMinute else DateTimeFormatter.ofPattern("h:mm a", Lang.locale)

fun shortHour(dateTime: LocalDateTime): String = dateTime.format(timeFormatter())

fun timeOf(isoDateTime: String): String = shortHour(LocalDateTime.parse(isoDateTime))

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> tr("Hoy", "Today")
    today.plusDays(1) -> tr("Mañana", "Tomorrow")
    else -> weekday(date.dayOfWeek)
}

/** "Sáb", "Dom"... sin el punto de la abreviatura. */
fun weekday(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, Lang.locale)
        .trimEnd('.')
        .replaceFirstChar { it.titlecase(Lang.locale) }

/** "Sábado 24 de oct." como el "De un vistazo" del Pixel ("Saturday, Oct 24" en inglés). */
fun longDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(tr("EEEE d 'de' MMM", "EEEE, MMM d"), Lang.locale))
        .replaceFirstChar { it.titlecase(Lang.locale) }

fun rainSoonText(minutes: Int): String = when {
    minutes <= 20 -> tr("Lluvia en unos minutos", "Rain in a few minutes")
    minutes < 60 -> tr("Lluvia en unos ${(minutes + 4) / 5 * 5} min", "Rain in about ${(minutes + 4) / 5 * 5} min")
    minutes < 90 -> tr("Lluvia en la próxima hora", "Rain within the hour")
    else -> tr("Lluvia en menos de 2 horas", "Rain in under 2 hours")
}

fun updatedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter())
