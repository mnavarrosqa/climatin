package com.mnavarrosqa.clima.data

import java.time.Duration
import java.time.LocalDateTime

/** Qué tan fuerte llueve, en mm por hora (umbrales habituales de los servicios meteorológicos). */
enum class RainIntensity(private val es: String, private val en: String) {
    LIGHT("débil", "light"),
    MODERATE("moderada", "moderate"),
    HEAVY("fuerte", "heavy"),
    ;

    val label: String get() = tr(es, en)

    companion object {
        fun of(mmPerHour: Double): RainIntensity = when {
            mmPerHour >= 7.6 -> HEAVY
            mmPerHour >= 2.5 -> MODERATE
            else -> LIGHT
        }
    }
}

/** Lo que va a pasar con la lluvia en las próximas horas. */
sealed interface RainOutlook {
    /** Empieza en [inMinutes]; dura [lastsMinutes] (null si sigue más allá de lo que se sabe). */
    data class Starts(val inMinutes: Int, val lastsMinutes: Int?, val intensity: RainIntensity) : RainOutlook

    /** Está lloviendo y para en [inMinutes]. */
    data class Stops(val inMinutes: Int) : RainOutlook
}

/** Un tramo del pronóstico con su lluvia en mm por hora. */
private data class RainSlot(val start: LocalDateTime, val minutes: Long, val mmPerHour: Double)

/**
 * Mira la lluvia de cada 15 minutos (2 horas) y después la de cada hora (hasta 6 horas), y dice si
 * empieza o para dentro de [withinMinutes]. En Open-Meteo la lluvia de cada hora (y de cada cuarto
 * de hora) es la del tramo *anterior* a esa hora: el tramo de las 15:00 es de 14:00 a 15:00. Una hora sin dato de milímetros pero con 60% o más
 * de probabilidad cuenta como lluvia débil.
 */
fun WeatherSnapshot.rainOutlook(withinMinutes: Int = 120): RainOutlook? {
    val now = nowAtPlace()
    val slots = buildList {
        // Solo los tramos que terminan después de ahora.
        minutely.filter { it.dateTime.isAfter(now) }.forEach {
            add(RainSlot(it.dateTime.minusMinutes(15), 15, it.precipitation * 4))
        }
        val coveredUntil = lastOrNull()?.let { it.start.plusMinutes(it.minutes) } ?: now.minusMinutes(1)
        upcomingHours(8).filter { it.dateTime.isAfter(coveredUntil) && it.dateTime.isAfter(now) }.forEach { hour ->
            val mm = hour.precipitation ?: if ((hour.precipitationProbability ?: 0) >= 60) 0.5 else 0.0
            val wet = mm >= 0.2 || (hour.precipitation == null && (hour.precipitationProbability ?: 0) >= 60)
            add(RainSlot(maxOf(hour.dateTime.minusHours(1), coveredUntil), 60, if (wet) mm.coerceAtLeast(0.2) else 0.0))
        }
    }.sortedBy { it.start }
    fun wet(slot: RainSlot) = slot.mmPerHour >= 0.4 || (slot.minutes == 60L && slot.mmPerHour >= 0.2)
    fun minutesUntil(time: LocalDateTime) = ((Duration.between(now, time).seconds + 30) / 60).toInt().coerceAtLeast(0)

    val rainingNow = current.precipitation >= 0.1 || WeatherCodes.kind(current.code).let {
        it == WeatherKind.RAIN || it == WeatherKind.DRIZZLE || it == WeatherKind.STORM
    }
    if (rainingNow) {
        // Solo con el detalle de 15 minutos: la lluvia por hora es demasiado gruesa para "para en 20 min".
        val dry = slots.firstOrNull { it.minutes == 15L && it.start.isAfter(now.minusMinutes(1)) && !wet(it) } ?: return null
        val inMinutes = minutesUntil(dry.start)
        return if (inMinutes <= withinMinutes) RainOutlook.Stops(inMinutes.coerceAtLeast(5)) else null
    }
    val startIndex = slots.indexOfFirst { wet(it) && it.start.plusMinutes(it.minutes).isAfter(now) }
    if (startIndex < 0) return null
    val start = slots[startIndex]
    val inMinutes = minutesUntil(start.start)
    if (inMinutes > withinMinutes) return null
    val span = slots.drop(startIndex).takeWhile { wet(it) }
    val endIndex = startIndex + span.size
    val lasts = if (endIndex < slots.size) Duration.between(start.start, slots[endIndex].start).toMinutes().toInt() else null
    return RainOutlook.Starts(inMinutes.coerceAtLeast(5), lasts, RainIntensity.of(span.maxOf { it.mmPerHour }))
}

/** "Lluvia moderada en unos 20 min, por 1 h 30 min" o "Para de llover en unos 15 min". */
fun rainOutlookText(outlook: RainOutlook): String = when (outlook) {
    is RainOutlook.Stops -> when {
        outlook.inMinutes <= 20 -> tr("Para de llover en unos minutos", "Rain stops in a few minutes")
        else -> tr("Para de llover en unos ${roundTo5(outlook.inMinutes)} min", "Rain stops in about ${roundTo5(outlook.inMinutes)} min")
    }
    is RainOutlook.Starts -> {
        val what = tr("Lluvia ${outlook.intensity.label}", "${outlook.intensity.label.replaceFirstChar { it.uppercase() }} rain")
        val start = when {
            outlook.inMinutes <= 20 -> tr("en unos minutos", "in a few minutes")
            outlook.inMinutes < 90 -> tr("en unos ${roundTo5(outlook.inMinutes)} min", "in about ${roundTo5(outlook.inMinutes)} min")
            else -> tr("en unas ${(outlook.inMinutes + 30) / 60} horas", "in about ${(outlook.inMinutes + 30) / 60} hours")
        }
        "$what $start"
    }
}

/** Cuánto dura la lluvia que viene, para el texto chico: "Por 1 h 30 min" o "Por un buen rato". */
fun rainDurationText(outlook: RainOutlook.Starts): String {
    val minutes = outlook.lastsMinutes ?: return tr("Por un buen rato", "For a good while")
    return when {
        minutes < 60 -> tr("Por unos $minutes min", "For about $minutes min")
        minutes % 60 == 0 -> tr("Por ${minutes / 60} h", "For ${minutes / 60} h")
        else -> tr("Por ${minutes / 60} h ${minutes % 60} min", "For ${minutes / 60} h ${minutes % 60} min")
    }
}

private fun roundTo5(minutes: Int) = (minutes + 4) / 5 * 5
