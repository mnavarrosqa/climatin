package com.mnavarrosqa.clima.data

import androidx.annotation.DrawableRes
import com.mnavarrosqa.clima.core.R

enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

/** Códigos WMO que devuelve Open-Meteo. */
object WeatherCodes {

    fun kind(code: Int): WeatherKind = when (code) {
        0, 1 -> WeatherKind.CLEAR
        2 -> WeatherKind.PARTLY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
        95, 96, 99 -> WeatherKind.STORM
        else -> WeatherKind.CLOUDY
    }

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
    fun icon(code: Int, isDay: Boolean = true): Int = when (kind(code)) {
        WeatherKind.CLEAR -> if (isDay) R.drawable.ic_weather_clear_day else R.drawable.ic_weather_clear_night
        WeatherKind.PARTLY -> if (isDay) R.drawable.ic_weather_partly_cloudy_day else R.drawable.ic_weather_partly_cloudy_night
        WeatherKind.CLOUDY -> R.drawable.ic_weather_cloudy
        WeatherKind.FOG -> R.drawable.ic_weather_fog
        WeatherKind.DRIZZLE -> R.drawable.ic_weather_drizzle
        WeatherKind.RAIN -> R.drawable.ic_weather_rain
        WeatherKind.SNOW -> R.drawable.ic_weather_snow
        WeatherKind.STORM -> R.drawable.ic_weather_thunderstorm
    }

    /** Índice europeo de calidad del aire. */
    fun airQualityLabel(aqi: Int): String = when {
        aqi < 20 -> "Buena"
        aqi < 40 -> "Aceptable"
        aqi < 60 -> "Moderada"
        aqi < 80 -> "Mala"
        aqi < 100 -> "Muy mala"
        else -> "Pésima"
    }

    fun uvLabel(uv: Double): String = when {
        uv < 3 -> "bajo"
        uv < 6 -> "moderado"
        uv < 8 -> "alto"
        uv < 11 -> "muy alto"
        else -> "extremo"
    }
}
