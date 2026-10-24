package com.mnavarrosqa.clima.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class RainOutlookTest {

    private val now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)

    private fun snapshot(code: Int = 2, precipitation: Double = 0.0, minutely: List<Double>) = WeatherSnapshot(
        place = "Prueba",
        latitude = 0.0,
        longitude = 0.0,
        utcOffsetSeconds = 0,
        fetchedAt = 0L,
        current = Current(now.toString(), 20.0, 20.0, 50, true, code, 10.0, precipitation),
        hours = emptyList(),
        days = emptyList(),
        // Un valor cada 15 minutos desde ahora.
        minutely = minutely.mapIndexed { i, mm -> Minutely(now.plusMinutes(15L * i).toString(), mm) },
    )

    @Test
    fun sinLluviaNoHayAviso() {
        assertNull(snapshot(minutely = List(8) { 0.0 }).rainOutlook())
    }

    @Test
    fun lluviaQueEmpiezaYTermina() {
        // Seca 30 min, después 45 min de lluvia moderada (1 mm cada 15 min = 4 mm/h), y seca otra vez.
        val outlook = snapshot(minutely = listOf(0.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0)).rainOutlook()
        assertTrue(outlook is RainOutlook.Starts)
        outlook as RainOutlook.Starts
        assertEquals(30, outlook.inMinutes)
        assertEquals(45, outlook.lastsMinutes)
        assertEquals(RainIntensity.MODERATE, outlook.intensity)
    }

    @Test
    fun lluviaQueSigueMasAllaDeLoQueSeSabe() {
        val outlook = snapshot(minutely = listOf(0.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0)).rainOutlook() as RainOutlook.Starts
        assertNull(outlook.lastsMinutes)
        assertEquals(RainIntensity.HEAVY, outlook.intensity)
    }

    @Test
    fun avisaCuandoParaDeLlover() {
        val outlook = snapshot(code = 63, precipitation = 1.0, minutely = listOf(1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)).rainOutlook()
        assertEquals(RainOutlook.Stops(30), outlook)
    }

    @Test
    fun respetaLaAnticipacion() {
        val minutely = listOf(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0)
        assertNull(snapshot(minutely = minutely).rainOutlook(withinMinutes = 30))
        assertTrue(snapshot(minutely = minutely).rainOutlook(withinMinutes = 60) is RainOutlook.Starts)
    }

    @Test
    fun textos() {
        assertEquals("Lluvia moderada en unos 30 min", rainOutlookText(RainOutlook.Starts(30, 45, RainIntensity.MODERATE)))
        assertEquals("Por 1 h 30 min", rainDurationText(RainOutlook.Starts(30, 90, RainIntensity.LIGHT)))
        assertEquals("Para de llover en unos 25 min", rainOutlookText(RainOutlook.Stops(25)))
    }
}
