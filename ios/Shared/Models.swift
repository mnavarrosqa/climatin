import Foundation

/// Id reservado para "donde estoy".
let currentPlaceId = "current"

/// Un lugar de la lista: la ubicación actual o una ciudad agregada desde el buscador.
struct Place: Codable, Identifiable, Hashable {
    var id: String
    var name: String
    var detail: String?
    var latitude: Double
    var longitude: Double

    var isCurrentLocation: Bool { id == currentPlaceId }
}

/// Lo que guardamos en caché por lugar y muestran la app y el widget.
/// Todas las fechas `Date` de acá son horas de pared del lugar (ver `LocalTime`), salvo `fetchedAt`.
struct WeatherSnapshot: Codable, Equatable {
    var placeId: String
    var place: String?
    var latitude: Double
    var longitude: Double
    var utcOffsetSeconds: Int
    /// Zona IANA ("America/Santiago"): con ella la hora del lugar sigue bien aunque cambie el horario de verano.
    var timezone: String?
    /// Momento real de la descarga (no hora de pared).
    var fetchedAt: Date
    var current: Current
    /// Desde la hora actual en adelante.
    var hours: [Hour]
    /// Las que ya pasaron desde ayer a las 0.
    var pastHours: [Hour]
    /// Incluye ayer (past_days=1), para comparar.
    var days: [Day]
    var minutely: [Minutely]

    var zone: TimeZone {
        if let timezone, let zone = TimeZone(identifier: timezone) { return zone }
        return TimeZone(secondsFromGMT: utcOffsetSeconds) ?? LocalTime.utc
    }

    /// Hora actual en el lugar del pronóstico, no en el teléfono.
    func nowAtPlace(_ now: Date = Date()) -> Date {
        LocalTime.wallClock(now, zone: zone)
    }

    /// Horas desde la actual. La caché puede tener un rato, así que se recorta al mostrar.
    func upcomingHours(_ count: Int, now: Date = Date()) -> [Hour] {
        let from = LocalTime.startOfHour(nowAtPlace(now))
        guard let start = hours.firstIndex(where: { $0.dateTime >= from }) else { return [] }
        return Array(hours[start..<min(hours.count, start + count)])
    }

    /// Si el pronóstico tiene más de una hora, "ahora" se toma de la hora actual del pronóstico
    /// por hora: el dato de cuando se bajó ya no corresponde.
    func withCurrentFromHours(now: Date = Date()) -> WeatherSnapshot {
        if now.timeIntervalSince(fetchedAt) < 3600 { return self }
        guard let hour = upcomingHours(1, now: now).first,
              hour.dateTime == LocalTime.startOfHour(nowAtPlace(now)) else { return self }
        var copy = self
        copy.current.temperature = hour.temperature
        copy.current.code = hour.code
        copy.current.isDay = hour.isDay
        copy.current.feelsLike = hour.feelsLike ?? current.feelsLike
        copy.current.windSpeed = hour.windSpeed ?? current.windSpeed
        copy.current.precipitation = hour.precipitation ?? current.precipitation
        copy.current.pressure = hour.pressure ?? current.pressure
        copy.current.uvIndex = hour.uvIndex ?? current.uvIndex
        copy.current.humidity = hour.humidity ?? current.humidity
        copy.current.visibility = hour.visibility ?? current.visibility
        copy.current.windGusts = hour.windGusts ?? current.windGusts
        return copy
    }

    func upcomingDays(_ count: Int, now: Date = Date()) -> [Day] {
        let today = LocalTime.startOfDay(nowAtPlace(now))
        return Array(days.filter { $0.localDate >= today }.prefix(count))
    }

    func today(now: Date = Date()) -> Day? {
        upcomingDays(1, now: now).first
    }

    func yesterday(now: Date = Date()) -> Day? {
        let date = LocalTime.startOfDay(nowAtPlace(now)).addingTimeInterval(-86_400)
        return days.first { $0.localDate == date }
    }

    /// Minutos hasta que empiece a llover, si va a llover en las próximas dos horas y ahora no llueve.
    /// Usa el pronóstico cada 15 minutos y, si no hay, la probabilidad por hora.
    func minutesUntilRain(now: Date = Date()) -> Int? {
        if current.precipitation >= 0.1 || WeatherCodes.kind(current.code).isWet { return nil }
        let local = nowAtPlace(now)
        let limit = local.addingTimeInterval(2 * 3600)
        // Cada valor es del tramo anterior a su hora: el tramo empieza 15 minutos (o una hora) antes.
        if let slot = minutely.first(where: {
            $0.precipitation >= 0.1 && $0.dateTime > local && $0.dateTime.addingTimeInterval(-900) < limit
        }) {
            return max(5, Int(slot.dateTime.addingTimeInterval(-900).timeIntervalSince(local) / 60))
        }
        // Si hay detalle cada 15 minutos y dice seco, no se lo contradice con la probabilidad por hora.
        if !minutely.isEmpty { return nil }
        guard let hour = upcomingHours(4, now: now).dropFirst().first(where: {
            ($0.precipitationProbability ?? 0) >= 60 && $0.dateTime.addingTimeInterval(-3600) < limit
        }) else { return nil }
        return max(5, Int(hour.dateTime.addingTimeInterval(-3600).timeIntervalSince(local) / 60))
    }
}

struct Current: Codable, Equatable {
    var time: String
    var temperature: Double
    var feelsLike: Double
    var humidity: Int
    var isDay: Bool
    var code: Int
    var windSpeed: Double
    var precipitation: Double
    var pressure: Double? = nil
    var visibility: Double? = nil
    var uvIndex: Double? = nil
    var windDirection: Double? = nil
    var windGusts: Double? = nil
}

struct Hour: Codable, Equatable, Identifiable {
    var time: String
    /// Ya leída: los gráficos y el widget la consultan una y otra vez.
    var dateTime: Date
    var temperature: Double
    var code: Int
    var precipitationProbability: Int?
    var isDay: Bool
    var feelsLike: Double? = nil
    var windSpeed: Double? = nil
    var precipitation: Double? = nil
    var pressure: Double? = nil
    var uvIndex: Double? = nil
    var humidity: Int? = nil
    var dewPoint: Double? = nil
    var visibility: Double? = nil
    var windGusts: Double? = nil

    var id: String { time }
}

struct Day: Codable, Equatable, Identifiable {
    var date: String
    var localDate: Date
    var code: Int
    var high: Double
    var low: Double
    var sunrise: String
    var sunset: String
    var uvIndex: Double?
    var precipitationProbability: Int?
    var precipitationSum: Double? = nil
    var windMax: Double? = nil

    var id: String { date }
}

struct Minutely: Codable, Equatable {
    var time: String
    var dateTime: Date
    var precipitation: Double
}
