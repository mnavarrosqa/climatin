package com.mnavarrosqa.clima.data

import androidx.annotation.DrawableRes
import com.mnavarrosqa.clima.R

/** Códigos WMO que devuelve Open-Meteo. */
object WeatherCodes {

    fun description(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Mayormente despejado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        51, 53, 55 -> "Llovizna"
        56, 57 -> "Llovizna helada"
        61 -> "Lluvia leve"
        63 -> "Lluvia"
        65 -> "Lluvia fuerte"
        66, 67 -> "Lluvia helada"
        71 -> "Nevada leve"
        73 -> "Nevada"
        75 -> "Nevada fuerte"
        77 -> "Granizo fino"
        80, 81 -> "Chaparrones"
        82 -> "Chaparrones fuertes"
        85, 86 -> "Chaparrones de nieve"
        95 -> "Tormenta"
        96, 99 -> "Tormenta con granizo"
        else -> "Sin datos"
    }

    @DrawableRes
    fun icon(code: Int, isDay: Boolean = true): Int = when (code) {
        0, 1 -> if (isDay) R.drawable.ic_weather_clear_day else R.drawable.ic_weather_clear_night
        2 -> if (isDay) R.drawable.ic_weather_partly_cloudy_day else R.drawable.ic_weather_partly_cloudy_night
        3 -> R.drawable.ic_weather_cloudy
        45, 48 -> R.drawable.ic_weather_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_weather_drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_weather_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
        95, 96, 99 -> R.drawable.ic_weather_thunderstorm
        else -> R.drawable.ic_weather_cloudy
    }
}
