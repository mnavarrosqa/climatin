package com.mnavarrosqa.clima.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class UnitsTest {
    @After
    fun restaurar() {
        Units.current = UnitSettings()
    }

    @Test
    fun fahrenheit() {
        Units.current = UnitSettings(temperature = TemperatureUnit.FAHRENHEIT)
        assertEquals("72°", 22.0.degrees())
        assertEquals("32°", 0.0.degrees())
    }

    @Test
    fun viento() {
        assertEquals("36 km/h", windText(36.0))
        Units.current = UnitSettings(wind = WindUnit.MS)
        assertEquals("10 m/s", windText(36.0))
        Units.current = UnitSettings(wind = WindUnit.KNOTS)
        assertEquals("19 nudos", windText(36.0))
    }

    @Test
    fun presion() {
        assertEquals("1013 hPa", pressureText(1013.25))
        Units.current = UnitSettings(pressure = PressureUnit.INHG)
        assertEquals("29.92 inHg", pressureText(1013.25))
    }

    @Test
    fun horaEn24Y12() {
        val time = LocalDateTime.of(2026, 10, 24, 15, 0)
        assertEquals("15:00", shortHour(time))
        Units.current = UnitSettings(use24h = false)
        assertEquals(true, shortHour(time).startsWith("3:00"))
    }
}
