package com.mnavarrosqa.clima.data

import java.time.Duration
import java.time.LocalDateTime

/**
 * La frase del momento, al estilo "De un vistazo": de todo lo que pasa, elige lo más útil.
 * Orden: alertas, lluvia inminente, cuándo para de llover, lluvia más tarde, UV alto, viento
 * fuerte, cambio de temperatura de mañana, amanecer o atardecer cercanos y diferencia con ayer.
 */
object Insights {

    fun headline(snapshot: WeatherSnapshot): String? {
        val now = snapshot.nowAtPlace()
        snapshot.alerts.firstOrNull { !it.isExpired() }?.let { return tr("Alerta: ${it.event}", "Alert: ${it.event}") }
        snapshot.minutesUntilRain()?.let { return rainSoonText(it) }
        rainStops(snapshot, now)?.let { return it }
        rainLater(snapshot, now)?.let { return it }
        uvWarning(snapshot, now)?.let { return it }
        if (snapshot.current.windSpeed >= 40) return tr("Viento fuerte", "Strong wind") + ": ${windText(snapshot.current.windSpeed)}"
        tomorrowChange(snapshot)?.let { return it }
        sunEvent(snapshot, now)?.let { return it }
        return versusYesterday(snapshot)
    }

    /** Hoy con 3° o más de diferencia de máxima respecto de ayer. */
    private fun versusYesterday(snapshot: WeatherSnapshot): String? {
        val today = snapshot.today() ?: return null
        val yesterday = snapshot.yesterday() ?: return null
        val delta = today.max - yesterday.max
        if (kotlin.math.abs(delta) < 3) return null
        return if (delta > 0) {
            tr("Hoy ${delta.degreesDelta()} más cálido que ayer", "Today ${delta.degreesDelta()} warmer than yesterday")
        } else {
            tr("Hoy ${delta.degreesDelta()} más fresco que ayer", "Today ${delta.degreesDelta()} cooler than yesterday")
        }
    }

    private fun isWet(code: Int) = WeatherCodes.kind(code) in
        setOf(WeatherKind.DRIZZLE, WeatherKind.RAIN, WeatherKind.STORM, WeatherKind.SNOW)

    /** Si ahora llueve, a qué hora se espera que pare (dentro de 6 horas). */
    private fun rainStops(snapshot: WeatherSnapshot, now: LocalDateTime): String? {
        if (!isWet(snapshot.current.code) && snapshot.current.precipitation < 0.1) return null
        val dry = snapshot.upcomingHours(7).drop(1).firstOrNull {
            !isWet(it.code) && (it.precipitationProbability ?: 0) < 30
        } ?: return null
        return tr("Para de llover cerca de las", "Rain stops around") + " ${shortHour(dry.dateTime)}"
    }

    /** Lluvia probable más tarde hoy (en las próximas 12 horas). */
    private fun rainLater(snapshot: WeatherSnapshot, now: LocalDateTime): String? {
        val rainy = snapshot.upcomingHours(13).drop(2).firstOrNull {
            (it.precipitationProbability ?: 0) >= 60 && it.dateTime.toLocalDate() == now.toLocalDate()
        } ?: return null
        return tr("Llevá paraguas: lluvia a las", "Bring an umbrella: rain at") + " ${shortHour(rainy.dateTime)}"
    }

    private fun uvWarning(snapshot: WeatherSnapshot, now: LocalDateTime): String? {
        if (!snapshot.current.isDay || now.hour >= 16) return null
        val uv = snapshot.today()?.uvIndex ?: return null
        if (uv < 6) return null
        return tr("UV ${WeatherCodes.uvLabel(uv)} al mediodía: usá protector", "${WeatherCodes.uvLabel(uv).replaceFirstChar { it.uppercase() }} UV at midday: wear sunscreen")
    }

    /** Mañana con 5° o más de diferencia de máxima respecto de hoy. */
    private fun tomorrowChange(snapshot: WeatherSnapshot): String? {
        val days = snapshot.upcomingDays(2)
        if (days.size < 2) return null
        val delta = days[1].max - days[0].max
        if (kotlin.math.abs(delta) < 5) return null
        return if (delta < 0) {
            tr("Mañana ${delta.degreesDelta()} más fresco", "Tomorrow ${delta.degreesDelta()} cooler")
        } else {
            tr("Mañana ${delta.degreesDelta()} más cálido", "Tomorrow ${delta.degreesDelta()} warmer")
        }
    }

    private fun sunEvent(snapshot: WeatherSnapshot, now: LocalDateTime): String? {
        val today = snapshot.today() ?: return null
        val sunrise = LocalDateTime.parse(today.sunrise)
        val sunset = LocalDateTime.parse(today.sunset)
        val toSunset = Duration.between(now, sunset).toMinutes()
        if (toSunset in 1..60) return tr("Atardece en $toSunset min", "Sunset in $toSunset min")
        val toSunrise = Duration.between(now, sunrise).toMinutes()
        if (toSunrise in 1..60) return tr("Amanece en $toSunrise min", "Sunrise in $toSunrise min")
        return null
    }
}
