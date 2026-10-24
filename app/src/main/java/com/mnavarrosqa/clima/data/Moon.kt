package com.mnavarrosqa.clima.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Fase lunar calculada a partir de una luna nueva conocida (6/1/2000). */
object Moon {
    private const val SYNODIC_DAYS = 29.530588853
    private val referenceNewMoon: Instant = Instant.parse("2000-01-06T18:14:00Z")

    /** 0 = luna nueva, 0.5 = llena, 1 = nueva otra vez. */
    fun age(date: LocalDate): Double {
        val noon = date.atTime(12, 0).toInstant(ZoneOffset.UTC)
        val days = (noon.epochSecond - referenceNewMoon.epochSecond) / 86_400.0
        val cycles = days / SYNODIC_DAYS
        return cycles - kotlin.math.floor(cycles)
    }

    fun phaseName(date: LocalDate): String {
        val a = age(date)
        return when {
            a < 0.03 || a >= 0.97 -> "Luna nueva"
            a < 0.22 -> "Creciente"
            a < 0.28 -> "Cuarto creciente"
            a < 0.47 -> "Gibosa creciente"
            a < 0.53 -> "Luna llena"
            a < 0.72 -> "Gibosa menguante"
            a < 0.78 -> "Cuarto menguante"
            else -> "Menguante"
        }
    }
}
