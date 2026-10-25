package com.mnavarrosqa.clima.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class ClimateTest {

    /** Diez años de días: máxima 20° en la ventana de [around] y 40° lejos, para ver que filtra. */
    private fun years(around: LocalDate, from: Int = around.year - 10, to: Int = around.year - 1): List<ClimateDay> {
        val days = mutableListOf<ClimateDay>()
        var d = LocalDate.of(from, 1, 1)
        while (d.year <= to) {
            val near = daysFromCalendarDate(d, around) <= 7
            days += ClimateDay(d.toString(), if (near) 20.0 + d.year % 2 else 40.0, if (near) 8.0 else -5.0, if (near) 1.0 else 9.0)
            d = d.plusDays(1)
        }
        return days
    }

    @Test
    fun distanciaCruzaElAnio() {
        assertEquals(2, daysFromCalendarDate(LocalDate.of(2020, 12, 30), LocalDate.of(2026, 1, 1)))
        assertEquals(2, daysFromCalendarDate(LocalDate.of(2021, 1, 2), LocalDate.of(2026, 12, 31)))
        assertEquals(0, daysFromCalendarDate(LocalDate.of(2021, 2, 28), LocalDate.of(2024, 2, 29)))
        assertEquals(10, daysFromCalendarDate(LocalDate.of(2019, 10, 15), LocalDate.of(2026, 10, 25)))
    }

    @Test
    fun normalSoloConLaVentana() {
        val today = LocalDate.of(2026, 10, 25)
        val normal = normalFor(years(today), today)
        assertNotNull(normal)
        // Años pares 20°, impares 21°: el promedio queda en 20,5.
        assertEquals(20.5, normal!!.max, 0.01)
        assertEquals(8.0, normal.min, 0.001)
        assertEquals(1.0, normal.precipitationPerDay!!, 0.001)
        assertEquals(10, normal.years)
    }

    @Test
    fun normalEnAnioNuevo() {
        val today = LocalDate.of(2027, 1, 2)
        val normal = normalFor(years(today, 2016, 2026), today)
        assertTrue(normal!!.max in 20.0..21.0)
    }

    @Test
    fun noComparaHoyConsigoMismo() {
        val today = LocalDate.of(2026, 10, 25)
        val onlyThisYear = (0..30L).map { ClimateDay(today.minusDays(it).toString(), 30.0, 10.0, 0.0) }
        assertNull(normalFor(onlyThisYear, today))
    }

    @Test
    fun sinDatosSuficientesNoHayNormal() {
        val today = LocalDate.of(2026, 10, 25)
        val few = (1..2).flatMap { y -> (-3..3L).map { ClimateDay(today.minusYears(y.toLong()).plusDays(it).toString(), 20.0, 10.0) } }
        assertNull(normalFor(few, today))
        val noMax = years(today).map { it.copy(max = null) }
        assertNull(normalFor(noMax, today))
    }

    @Test
    fun laCacheAlcanzaParaUnMes() {
        val today = LocalDate.of(2026, 10, 25)
        val all = years(today.plusDays(15))
        val kept = keepForCache(all, today)
        assertTrue(kept.size < all.size / 5)
        // Con lo guardado da lo mismo que con todo, hoy y dentro de 30 días.
        for (later in listOf(0L, 15L, 30L)) {
            val day = today.plusDays(later)
            assertEquals(normalFor(all, day)!!.max, normalFor(kept, day)!!.max, 0.0001)
        }
    }

    @Test
    fun textoDeLaEpoca() {
        val normal = ClimateNormal(max = 21.0, min = 10.0, precipitationPerDay = 2.0, years = 10)
        val date = LocalDate.of(2026, 10, 25)
        val warm = seasonText(25.2, normal, date)
        assertEquals("+4°", warm.value)
        assertEquals("Más cálido que lo normal para octubre (máx normal 21°)", warm.note)
        assertEquals("−7°", seasonText(14.0, normal, date).value)
        assertEquals("Mucho más frío que lo normal para octubre (máx normal 21°)", seasonText(14.0, normal, date).note)
        val usual = seasonText(21.3, normal, date)
        assertEquals("0°", usual.value)
        assertEquals("Normal para octubre (máx normal 21°)", usual.note)
    }

    @Test
    fun milimetros() {
        assertEquals("0.4 mm", mmText(0.43))
        assertEquals("2 mm", mmText(2.0))
        assertEquals("9.5 mm", mmText(9.46))
        assertEquals("34 mm", mmText(33.6))
    }

    @Test
    fun lluviaDeLaSemana() {
        val today = LocalDate.of(2026, 10, 25)
        val days = (0..7L).map { ClimateDay(today.minusDays(it).toString(), precipitation = it.toDouble()) }
        // 1 + 2 + ... + 7: el día de hoy (0) no cuenta.
        assertEquals(28.0, weekRain(days, today)!!, 0.001)
        assertNull(weekRain(days.drop(3), today))
        assertNull(weekRain(days.map { if (it.localDate == today.minusDays(4)) it.copy(precipitation = null) else it }, today))
    }

    @Test
    fun textoDeLluviaCaida() {
        assertEquals(RainfallText("12 mm", "Ayer · 34 mm en 7 días"), rainfallText(12.0, 34.0, 0.0))
        assertEquals(RainfallText("3.5 mm", "Ayer · hoy van 1.2 mm"), rainfallText(3.5, null, 1.2))
        assertEquals(RainfallText("8 mm", "En los últimos 7 días"), rainfallText(0.0, 8.0, null))
        assertEquals(RainfallText("0.6 mm", "Hoy hasta ahora"), rainfallText(0.0, null, 0.6))
        assertEquals(RainfallText("Nada", "Sin lluvia en la semana"), rainfallText(0.0, 0.0, 0.0))
        // Sin lluvia ayer y sin el dato de la semana no hay tarjeta.
        assertNull(rainfallText(0.0, null, null))
        assertNull(rainfallText(null, null, 0.05))
    }

    @Test
    fun lluviaDeHoyHastaAhora() {
        val now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS)
        val start = now.toLocalDate().atStartOfDay()
        // De ayer a las 0 hasta la hora anterior a la actual, 0,5 mm por hora.
        val past = generateSequence(start.minusDays(1)) { it.plusHours(1) }.takeWhile { it.isBefore(now) }
            .map { Hour(it.toString(), 15.0, 61, null, true, precipitation = 0.5) }.toList()
        val snapshot = WeatherSnapshot(
            place = "X", latitude = 0.0, longitude = 0.0, utcOffsetSeconds = 0, timezone = "UTC",
            fetchedAt = System.currentTimeMillis(), current = Current(now.toString(), 15.0, 15.0, 50, true, 61, 10.0, 0.5),
            hours = emptyList(), days = emptyList(), pastHours = past,
        )
        // La hora de las 0 trae lo de ayer a las 23: cuentan las de la 1 en adelante.
        val expected = past.count { LocalDateTime.parse(it.time).minusHours(1).toLocalDate() == now.toLocalDate() } * 0.5
        assertEquals(expected, snapshot.rainSoFarToday() ?: 0.0, 0.001)
        assertEquals(maxOf(now.hour - 1, 0) * 0.5, snapshot.rainSoFarToday() ?: 0.0, 0.001)
    }

    @Test
    fun leeElArchivoHistorico() {
        val body = """
            {"latitude": -31.4, "longitude": -64.2, "daily_units": {"time": "iso8601"},
             "daily": {"time": ["2024-10-24", "2024-10-25"], "temperature_2m_max": [24.1, null],
                       "temperature_2m_min": [11.0, 12.5], "precipitation_sum": [0.0, 3.2]}}
        """.trimIndent()
        val days = Json { ignoreUnknownKeys = true }.decodeFromString(ArchiveResponse.serializer(), body).toClimateDays()
        assertEquals(listOf(ClimateDay("2024-10-24", 24.1, 11.0, 0.0), ClimateDay("2024-10-25", null, 12.5, 3.2)), days)
    }

    @Test
    fun laLluviaDeLasHorasPasadasSeGuarda() {
        val body = """
            {"current": {"time": "2026-10-24T14:15", "temperature_2m": 21.4, "apparent_temperature": 20.1,
              "relative_humidity_2m": 48, "is_day": 1, "weather_code": 2, "wind_speed_10m": 12.5},
             "hourly": {"time": ["2026-10-24T13:00", "2026-10-24T14:00"], "temperature_2m": [20.0, 21.0],
              "weather_code": [61, 2], "is_day": [1, 1], "precipitation": [1.5, 0.0]},
             "daily": {"time": ["2026-10-24"], "weather_code": [2], "temperature_2m_max": [25.0],
              "temperature_2m_min": [12.0], "sunrise": ["2026-10-24T06:42"], "sunset": ["2026-10-24T19:58"]}}
        """.trimIndent()
        val forecast = Json { ignoreUnknownKeys = true }.decodeFromString(Forecast.serializer(), body)
        val snapshot = OpenMeteo.toSnapshot(forecast, CURRENT_PLACE_ID, "X", 0.0, 0.0, null, 0L)
        assertEquals(1.5, snapshot.pastHours.single().precipitation!!, 0.001)
    }
}
