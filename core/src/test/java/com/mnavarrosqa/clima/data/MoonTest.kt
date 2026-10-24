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
}
