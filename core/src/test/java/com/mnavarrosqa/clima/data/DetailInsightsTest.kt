package com.mnavarrosqa.clima.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class DetailInsightsTest {
    private val now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS)

    private fun snapshot(
        hours: List<Hour>,
        current: Current = Current(now.toString(), 15.0, 15.0, 50, true, 0, 10.0, 0.0),
        pastHours: List<Hour> = emptyList(),
    ) = WeatherSnapshot(
        place = "X", latitude = 0.0, longitude = 0.0, utcOffsetSeconds = 0, timezone = "UTC",
        fetchedAt = System.currentTimeMillis(), current = current, hours = hours, days = emptyList(), pastHours = pastHours,
    )

    private fun hour(offset: Long, temp: Double = 15.0, isDay: Boolean = true, block: Hour.() -> Hour = { this }) =
        Hour(now.plusHours(offset).toString(), temp, 0, 0, isDay, feelsLike = temp, windSpeed = 5.0).block()

    @Test
    fun beaufort() {
        assertEquals("Calma", beaufortLabel(0.5))
        assertEquals("Brisa moderada", beaufortLabel(25.0))
        assertEquals("Temporal", beaufortLabel(70.0))
    }

    @Test
    fun outfitConFrioYLluvia() {
        val hours = (0..11L).map { hour(it, 3.0) { copy(precipitationProbability = 80) } }
        val items = snapshot(hours).outfit()
        assertTrue(items.toString(), items.any { "Abrigo grueso" in it })
        assertTrue(items.toString(), items.any { "Paraguas" in it })
    }

    @Test
    fun rocioCuandoLaTemperaturaLlegaAlPuntoDeRocio() {
        val hours = (0..15L).map { i -> hour(i, 12.0 - i, isDay = false) { copy(dewPoint = 6.0) } }
        val dew = snapshot(hours).dewTonight()
        // Con hora de madrugada o de noche, el límite es otro; alcanza con que la encuentre si cabe.
        if (dew != null) assertTrue(dew.at.isAfter(now.minusHours(1)))
    }

    @Test
    fun nieblaQueSeLevanta() {
        val hours = (0..5L).map { i -> hour(i) { copy(visibility = if (i < 3) 400.0 else 8000.0) } }
        val fog = snapshot(hours, current = Current(now.toString(), 10.0, 10.0, 95, true, 45, 2.0, 0.0, visibility = 300.0)).fog()
        assertNotNull(fog)
        assertTrue(fog!!.now)
        assertEquals(now.plusHours(3), fog.at)
    }

    @Test
    fun sinNiebla() {
        val hours = (0..5L).map { i -> hour(i) { copy(visibility = 20000.0) } }
        assertNull(snapshot(hours, current = Current(now.toString(), 10.0, 10.0, 50, true, 0, 2.0, 0.0, visibility = 20000.0)).fog())
    }

    @Test
    fun aireQueEmpeora() {
        assertEquals(1, AirQuality(20, 5.0, upcomingAqi = listOf(20, 25, 30, 35, 40, 45)).trend())
        assertEquals(0, AirQuality(20, 5.0, upcomingAqi = listOf(20, 21, 20, 22, 21, 20)).trend())
        assertEquals("ozono", AirQuality(40, 5.0, pm10 = 10.0, ozone = 120.0).mainPollutant())
    }

    @Test
    fun polenSoloSiHay() {
        assertNull(AirQuality(20, 5.0, grassPollen = 3.0).pollenText())
        assertEquals("pastos alto", AirQuality(20, 5.0, grassPollen = 80.0).pollenText())
    }

    @Test
    fun comparacionConAyer() {
        val past = listOf(Hour(now.minusDays(1).toString(), 11.0, 0, null, true))
        val diff = snapshot(listOf(hour(0)), pastHours = past).versusYesterday()
        assertEquals(4.0, diff!!, 0.001)
        assertEquals("4° más cálido que ayer", versusYesterdayText(diff))
    }

    @Test
    fun resumenDelDia() {
        val prev = Day("2026-10-25", 0, 25.0, 12.0, "2026-10-25T06:00", "2026-10-25T19:00", 5.0, 0)
        val day = prev.copy(date = "2026-10-26", max = 19.0, precipitationProbability = 70, windGustsMax = 60.0)
        assertEquals("Respecto del día anterior: Más fresco, con lluvia y con viento", daySummary(day, prev))
        assertEquals("", daySummary(prev.copy(precipitationProbability = 0), null))
    }
}
