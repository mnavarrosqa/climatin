package com.mnavarrosqa.clima.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    private fun alert(id: String, expires: String?, text: String = "Lluvias fuertes") =
        WeatherAlert(id, "Lluvia", "Severe", text, "Detalle", null, expires)

    @Test
    fun alertasRepetidasEnDosZonas() {
        val a = alert("zona-a", "2026-10-24T18:00:00-03:00")
        val b = alert("zona-b", "2026-10-24T21:00:00-03:00")
        val other = alert("otra", "2026-10-24T12:00:00-03:00", text = "Viento")
        // Queda la que vence más tarde, venga en el orden que venga.
        assertEquals(listOf("zona-b", "otra"), listOf(a, other, b).withoutRepeats().map { it.id })
        assertEquals(listOf("otra", "zona-b"), listOf(other, b, a).withoutRepeats().map { it.id })
        // Mismo vencimiento: la de id menor, no la primera.
        val c = alert("zona-c", "2026-10-24T21:00:00-03:00")
        assertEquals(listOf("zona-b"), listOf(c, b).withoutRepeats().map { it.id })
        assertEquals(a.textSignature, b.textSignature)
    }

    // Un cuadrado alrededor de Córdoba capital.
    private val cordoba = Geo.parsePolygon("-31.3,-64.3 -31.3,-64.1 -31.5,-64.1 -31.5,-64.3 -31.3,-64.3")

    @Test
    fun leeElPoligonoCap() {
        assertEquals(5, cordoba.size)
        assertEquals(-31.3 to -64.3, cordoba.first())
    }

    @Test
    fun puntoAdentroYAfuera() {
        assertTrue(Geo.contains(cordoba, -31.42, -64.18))
        assertFalse(Geo.contains(cordoba, -34.6, -58.4))
    }

    @Test
    fun argentina() {
        assertTrue(Geo.isInArgentina(-31.42, -64.18))
        assertFalse(Geo.isInArgentina(40.4, -3.7))
    }

    @Test
    fun teselas() {
        assertEquals(1.0, Geo.tileX(0.0, 1), 1e-9)
        assertEquals(1.0, Geo.tileY(0.0, 1), 1e-9)
        assertEquals(0.0, Geo.tileX(-180.0, 5), 1e-9)
    }
}
