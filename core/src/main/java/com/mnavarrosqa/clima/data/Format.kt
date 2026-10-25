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
    minutes < 55 -> tr("Lluvia en unos ${(minutes + 4) / 5 * 5} min", "Rain in about ${(minutes + 4) / 5 * 5} min")
    minutes < 90 -> tr("Lluvia en la próxima hora", "Rain within the hour")
    else -> tr("Lluvia en menos de 2 horas", "Rain in under 2 hours")
}

/** Cuánto hace que se bajó el pronóstico: "recién", "hace 12 min", "hace 3 h" o "el 23/10". */
fun ageText(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - epochMillis) / 60_000
    return when {
        minutes < 1 -> tr("recién", "just now")
        minutes < 60 -> tr("hace $minutes min", "$minutes min ago")
        minutes < 24 * 60 -> tr("hace ${minutes / 60} h", "${minutes / 60} h ago")
        else -> {
            val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            tr("el ${date.dayOfMonth}/${date.monthValue}", "on ${date.monthValue}/${date.dayOfMonth}")
        }
    }
}

fun updatedAt(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter())
