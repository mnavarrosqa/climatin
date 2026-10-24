package com.mnavarrosqa.clima.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class FormatTest {
    @Test
    fun etiquetasDeDia() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals("Hoy", dayLabel(today, today))
        assertEquals("Mañana", dayLabel(today.plusDays(1), today))
        assertEquals("Lun", dayLabel(LocalDate.of(2026, 10, 26), today))
    }

    @Test
    fun diaDeLaSemanaSinPunto() {
        assertEquals("Sáb", weekday(DayOfWeek.SATURDAY))
    }

    @Test
    fun textoDeLluviaProxima() {
        assertEquals("Lluvia en unos minutos", rainSoonText(10))
        assertEquals("Lluvia en unos 35 min", rainSoonText(32))
        assertEquals("Lluvia en la próxima hora", rainSoonText(75))
        assertEquals("Lluvia en menos de 2 horas", rainSoonText(110))
    }

    @Test
    fun gradosRedondeados() {
        assertEquals("22°", 21.6.degrees())
        assertEquals("-3°", (-3.2).degrees())
    }
}
