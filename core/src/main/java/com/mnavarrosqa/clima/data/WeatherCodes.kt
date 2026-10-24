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
        0 -> tr("Despejado", "Clear")
        1 -> tr("Mayormente despejado", "Mostly clear")
        2 -> tr("Parcialmente nublado", "Partly cloudy")
        3 -> tr("Nublado", "Cloudy")
        45, 48 -> tr("Niebla", "Fog")
        51, 53, 55 -> tr("Llovizna", "Drizzle")
        56, 57 -> tr("Llovizna helada", "Freezing drizzle")
        61 -> tr("Lluvia leve", "Light rain")
        63 -> tr("Lluvia", "Rain")
        65 -> tr("Lluvia fuerte", "Heavy rain")
        66, 67 -> tr("Lluvia helada", "Freezing rain")
        71 -> tr("Nevada leve", "Light snow")
        73 -> tr("Nevada", "Snow")
        75 -> tr("Nevada fuerte", "Heavy snow")
        77 -> tr("Granizo fino", "Snow grains")
        80, 81 -> tr("Chaparrones", "Showers")
        82 -> tr("Chaparrones fuertes", "Heavy showers")
        85, 86 -> tr("Chaparrones de nieve", "Snow showers")
        95 -> tr("Tormenta", "Thunderstorm")
        96, 99 -> tr("Tormenta con granizo", "Thunderstorm with hail")
        else -> tr("Sin datos", "No data")
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
        aqi < 20 -> tr("Buena", "Good")
        aqi < 40 -> tr("Aceptable", "Fair")
        aqi < 60 -> tr("Moderada", "Moderate")
        aqi < 80 -> tr("Mala", "Poor")
        aqi < 100 -> tr("Muy mala", "Very poor")
        else -> tr("Pésima", "Extremely poor")
    }

    fun uvLabel(uv: Double): String = when {
        uv < 3 -> tr("bajo", "low")
        uv < 6 -> tr("moderado", "moderate")
        uv < 8 -> tr("alto", "high")
        uv < 11 -> tr("muy alto", "very high")
        else -> tr("extremo", "extreme")
    }
}
