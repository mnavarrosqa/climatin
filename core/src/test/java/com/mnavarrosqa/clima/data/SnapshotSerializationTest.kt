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
}
