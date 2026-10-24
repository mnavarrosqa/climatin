package com.mnavarrosqa.clima.data

import java.time.Duration
import java.time.LocalDateTime

/**
 * La frase del momento, al estilo "De un vistazo": de todo lo que pasa, elige lo más útil.
 * Orden: alertas, lluvia inminente, cuándo para de llover, lluvia más tarde, UV alto, viento
 * fuerte, cambio de temperatura de mañana y amanecer o atardecer cercanos.
 */
object Insights {

    fun headline(snapshot: WeatherSnapshot): String? {
        val now = snapshot.nowAtPlace()
        snapshot.alerts.firstOrNull()?.let { return "Alerta: ${it.event}" }
        snapshot.minutesUntilRain()?.let { return rainSoonText(it) }
        rainStops(snapshot, now)?.let { return it }
        rainLater(snapshot, now)?.let { return it }
        uvWarning(snapshot, now)?.let { return it }
        if (snapshot.current.windSpeed >= 40) return "Viento fuerte: ${windText(snapshot.current.windSpeed)}"
        tomorrowChange(snapshot)?.let { return it }
        sunEvent(snapshot, now)?.let { return it }
        return null
    }

    private fun isWet(code: Int) = WeatherCodes.kind(code) in
        setOf(WeatherKind.DRIZZLE, WeatherKind.RAIN, WeatherKind.STORM, WeatherKind.SNOW)

    /** Si ahora llueve, a qué hora se espera que pare (dentro de 6 horas). */
    private fun rainStops(snapshot: WeatherSnapshot, now: LocalDateTime): String? {
        if (!isWet(snapshot.current.code) && snapshot.current.precipitation < 0.1) return null
        val dry = snapshot.upcomingHours(7).drop(1).firstOrNull {
            !isWet(it.code) && (it.precipitationProbability ?: 0) < 30
        } ?: return null
        return "Para de llover cerca de las ${shortHour(dry.dateTime)}"
    }

    /** Lluvia probable más tarde hoy (en las próximas 12 horas). */
    private fun rainLater(snapshot: WeatherSnapshot, now: LocalDateTime): String? {
        val rainy = snapshot.upcomingHours(13).drop(2).firstOrNull {
            (it.precipitationProbability ?: 0) >= 60 && it.dateTime.toLocalDate() == now.toLocalDate()
        } ?: return null
        return "Llevá paraguas: lluvia a las ${shortHour(rainy.dateTime)}"
    }

    private fun uvWarning(snapshot: WeatherSnapshot, now: LocalDateTime): String? {
        if (!snapshot.current.isDay || now.hour >= 16) return null
        val uv = snapshot.today()?.uvIndex ?: return null
        if (uv < 6) return null
        return "UV ${WeatherCodes.uvLabel(uv)} al mediodía: usá protector"
    }

    /** Mañana con 5° o más de diferencia de máxima respecto de hoy. */
    private fun tomorrowChange(snapshot: WeatherSnapshot): String? {
        val days = snapshot.upcomingDays(2)
        if (days.size < 2) return null
        val delta = days[1].max - days[0].max
        if (kotlin.math.abs(delta) < 5) return null
        return if (delta < 0) "Mañana ${delta.degreesDelta()} más fresco" else "Mañana ${delta.degreesDelta()} más cálido"
    }

    private fun sunEvent(snapshot: WeatherSnapshot, now: LocalDateTime): String? {
        val today = snapshot.today() ?: return null
        val sunrise = LocalDateTime.parse(today.sunrise)
        val sunset = LocalDateTime.parse(today.sunset)
        val toSunset = Duration.between(now, sunset).toMinutes()
        if (toSunset in 1..60) return "Atardece en $toSunset min"
        val toSunrise = Duration.between(now, sunrise).toMinutes()
        if (toSunrise in 1..60) return "Amanece en $toSunrise min"
        return null
    }
}
