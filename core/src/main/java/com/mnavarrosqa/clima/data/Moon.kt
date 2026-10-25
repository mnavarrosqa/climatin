package com.mnavarrosqa.clima.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
            a < 0.03 || a >= 0.97 -> tr("Luna nueva", "New moon")
            a < 0.22 -> tr("Creciente", "Waxing crescent")
            a < 0.28 -> tr("Cuarto creciente", "First quarter")
            a < 0.47 -> tr("Gibosa creciente", "Waxing gibbous")
            a < 0.53 -> tr("Luna llena", "Full moon")
            a < 0.72 -> tr("Gibosa menguante", "Waning gibbous")
            a < 0.78 -> tr("Cuarto menguante", "Last quarter")
            else -> tr("Menguante", "Waning crescent")
        }
    }

    /** Parte iluminada, de 0 (nueva) a 1 (llena). */
    fun illumination(age: Double): Double = (1 - kotlin.math.cos(2 * kotlin.math.PI * age)) / 2

    /**
     * Altura de la luna sobre el horizonte en grados, ya corrida para que 0 sea el momento en que
     * sale o se pone (con refracción, paralaje y el radio del disco). Fórmula de baja precisión
     * de Montenbruck y Pfleger: alcanza para las horas con unos minutos de error.
     */
    fun altitude(instant: Instant, latitude: Double, longitude: Double): Double {
        val jd = instant.toEpochMilli() / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0
        fun frac(x: Double) = x - kotlin.math.floor(x)
        val twoPi = 2 * PI
        val l0 = frac(0.606433 + 1336.855225 * t)
        val l = twoPi * frac(0.374897 + 1325.552410 * t)
        val ls = twoPi * frac(0.993133 + 99.997361 * t)
        val d = twoPi * frac(0.827361 + 1236.853086 * t)
        val f = twoPi * frac(0.259086 + 1342.227825 * t)
        val dl = 22640 * sin(l) - 4586 * sin(l - 2 * d) + 2370 * sin(2 * d) + 769 * sin(2 * l) -
            668 * sin(ls) - 412 * sin(2 * f) - 212 * sin(2 * l - 2 * d) - 206 * sin(l + ls - 2 * d) +
            192 * sin(l + 2 * d) - 165 * sin(ls - 2 * d) - 125 * sin(d) - 110 * sin(l + ls) +
            148 * sin(l - ls) - 55 * sin(2 * f - 2 * d)
        val arc = 206_264.8062
        val s = f + (dl + 412 * sin(2 * f) + 541 * sin(ls)) / arc
        val h = f - 2 * d
        val n = -526 * sin(h) + 44 * sin(l + h) - 31 * sin(-l + h) - 23 * sin(ls + h) +
            11 * sin(-ls + h) - 25 * sin(-2 * l + f) + 21 * sin(-l + f)
        val lambda = twoPi * frac(l0 + dl / 1_296_000.0)
        val beta = (18_520.0 * sin(s) + n) / arc
        // De la eclíptica al ecuador.
        val eps = Math.toRadians(23.43929111 - 0.0130042 * t)
        val x = cos(beta) * cos(lambda)
        val y = cos(beta) * sin(lambda)
        val z = sin(beta)
        val ye = cos(eps) * y - sin(eps) * z
        val ze = sin(eps) * y + cos(eps) * z
        val ra = atan2(ye, x)
        val dec = asin(ze.coerceIn(-1.0, 1.0))
        // Hora sidérea del lugar y altura.
        val gmst = Math.toRadians(280.46061837 + 360.98564736629 * (jd - 2_451_545.0))
        val hourAngle = gmst + Math.toRadians(longitude) - ra
        val phi = Math.toRadians(latitude)
        val alt = asin((sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(hourAngle)).coerceIn(-1.0, 1.0))
        return Math.toDegrees(alt) - 0.133
    }
}
