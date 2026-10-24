package com.mnavarrosqa.clima.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.WeatherSnapshot
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * El cielo según la hora: cerca del amanecer y del atardecer los colores se entibian (rosa,
 * naranja), con más fuerza si está despejado que si está nublado o lloviendo.
 */
object SkyTime {
    private val Dawn = listOf(Color(0xFF34407A), Color(0xFFE8A0A0), Color(0xFFFFD6A8))
    private val Dusk = listOf(Color(0xFF2A2B66), Color(0xFFC0607A), Color(0xFFFF9E5E))

    fun gradient(kind: WeatherKind, isDay: Boolean, snapshot: WeatherSnapshot?): SkyGradient =
        colors(kind, isDay, snapshot).toGradient()

    fun colors(kind: WeatherKind, isDay: Boolean, snapshot: WeatherSnapshot?): List<Color> {
        val base = skyColors(kind, isDay)
        val today = snapshot?.today() ?: return base
        val now = snapshot.nowAtPlace()
        val fromSunrise = minutesBetween(today.sunrise, now) ?: return base
        val fromSunset = minutesBetween(today.sunset, now) ?: return base
        // Una hora alrededor del amanecer; desde una hora y cuarto antes del atardecer hasta 50 min después.
        val (warm, weight) = when {
            abs(fromSunrise) <= 60 -> Dawn to 1f - abs(fromSunrise) / 60f
            fromSunset in -75L..50L -> Dusk to 1f - abs(fromSunset) / (if (fromSunset < 0) 75f else 50f)
            else -> return base
        }
        val strength = weight * when (kind) {
            WeatherKind.CLEAR, WeatherKind.PARTLY -> 0.9f
            WeatherKind.CLOUDY, WeatherKind.FOG, WeatherKind.SNOW -> 0.45f
            else -> 0.3f
        }
        return base.indices.map { lerp(base[it], warm[it], strength) }
    }

    private fun minutesBetween(iso: String, now: LocalDateTime): Long? =
        runCatching { Duration.between(LocalDateTime.parse(iso), now).toMinutes() }.getOrNull()
}
