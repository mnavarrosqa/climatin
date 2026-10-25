package com.mnavarrosqa.clima.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SnapshotSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsedDatesAreNotSaved() {
        val hour = Hour("2026-10-25T14:00", 20.0, 1, 10, true, pressure = 1015.0)
        hour.dateTime
        val text = json.encodeToString(Hour.serializer(), hour)
        assertFalse(text, text.contains("dateTime") || text.contains("delegate"))
        assertEquals(hour, json.decodeFromString(Hour.serializer(), text))
    }

    @Test
    fun staleSnapshotTakesCurrentFromThisHour() {
        val zone = java.time.ZoneOffset.UTC
        val nowHour = java.time.LocalDateTime.now(zone).truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        val hours = (0..3).map { Hour(nowHour.plusHours(it.toLong()).toString(), 10.0 + it, 61, 80, true) }
        val snapshot = WeatherSnapshot(
            place = "X", latitude = 0.0, longitude = 0.0, utcOffsetSeconds = 0, timezone = "UTC",
            fetchedAt = System.currentTimeMillis() - 3 * 60 * 60_000L,
            current = Current(nowHour.minusHours(3).toString(), 25.0, 25.0, 50, true, 0, 5.0, 0.0),
            hours = hours, days = emptyList(),
        )
        val fresh = snapshot.withCurrentFromHours()
        assertEquals(10.0, fresh.current.temperature, 0.001)
        assertEquals(61, fresh.current.code)
        assertEquals(25.0, snapshot.copy(fetchedAt = System.currentTimeMillis()).withCurrentFromHours().current.temperature, 0.001)
    }
}
