package com.mnavarrosqa.clima.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Cliente mínimo de https://open-meteo.com (gratis, sin clave). */
object OpenMeteo {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun forecast(latitude: Double, longitude: Double): Forecast = get(
        String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day," +
                "weather_code,wind_speed_10m,wind_direction_10m,precipitation,surface_pressure,visibility,uv_index" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
                "uv_index_max,precipitation_probability_max,precipitation_sum,wind_speed_10m_max" +
                "&minutely_15=precipitation&forecast_minutely_15=8" +
                "&timezone=auto&forecast_days=14",
            latitude,
            longitude,
        ),
        Forecast.serializer(),
    )

    /** Calidad del aire. Es opcional: si falla, se muestra el resto igual. */
    suspend fun airQuality(latitude: Double, longitude: Double): AirQuality? = runCatching {
        val response = get(
            String.format(
                Locale.US,
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f" +
                    "&current=european_aqi,pm2_5&timezone=auto",
                latitude,
                longitude,
            ),
            AirQualityResponse.serializer(),
        )
        AirQuality(europeanAqi = response.current.europeanAqi, pm25 = response.current.pm25)
    }.getOrNull()

    suspend fun searchCities(query: String): List<Place> {
        val response = get(
            "https://geocoding-api.open-meteo.com/v1/search?count=10&language=es&format=json&name=" +
                URLEncoder.encode(query.trim(), "UTF-8"),
            GeocodingResponse.serializer(),
        )
        return response.results.orEmpty().map { r ->
            Place(
                id = "geo:${r.id}",
                name = r.name,
                detail = listOfNotNull(r.admin1, r.country).distinct().joinToString(", ").ifBlank { null },
                latitude = r.latitude,
                longitude = r.longitude,
            )
        }
    }

    private suspend fun <T> get(url: String, deserializer: DeserializationStrategy<T>): T =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                val code = connection.responseCode
                if (code !in 200..299) error("Open-Meteo respondió $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString(deserializer, body)
            } finally {
                connection.disconnect()
            }
        }

    fun toSnapshot(
        forecast: Forecast,
        placeId: String,
        place: String?,
        latitude: Double,
        longitude: Double,
        airQuality: AirQuality?,
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
        }
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
                precipitationSum = d.precipitationSum.getOrNull(i),
                windMax = d.windMax.getOrNull(i),
            )
        }
        val minutely = forecast.minutely?.let { m ->
            m.time.indices.mapNotNull { i ->
                Minutely(m.time[i], m.precipitation.getOrNull(i) ?: return@mapNotNull null)
            }
        }.orEmpty()
        return WeatherSnapshot(
            placeId = placeId,
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
                pressure = c.pressure,
                visibility = c.visibility,
                uvIndex = c.uvIndex,
                windDirection = c.windDirection,
            ),
            hours = hours,
            days = days,
            minutely = minutely,
            airQuality = airQuality,
        )
    }
}

@Serializable
data class Forecast(
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto,
    @SerialName("minutely_15") val minutely: MinutelyDto? = null,
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
    @SerialName("surface_pressure") val pressure: Double? = null,
    val visibility: Double? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Double? = null,
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
    @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windMax: List<Double?> = emptyList(),
)

@Serializable
data class MinutelyDto(
    val time: List<String>,
    val precipitation: List<Double?>,
)

@Serializable
data class AirQualityResponse(val current: AirQualityCurrentDto)

@Serializable
data class AirQualityCurrentDto(
    @SerialName("european_aqi") val europeanAqi: Int? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
)

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult>? = null)

@Serializable
data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)
