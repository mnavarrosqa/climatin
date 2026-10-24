package com.mnavarrosqa.clima.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Cliente mínimo de https://open-meteo.com (gratis, sin clave). */
object OpenMeteo {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forecast(latitude: Double, longitude: Double): Forecast = withContext(Dispatchers.IO) {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day," +
                    "weather_code,wind_speed_10m,precipitation" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
                    "uv_index_max,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=7",
                latitude,
                longitude,
            ),
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val code = connection.responseCode
            if (code !in 200..299) error("Open-Meteo respondió $code")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString(Forecast.serializer(), body)
        } finally {
            connection.disconnect()
        }
    }

    fun toSnapshot(
        forecast: Forecast,
        place: String?,
        latitude: Double,
        longitude: Double,
        fetchedAt: Long,
    ): WeatherSnapshot {
        val c = forecast.current
        val currentHour = LocalDateTime.parse(c.time).truncatedTo(ChronoUnit.HOURS)
        val h = forecast.hourly
        val hours = h.time.indices.mapNotNull { i ->
            val temp = h.temperature.getOrNull(i) ?: return@mapNotNull null
            val time = h.time[i]
            if (LocalDateTime.parse(time).isBefore(currentHour)) return@mapNotNull null
            Hour(
                time = time,
                temperature = temp,
                code = h.weatherCode.getOrNull(i) ?: 0,
                precipitationProbability = h.precipitationProbability.getOrNull(i),
                isDay = h.isDay.getOrNull(i) == 1,
            )
        }.take(48)
        val d = forecast.daily
        val days = d.time.indices.mapNotNull { i ->
            Day(
                date = d.time[i],
                code = d.weatherCode.getOrNull(i) ?: 0,
                max = d.max.getOrNull(i) ?: return@mapNotNull null,
                min = d.min.getOrNull(i) ?: return@mapNotNull null,
                sunrise = d.sunrise.getOrNull(i) ?: return@mapNotNull null,
                sunset = d.sunset.getOrNull(i) ?: return@mapNotNull null,
                uvIndex = d.uvIndex.getOrNull(i),
                precipitationProbability = d.precipitationProbability.getOrNull(i),
            )
        }
        return WeatherSnapshot(
            place = place,
            latitude = latitude,
            longitude = longitude,
            utcOffsetSeconds = forecast.utcOffsetSeconds,
            fetchedAt = fetchedAt,
            current = Current(
                time = c.time,
                temperature = c.temperature,
                feelsLike = c.feelsLike,
                humidity = c.humidity,
                isDay = c.isDay == 1,
                code = c.weatherCode,
                windSpeed = c.windSpeed,
                precipitation = c.precipitation,
            ),
            hours = hours,
            days = days,
        )
    }
}

@Serializable
data class Forecast(
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto,
)

@Serializable
data class CurrentDto(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val feelsLike: Double,
    @SerialName("relative_humidity_2m") val humidity: Int,
    @SerialName("is_day") val isDay: Int,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    val precipitation: Double = 0.0,
)

@Serializable
data class HourlyDto(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?>,
)

@Serializable
data class DailyDto(
    val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int?>,
    @SerialName("temperature_2m_max") val max: List<Double?>,
    @SerialName("temperature_2m_min") val min: List<Double?>,
    val sunrise: List<String?>,
    val sunset: List<String?>,
    @SerialName("uv_index_max") val uvIndex: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbability: List<Int?> = emptyList(),
)
