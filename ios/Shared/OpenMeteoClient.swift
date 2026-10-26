import Foundation

enum OpenMeteoError: LocalizedError {
    case badURL
    case http(Int)

    var errorDescription: String? {
        switch self {
        case .badURL: return "No se pudo armar la consulta."
        case .http(let code): return "Open-Meteo respondió \(code)."
        }
    }
}

/// Cliente mínimo de https://open-meteo.com (gratis, sin clave).
struct OpenMeteoClient {
    /// La misma consulta que la app de Android (OpenMeteo.kt), para que las dos muestren lo mismo.
    static let forecastFields =
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day," +
        "weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,pressure_msl,visibility,uv_index" +
        "&hourly=temperature_2m,weather_code,precipitation_probability,is_day," +
        "apparent_temperature,wind_speed_10m,precipitation,pressure_msl," +
        "uv_index,relative_humidity_2m,dew_point_2m,visibility,wind_gusts_10m" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
        "uv_index_max,precipitation_probability_max,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max" +
        "&minutely_15=precipitation&forecast_minutely_15=8" +
        "&timezone=auto&past_days=1"

    var session: URLSession = .shared
    /// Diez días alcanzan para la tarjeta y achican la caché que lee el widget.
    var days: Int = 10

    func snapshot(for place: Place, now: Date = Date()) async throws -> WeatherSnapshot {
        let response = try await self.forecast(latitude: place.latitude, longitude: place.longitude)
        return Self.makeSnapshot(response, place: place, fetchedAt: now)
    }

    func forecast(latitude: Double, longitude: Double) async throws -> ForecastDTO {
        // String(format:) sin locale usa siempre punto decimal, como Locale.US en Android.
        let coordinates = "latitude=" + String(format: "%.4f", latitude) + "&longitude=" + String(format: "%.4f", longitude)
        let text = "https://api.open-meteo.com/v1/forecast?" + coordinates + Self.forecastFields + "&forecast_days=\(days)"
        guard let url = URL(string: text) else { throw OpenMeteoError.badURL }
        return try await get(url)
    }

    func searchCities(_ query: String) async throws -> [Place] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }
        var components = URLComponents(string: "https://geocoding-api.open-meteo.com/v1/search")
        components?.queryItems = [
            URLQueryItem(name: "count", value: "10"),
            URLQueryItem(name: "format", value: "json"),
            URLQueryItem(name: "language", value: "es"),
            URLQueryItem(name: "name", value: trimmed),
        ]
        guard let url = components?.url else { throw OpenMeteoError.badURL }
        let response: GeocodingResponse = try await get(url)
        return (response.results ?? []).map { result in
            var parts: [String] = []
            for part in [result.admin1, result.country] {
                if let part = part, !part.isEmpty, !parts.contains(part) { parts.append(part) }
            }
            return Place(
                id: "geo:\(result.id)",
                name: result.name,
                detail: parts.isEmpty ? nil : parts.joined(separator: ", "),
                latitude: result.latitude,
                longitude: result.longitude
            )
        }
    }

    private func get<T: Decodable>(_ url: URL) async throws -> T {
        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw OpenMeteoError.http(http.statusCode)
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    /// Arma lo que se guarda: separa las horas que ya pasaron de las que vienen.
    static func makeSnapshot(_ forecast: ForecastDTO, place: Place, fetchedAt: Date) -> WeatherSnapshot {
        let zone: TimeZone = forecast.timezone.flatMap { TimeZone(identifier: $0) }
            ?? TimeZone(secondsFromGMT: forecast.utcOffsetSeconds ?? 0)
            ?? LocalTime.utc
        let c = forecast.current
        let currentTime = LocalTime.parse(c.time) ?? LocalTime.wallClock(fetchedAt, zone: zone)
        let currentHour = LocalTime.startOfHour(currentTime)
        // Desde ayer a las 0: las que ya pasaron sirven para comparar con ayer.
        let since = LocalTime.startOfDay(currentHour).addingTimeInterval(-86_400)

        let h = forecast.hourly
        var pastHours: [Hour] = []
        var hours: [Hour] = []
        for i in h.time.indices {
            guard let temperature = value(h.temperature, i),
                  let time = LocalTime.parse(h.time[i]),
                  time >= since else { continue }
            let hour = Hour(
                time: h.time[i],
                dateTime: time,
                temperature: temperature,
                code: int(value(h.weatherCode, i)) ?? 0,
                precipitationProbability: int(value(h.precipitationProbability, i)),
                isDay: int(value(h.isDay, i)) == 1,
                feelsLike: value(h.feelsLike, i),
                windSpeed: value(h.windSpeed, i),
                precipitation: value(h.precipitation, i),
                pressure: value(h.pressure, i),
                uvIndex: value(h.uvIndex, i),
                humidity: int(value(h.humidity, i)),
                dewPoint: value(h.dewPoint, i),
                visibility: value(h.visibility, i),
                windGusts: value(h.windGusts, i)
            )
            if time < currentHour { pastHours.append(hour) } else { hours.append(hour) }
        }

        let d = forecast.daily
        var days: [Day] = []
        for i in d.time.indices {
            // Sin máxima, mínima o sol (días polares) el día no se puede dibujar: se saltea.
            guard let date = LocalTime.parse(d.time[i]),
                  let high = value(d.high, i),
                  let low = value(d.low, i),
                  let sunrise = value(d.sunrise, i),
                  let sunset = value(d.sunset, i) else { continue }
            days.append(Day(
                date: d.time[i],
                localDate: date,
                code: int(value(d.weatherCode, i)) ?? 0,
                high: high,
                low: low,
                sunrise: sunrise,
                sunset: sunset,
                uvIndex: value(d.uvIndex, i),
                precipitationProbability: int(value(d.precipitationProbability, i)),
                precipitationSum: value(d.precipitationSum, i),
                windMax: value(d.windMax, i)
            ))
        }

        var minutely: [Minutely] = []
        if let m = forecast.minutely15 {
            for i in m.time.indices {
                guard let time = LocalTime.parse(m.time[i]), let mm = value(m.precipitation, i) else { continue }
                minutely.append(Minutely(time: m.time[i], dateTime: time, precipitation: mm))
            }
        }

        return WeatherSnapshot(
            placeId: place.id,
            place: place.name,
            latitude: place.latitude,
            longitude: place.longitude,
            utcOffsetSeconds: forecast.utcOffsetSeconds ?? 0,
            timezone: forecast.timezone,
            fetchedAt: fetchedAt,
            current: Current(
                time: c.time,
                temperature: c.temperature,
                feelsLike: c.feelsLike ?? c.temperature,
                humidity: int(c.humidity) ?? 0,
                isDay: int(c.isDay) == 1,
                code: int(c.weatherCode) ?? 0,
                windSpeed: c.windSpeed ?? 0,
                precipitation: c.precipitation ?? 0,
                pressure: c.pressure,
                visibility: c.visibility,
                uvIndex: c.uvIndex,
                windDirection: c.windDirection,
                windGusts: c.windGusts
            ),
            hours: hours,
            pastHours: pastHours,
            days: days,
            minutely: minutely
        )
    }
}

/// Elemento `index` de una lista de Open-Meteo, que puede faltar o traer nulls.
private func value<T>(_ array: [T?]?, _ index: Int) -> T? {
    guard let array = array, index >= 0, index < array.count else { return nil }
    return array[index]
}

/// Los enteros de Open-Meteo se leen como Double: así no falla si alguno llega como "65.0".
private func int(_ number: Double?) -> Int? {
    guard let number = number, number.isFinite else { return nil }
    return Int(number.rounded())
}

// MARK: - Respuestas de Open-Meteo

struct ForecastDTO: Decodable {
    let utcOffsetSeconds: Int?
    let timezone: String?
    let current: CurrentDTO
    let hourly: HourlyDTO
    let daily: DailyDTO
    let minutely15: MinutelyDTO?

    enum CodingKeys: String, CodingKey {
        case utcOffsetSeconds = "utc_offset_seconds"
        case timezone, current, hourly, daily
        case minutely15 = "minutely_15"
    }
}

struct CurrentDTO: Decodable {
    let time: String
    let temperature: Double
    let feelsLike: Double?
    let humidity: Double?
    let isDay: Double?
    let weatherCode: Double?
    let windSpeed: Double?
    let precipitation: Double?
    /// Al nivel del mar: así se compara con los 1013 hPa de siempre aunque el lugar esté alto.
    let pressure: Double?
    let visibility: Double?
    let uvIndex: Double?
    let windDirection: Double?
    let windGusts: Double?

    enum CodingKeys: String, CodingKey {
        case time
        case temperature = "temperature_2m"
        case feelsLike = "apparent_temperature"
        case humidity = "relative_humidity_2m"
        case isDay = "is_day"
        case weatherCode = "weather_code"
        case windSpeed = "wind_speed_10m"
        case precipitation
        case pressure = "pressure_msl"
        case visibility
        case uvIndex = "uv_index"
        case windDirection = "wind_direction_10m"
        case windGusts = "wind_gusts_10m"
    }
}

struct HourlyDTO: Decodable {
    let time: [String]
    let temperature: [Double?]?
    let weatherCode: [Double?]?
    let precipitationProbability: [Double?]?
    let isDay: [Double?]?
    let feelsLike: [Double?]?
    let windSpeed: [Double?]?
    let precipitation: [Double?]?
    let pressure: [Double?]?
    let uvIndex: [Double?]?
    let humidity: [Double?]?
    let dewPoint: [Double?]?
    let visibility: [Double?]?
    let windGusts: [Double?]?

    enum CodingKeys: String, CodingKey {
        case time
        case temperature = "temperature_2m"
        case weatherCode = "weather_code"
        case precipitationProbability = "precipitation_probability"
        case isDay = "is_day"
        case feelsLike = "apparent_temperature"
        case windSpeed = "wind_speed_10m"
        case precipitation
        case pressure = "pressure_msl"
        case uvIndex = "uv_index"
        case humidity = "relative_humidity_2m"
        case dewPoint = "dew_point_2m"
        case visibility
        case windGusts = "wind_gusts_10m"
    }
}

struct DailyDTO: Decodable {
    let time: [String]
    let weatherCode: [Double?]?
    let high: [Double?]?
    let low: [Double?]?
    let sunrise: [String?]?
    let sunset: [String?]?
    let uvIndex: [Double?]?
    let precipitationProbability: [Double?]?
    let precipitationSum: [Double?]?
    let windMax: [Double?]?
    let windGustsMax: [Double?]?

    enum CodingKeys: String, CodingKey {
        case time
        case weatherCode = "weather_code"
        case high = "temperature_2m_max"
        case low = "temperature_2m_min"
        case sunrise, sunset
        case uvIndex = "uv_index_max"
        case precipitationProbability = "precipitation_probability_max"
        case precipitationSum = "precipitation_sum"
        case windMax = "wind_speed_10m_max"
        case windGustsMax = "wind_gusts_10m_max"
    }
}

struct MinutelyDTO: Decodable {
    let time: [String]
    let precipitation: [Double?]?
}

struct GeocodingResponse: Decodable {
    let results: [GeocodingResult]?
}

struct GeocodingResult: Decodable {
    let id: Int
    let name: String
    let latitude: Double
    let longitude: Double
    let country: String?
    let admin1: String?
}
