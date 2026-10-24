package com.mnavarrosqa.clima.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Color de una temperatura en una escala fija (en °C): violeta y azul con frío, verde templado,
 * amarillo y naranja con calor, rojo cuando quema. Como en el Clima de Apple o Mercury Weather,
 * de un vistazo se nota si un día es más fresco o más caluroso que otro.
 */
object TempColors {
    private val stops = listOf(
        -10.0 to Color(0xFF8E7CFF),
        0.0 to Color(0xFF5B9BFF),
        10.0 to Color(0xFF4FD1E8),
        17.0 to Color(0xFF7EE08A),
        23.0 to Color(0xFFFFD54F),
        29.0 to Color(0xFFFF9F43),
        35.0 to Color(0xFFFF5A4E),
        42.0 to Color(0xFFD9365E),
    )

    fun of(celsius: Double): Color {
        if (celsius <= stops.first().first) return stops.first().second
        if (celsius >= stops.last().first) return stops.last().second
        val upper = stops.indexOfFirst { it.first >= celsius }
        val (t0, c0) = stops[upper - 1]
        val (t1, c1) = stops[upper]
        return lerp(c0, c1, ((celsius - t0) / (t1 - t0)).toFloat())
    }

    /** Colores entre dos temperaturas, para pintar una barra o una curva. */
    fun between(min: Double, max: Double, steps: Int = 4): List<Color> =
        if (max - min < 0.5) listOf(of(min), of(max)) else List(steps + 1) { of(min + (max - min) * it / steps) }
}
