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
    fun lluviaEnMilimetrosOPulgadas() {
        assertEquals("2.5 mm", mmText(2.54))
        Units.current = UnitSettings(precipitation = PrecipitationUnit.INCHES)
        assertEquals("0.10 in", mmText(2.54))
        assertEquals("1.00 in", mmText(25.4))
        assertEquals("12.0 in", mmText(304.8))
    }

    @Test
    fun distanciaYVisibilidad() {
        assertEquals("12 km", distanceText(12.2))
        assertEquals("1 km", distanceText(1.0))
        assertEquals("+10 km", visibilityText(24_000.0))
        assertEquals("4 km", visibilityText(4_200.0))
        assertEquals("350 m", visibilityText(350.0))
        Units.current = UnitSettings(distance = DistanceUnit.MILES)
        assertEquals("0.6 mi", distanceText(1.0))
        assertEquals("62 mi", distanceText(100.0))
        assertEquals("+6 mi", visibilityText(24_000.0))
        assertEquals("3 mi", visibilityText(4_200.0))
        assertEquals("0.2 mi", visibilityText(350.0))
        assertEquals("150 ft", visibilityText(50.0))
    }

    @Test
    fun unidadesSegunElPais() {
        val us = defaultUnitsFor("US", use24h = false)
        assertEquals(TemperatureUnit.FAHRENHEIT, us.temperature)
        assertEquals(WindUnit.MPH, us.wind)
        assertEquals(PressureUnit.INHG, us.pressure)
        assertEquals(PrecipitationUnit.INCHES, us.precipitation)
        assertEquals(DistanceUnit.MILES, us.distance)
        assertEquals(false, us.use24h)
        assertEquals(UnitSettings(), defaultUnitsFor("AR", use24h = true))
        assertEquals(UnitSettings(), defaultUnitsFor(null, use24h = true))
        val gb = defaultUnitsFor("gb", use24h = true)
        assertEquals(TemperatureUnit.CELSIUS, gb.temperature)
        assertEquals(DistanceUnit.MILES, gb.distance)
        assertEquals(PrecipitationUnit.MM, gb.precipitation)
    }

    @Test
    fun unidadesParaElReloj() {
        val units = UnitSettings(temperature = TemperatureUnit.FAHRENHEIT, use24h = false, distance = DistanceUnit.MILES)
        // Cuatro partes, como entiende un reloj con una versión anterior.
        assertEquals("FAHRENHEIT,KMH,HPA,false", units.encode())
        assertEquals(units.copy(distance = DistanceUnit.KM), decodeUnits(units.encode()))
        assertEquals(units.copy(precipitation = PrecipitationUnit.INCHES), decodeUnits("FAHRENHEIT,KMH,HPA,false,INCHES,MILES"))
        assertEquals(null, decodeUnits("FAHRENHEIT,KMH,HPA"))
    }

    @Test
    fun horaCompacta() {
        assertEquals("15:00", compactHour(LocalDateTime.of(2026, 10, 24, 15, 0)))
        Units.current = UnitSettings(use24h = false)
        assertEquals("3 PM", compactHour(LocalDateTime.of(2026, 10, 24, 15, 0)))
        assertEquals("12 AM", compactHour(LocalDateTime.of(2026, 10, 24, 0, 0)))
        assertEquals("12 PM", compactHour(LocalDateTime.of(2026, 10, 24, 12, 0)))
        assertEquals("7:34 PM", compactHour(LocalDateTime.of(2026, 10, 24, 19, 34)))
        assertEquals("6:05 AM", compactHour(LocalDateTime.of(2026, 10, 24, 6, 5)))
    }

    @Test
    fun horaEn24Y12() {
        val time = LocalDateTime.of(2026, 10, 24, 15, 0)
        assertEquals("15:00", shortHour(time))
        Units.current = UnitSettings(use24h = false)
        assertEquals(true, shortHour(time).startsWith("3:00"))
    }
}
