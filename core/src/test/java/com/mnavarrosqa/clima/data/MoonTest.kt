package com.mnavarrosqa.clima.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MoonTest {
    @Test
    fun lunaNuevaDelEclipseDe2024() {
        assertEquals("Luna nueva", Moon.phaseName(LocalDate.of(2024, 4, 8)))
    }

    @Test
    fun lunaLlenaDeAbrilDe2024() {
        assertEquals("Luna llena", Moon.phaseName(LocalDate.of(2024, 4, 23)))
    }

    @Test
    fun edadSiempreEntreCeroYUno() {
        var date = LocalDate.of(1990, 1, 1)
        repeat(400) {
            val age = Moon.age(date)
            assert(age >= 0.0 && age < 1.0) { "edad fuera de rango: $age el $date" }
            date = date.plusDays(37)
        }
    }

    /** Primera vez que la luna sale (cruza el horizonte hacia arriba) desde [from], en horas. */
    private fun moonriseHoursAfter(from: java.time.Instant, lat: Double, lon: Double): Double? {
        var previous = Moon.altitude(from, lat, lon)
        for (step in 1..24 * 12) {
            val now = from.plusSeconds(step * 300L)
            val alt = Moon.altitude(now, lat, lon)
            if (previous < 0 && alt >= 0) return step * 5 / 60.0
            previous = alt
        }
        return null
    }

    @Test
    fun lunaLlenaSaleAlAtardecer() {
        // Llena el 23/4/2024 a las 23:49 UTC: en el ecuador (meridiano 0) sale cerca de las 18 UTC.
        val rise = moonriseHoursAfter(java.time.Instant.parse("2024-04-23T12:00:00Z"), 0.0, 0.0)!!
        assert(rise in 4.5..8.0) { "salió $rise h después del mediodía" }
    }

    @Test
    fun lunaNuevaSaleAlAmanecer() {
        // Nueva el 8/4/2024 a las 18:21 UTC: sale con el sol, cerca de las 6 UTC.
        val rise = moonriseHoursAfter(java.time.Instant.parse("2024-04-08T00:00:00Z"), 0.0, 0.0)!!
        assert(rise in 4.5..7.5) { "salió $rise h después de la medianoche" }
    }

    @Test
    fun lunaLlenaAltaALaMedianoche() {
        val alt = Moon.altitude(java.time.Instant.parse("2024-04-24T00:00:00Z"), 0.0, 0.0)
        assert(alt > 55) { "altura $alt" }
    }

    @Test
    fun iluminacion() {
        assertEquals(0.0, Moon.illumination(0.0), 1e-9)
        assertEquals(1.0, Moon.illumination(0.5), 1e-9)
        assertEquals(0.5, Moon.illumination(0.25), 1e-9)
    }
}
