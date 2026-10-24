package com.mnavarrosqa.clima.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val body = """
        {
          "utc_offset_seconds": -10800,
          "current": {
            "time": "2026-10-24T14:15", "temperature_2m": 21.4, "apparent_temperature": 20.1,
            "relative_humidity_2m": 48, "is_day": 1, "weather_code": 2, "wind_speed_10m": 12.5,
            "precipitation": 0.0, "surface_pressure": 1012.3, "visibility": 24000.0, "uv_index": 5.2
          },
          "hourly": {
            "time": ["2026-10-24T13:00", "2026-10-24T14:00", "2026-10-24T15:00"],
            "temperature_2m": [20.0, 21.0, null],
            "weather_code": [1, 2, 3],
            "precipitation_probability": [0, 10, 40],
            "is_day": [1, 1, 1]
          },
          "daily": {
            "time": ["2026-10-24", "2026-10-25"],
            "weather_code": [2, 61],
            "temperature_2m_max": [25.0, 19.5],
            "temperature_2m_min": [12.0, 10.0],
            "sunrise": ["2026-10-24T06:42", "2026-10-25T06:41"],
            "sunset": ["2026-10-24T19:58", "2026-10-25T19:59"],
            "uv_index_max": [8.1, 3.0],
            "precipitation_probability_max": [10, 80]
          },
          "minutely_15": {
            "time": ["2026-10-24T14:15", "2026-10-24T14:30"],
            "precipitation": [0.0, 0.3]
          }
        }
    """.trimIndent()

    @Test
    fun convierteLaRespuestaEnSnapshot() {
        val forecast = json.decodeFromString(Forecast.serializer(), body)
        val snapshot = OpenMeteo.toSnapshot(forecast, CURRENT_PLACE_ID, "Córdoba", -31.4, -64.2, null, 0L)

        assertEquals(-10800, snapshot.utcOffsetSeconds)
        assertEquals(21.4, snapshot.current.temperature, 0.001)
        assertEquals(1012.3, snapshot.current.pressure!!, 0.001)
        // La hora pasada (13:00) se descarta y la que no tiene temperatura también.
        assertEquals(listOf("2026-10-24T14:00"), snapshot.hours.map { it.time })
        assertEquals(2, snapshot.days.size)
        assertEquals(80, snapshot.days[1].precipitationProbability)
        assertEquals(2, snapshot.minutely.size)
        assertTrue(snapshot.current.isDay)
    }
}
