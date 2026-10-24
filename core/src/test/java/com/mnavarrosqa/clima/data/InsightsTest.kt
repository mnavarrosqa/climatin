package com.mnavarrosqa.clima.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class InsightsTest {

    private val now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES)
    private val hour = now.truncatedTo(ChronoUnit.HOURS)

    private fun day(offset: Long, max: Double, uv: Double = 3.0) = Day(
        date = now.toLocalDate().plusDays(offset).toString(),
        code = 1,
        max = max,
        min = 10.0,
        // Amanecer y atardecer lejos de "ahora" para que no aparezcan en la frase.
        sunrise = now.minusHours(5).toString(),
        sunset = now.plusHours(5).toString(),
        uvIndex = uv,
        precipitationProbability = 0,
    )

    private fun snapshot(
        code: Int = 1,
        precipitation: Double = 0.0,
        wind: Double = 10.0,
        hours: List<Hour> = (0..14).map { Hour(hour.plusHours(it.toLong()).toString(), 20.0, 1, 0, true) },
        days: List<Day> = listOf(day(0, 25.0), day(1, 24.0)),
        alerts: List<WeatherAlert> = emptyList(),
        isDay: Boolean = false,
    ) = WeatherSnapshot(
        place = "Prueba",
        latitude = 0.0,
        longitude = 0.0,
        utcOffsetSeconds = 0,
        fetchedAt = 0L,
        current = Current(now.toString(), 20.0, 20.0, 50, isDay, code, wind, precipitation),
        hours = hours,
        days = days,
        alerts = alerts,
    )

    @Test
    fun sinNadaParaDecir() {
        assertNull(Insights.headline(snapshot()))
    }

    @Test
    fun laAlertaVaPrimero() {
        val alert = WeatherAlert("1", "Tormentas", "Severe", null, null, null, null)
        assertEquals("Alerta: Tormentas", Insights.headline(snapshot(wind = 60.0, alerts = listOf(alert))))
    }

    @Test
    fun cuandoParaDeLlover() {
        val hours = (0..8).map { i ->
            Hour(hour.plusHours(i.toLong()).toString(), 18.0, if (i < 3) 63 else 3, if (i < 3) 90 else 10, true)
        }
        val text = Insights.headline(snapshot(code = 63, precipitation = 1.5, hours = hours))
        assertEquals("Para de llover cerca de las ${shortHour(hour.plusHours(3))}", text)
    }

    @Test
    fun paraguasParaMasTarde() {
        // Lluvia a las 3 horas, siempre que siga siendo hoy.
        val rainHour = hour.plusHours(3)
        if (rainHour.toLocalDate() != now.toLocalDate()) return
        val hours = (0..14).map { i ->
            Hour(hour.plusHours(i.toLong()).toString(), 20.0, 1, if (i == 3) 80 else 0, true)
        }
        assertEquals("Llevá paraguas: lluvia a las ${shortHour(rainHour)}", Insights.headline(snapshot(hours = hours)))
    }

    @Test
    fun vientoFuerte() {
        assertEquals("Viento fuerte: 45 km/h", Insights.headline(snapshot(wind = 45.0)))
    }

    @Test
    fun mananaMasFresco() {
        val text = Insights.headline(snapshot(days = listOf(day(0, 28.0), day(1, 21.0))))
        assertEquals("Mañana 7° más fresco", text)
    }

    @Test
    fun puntosCardinales() {
        assertEquals("N", windFrom(5.0))
        assertEquals("SO", windFrom(225.0))
        assertEquals("N", windFrom(355.0))
        assertTrue(windFrom(-90.0) == "O")
    }
}
