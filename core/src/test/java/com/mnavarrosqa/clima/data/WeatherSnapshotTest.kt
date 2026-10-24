package com.mnavarrosqa.clima.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class WeatherSnapshotTest {

    private val now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES)

    private fun snapshot(
        code: Int = 2,
        precipitation: Double = 0.0,
        minutely: List<Minutely> = emptyList(),
        hours: List<Hour> = emptyList(),
    ) = WeatherSnapshot(
        place = "Prueba",
        latitude = 0.0,
        longitude = 0.0,
        utcOffsetSeconds = 0,
        fetchedAt = 0L,
        current = Current(
            time = now.toString(),
            temperature = 20.0,
            feelsLike = 20.0,
            humidity = 50,
            isDay = true,
            code = code,
            windSpeed = 10.0,
            precipitation = precipitation,
        ),
        hours = hours,
        days = emptyList(),
        minutely = minutely,
    )

    @Test
    fun avisaLluviaSegunElPronosticoCada15Minutos() {
        val minutely = (0..7).map { i ->
            Minutely(now.plusMinutes(15L * i).toString(), if (i == 2) 0.6 else 0.0)
        }
        val minutes = snapshot(minutely = minutely).minutesUntilRain()
        assertTrue("esperaba ~30 min y dio $minutes", minutes != null && minutes in 25..35)
    }

    @Test
    fun noAvisaSiYaLlueve() {
        val minutely = listOf(Minutely(now.plusMinutes(15).toString(), 2.0))
        assertNull(snapshot(code = 63, precipitation = 1.2, minutely = minutely).minutesUntilRain())
    }

    @Test
    fun usaLaProbabilidadPorHoraSiNoHayDatosCada15Minutos() {
        val hour = now.truncatedTo(ChronoUnit.HOURS)
        val hours = (0..3).map { i ->
            Hour(hour.plusHours(i.toLong()).toString(), 20.0, 2, if (i == 1) 80 else 0, true)
        }
        val minutes = snapshot(hours = hours).minutesUntilRain()
        assertTrue("esperaba menos de 2 h y dio $minutes", minutes != null && minutes in 5..120)
    }

    @Test
    fun recortaLasHorasPasadas() {
        val hour = now.truncatedTo(ChronoUnit.HOURS)
        val hours = (-3..5).map { i -> Hour(hour.plusHours(i.toLong()).toString(), i.toDouble(), 0, 0, true) }
        val upcoming = snapshot(hours = hours).upcomingHours(3)
        assertEquals(listOf(0.0, 1.0, 2.0), upcoming.map { it.temperature })
    }
}
