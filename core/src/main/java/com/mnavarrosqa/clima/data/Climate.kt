package com.mnavarrosqa.clima.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "¿Normal para la época?" y "Lluvia caída": cuentas con datos de días pasados. Viven en el
 * núcleo para poder probarlas sin Android; los pedidos están en [OpenMeteo].
 */

/** Un día ya pasado: máxima, mínima (°C) y lluvia (mm). Se guarda en la caché del clima. */
@Serializable
data class ClimateDay(val date: String, val max: Double? = null, val min: Double? = null, val precipitation: Double? = null) {
    val localDate: LocalDate by lazy(LazyThreadSafetyMode.PUBLICATION) { LocalDate.parse(date) }
}

/** Respuesta diaria del archivo histórico (y del pronóstico con solo `precipitation_sum`). */
@Serializable
data class ArchiveResponse(val daily: ArchiveDailyDto? = null)

@Serializable
data class ArchiveDailyDto(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val max: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val min: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitation: List<Double?> = emptyList(),
)

fun ArchiveResponse.toClimateDays(): List<ClimateDay> {
    val d = daily ?: return emptyList()
    return d.time.indices.map { i -> ClimateDay(d.time[i], d.max.getOrNull(i), d.min.getOrNull(i), d.precipitation.getOrNull(i)) }
}

/** Lo normal para unos días del año: promedios de [years] años. La lluvia es por día. */
data class ClimateNormal(val max: Double, val min: Double, val precipitationPerDay: Double?, val years: Int)

/**
 * Días entre [date] y la misma fecha de [target] en el año de [date] (o el anterior o el
 * siguiente, así el 30/12 queda a 2 días del 1/1). El 29/2 cae en el 28/2 de los años comunes.
 */
internal fun daysFromCalendarDate(date: LocalDate, target: LocalDate): Long =
    (date.year - 1..date.year + 1).minOf { year ->
        val day = minOf(target.dayOfMonth, java.time.YearMonth.of(year, target.month).lengthOfMonth())
        abs(ChronoUnit.DAYS.between(date, LocalDate.of(year, target.month, day)))
    }

/**
 * Promedio de los días de otros años a [window] días o menos de la fecha de [today]. Pide un
 * mínimo de días con dato: con pocos el promedio dice poco. Los días de este último tramo no
 * cuentan (no se compara hoy consigo mismo).
 */
fun normalFor(days: List<ClimateDay>, today: LocalDate, window: Int = 7, minDays: Int = 20): ClimateNormal? {
    val near = days.filter { it.localDate.isBefore(today.minusDays(window.toLong())) && daysFromCalendarDate(it.localDate, today) <= window }
    val maxes = near.mapNotNull { it.max }
    val mins = near.mapNotNull { it.min }
    if (maxes.size < minDays || mins.size < minDays) return null
    val rain = near.mapNotNull { it.precipitation }
    return ClimateNormal(
        max = maxes.average(),
        min = mins.average(),
        precipitationPerDay = rain.takeIf { it.size >= minDays }?.average(),
        // Por "temporada": una ventana que cruza el año nuevo es de la misma.
        years = near.map { it.localDate.plusDays(window.toLong()).year }.distinct().size,
    )
}

/**
 * Días que hacen falta guardar para que la caché sirva [days] días más: los de la ventana más
 * ese margen. Así no se guardan diez años enteros.
 */
fun keepForCache(days: List<ClimateDay>, from: LocalDate, window: Int = 7, keepDays: Int = 31): List<ClimateDay> {
    val middle = from.plusDays(keepDays / 2L)
    val reach = keepDays / 2L + window + 1
    return days.filter { daysFromCalendarDate(it.localDate, middle) <= reach }
}

/** Nombre del mes en minúscula ("octubre"); en inglés con mayúscula ("October"). */
fun monthName(date: LocalDate): String {
    val name = date.month.getDisplayName(TextStyle.FULL_STANDALONE, Lang.locale)
    // FULL_STANDALONE puede venir como número en algunos JDK: se usa el de formato.
    val text = if (name.any { it.isLetter() }) name else date.month.getDisplayName(TextStyle.FULL, Lang.locale)
    return if (Lang.isSpanish) text.lowercase(Lang.locale) else text.replaceFirstChar { it.titlecase(Lang.locale) }
}

/** Tarjeta "Para la época": diferencia con la máxima normal y una frase. */
data class SeasonText(val value: String, val note: String)

/**
 * La máxima de hoy contra la normal. Con menos de 2° de diferencia se dice "normal": el
 * promedio de diez años tiene su margen. El signo sale del número ya redondeado (en °F puede no
 * coincidir con °C).
 */
fun seasonText(todayMax: Double, normal: ClimateNormal, date: LocalDate): SeasonText {
    val difference = todayMax - normal.max
    val shown = difference.degreesDelta()
    val sign = if (shown == "0°") "" else if (difference > 0) "+" else "−"
    val month = monthName(date)
    val usual = tr("máx normal ${normal.max.degrees()}", "normal high ${normal.max.degrees()}")
    val note = when {
        difference >= 6 -> tr("Mucho más cálido que lo normal para $month", "Much warmer than normal for $month")
        difference >= 2 -> tr("Más cálido que lo normal para $month", "Warmer than normal for $month")
        difference <= -6 -> tr("Mucho más frío que lo normal para $month", "Much colder than normal for $month")
        difference <= -2 -> tr("Más frío que lo normal para $month", "Colder than normal for $month")
        else -> tr("Normal para $month", "Normal for $month")
    }
    return SeasonText(sign + shown, "$note ($usual)")
}

/** Milímetros: con decimal si es poco ("2.5 mm"), redondeado si es mucho ("34 mm"). */
fun mmText(mm: Double): String = when {
    mm >= 10 -> "${mm.roundToInt()} mm"
    else -> String.format(Locale.US, "%.1f", mm).removeSuffix(".0") + " mm"
}

/**
 * Lo que llovió hoy hasta ahora. Cada hora de Open-Meteo trae lo de la hora anterior: la de las
 * 14 ya tiene lo de 13 a 14 aunque esté en [WeatherSnapshot.hours]. Por eso cuentan todas hasta
 * la hora actual inclusive, también las de [WeatherSnapshot.hours] que pasaron desde que se bajó.
 */
fun WeatherSnapshot.rainSoFarToday(): Double? {
    val now = nowAtPlace().truncatedTo(ChronoUnit.HOURS)
    val today = now.toLocalDate()
    val values = (pastHours + hours).distinctBy { it.time }
        .filter { !it.dateTime.isAfter(now) && it.dateTime.minusHours(1).toLocalDate() == today }
        .mapNotNull { it.precipitation }
    return values.takeIf { it.isNotEmpty() }?.sum()
}

/** Lluvia de los 7 días anteriores a [today] (sin hoy). Null si falta alguno. */
fun weekRain(days: List<ClimateDay>, today: LocalDate): Double? {
    val week = (1..7L).map { back -> days.firstOrNull { it.localDate == today.minusDays(back) }?.precipitation ?: return null }
    return week.sum()
}

/** Tarjeta "Lluvia caída". */
data class RainfallText(val value: String, val note: String)

/**
 * Lo que llovió ayer, en la semana y hoy hasta ahora (mm; null si no se sabe). Null si no hay
 * nada que decir: sin lluvia ayer y sin el dato de la semana no vale una tarjeta.
 */
fun rainfallText(yesterday: Double?, week: Double?, today: Double?): RainfallText? {
    val wet = 0.1
    val todayPart = today?.takeIf { it >= wet }?.let { tr("hoy van ${mmText(it)}", "${mmText(it)} so far today") }
    return when {
        yesterday != null && yesterday >= wet -> RainfallText(
            mmText(yesterday),
            listOfNotNull(
                tr("Ayer", "Yesterday"),
                week?.takeIf { it >= yesterday }?.let { tr("${mmText(it)} en 7 días", "${mmText(it)} in 7 days") },
                todayPart,
            ).joinToString(" · "),
        )
        week != null && week >= wet -> RainfallText(
            mmText(week),
            listOfNotNull(tr("En los últimos 7 días", "In the last 7 days"), todayPart).joinToString(" · "),
        )
        today != null && today >= wet -> RainfallText(mmText(today), tr("Hoy hasta ahora", "Today so far"))
        week != null -> RainfallText(tr("Nada", "None"), tr("Sin lluvia en la semana", "No rain this week"))
        else -> null
    }
}

/** Lo que miran "Para la época" y "Lluvia caída"; cada parte puede faltar (sin red, sin datos). */
data class PastWeather(val normal: ClimateNormal?, val weekRain: Double?)

/** Caché en disco de los días de otros años para un lugar: [fetchedOn] es la fecha del lugar al pedirlos. */
@Serializable
data class ClimateCache(val latitude: Double, val longitude: Double, val fetchedOn: String, val days: List<ClimateDay>)
