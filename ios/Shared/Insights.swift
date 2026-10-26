import Foundation

/// La frase del momento: de todo lo que pasa, elige lo más útil.
struct Insight: Equatable {
    let text: String
    /// Si habla de lluvia, la línea aparte de "próxima hora" sobra.
    let isAboutRain: Bool
}

/// Mismo orden que en Android: lluvia inminente, cuándo para, lluvia más tarde, UV alto, viento
/// fuerte, cambio de temperatura de mañana, amanecer o atardecer cercanos y diferencia con ayer.
/// (Las alertas oficiales todavía no están en iOS.)
enum Insights {
    static func headline(_ snapshot: WeatherSnapshot, now: Date = Date()) -> Insight? {
        let local = snapshot.nowAtPlace(now)
        if let minutes = snapshot.minutesUntilRain(now: now) {
            return Insight(text: WeatherFormat.rainSoonText(minutes), isAboutRain: true)
        }
        if let text = rainStops(snapshot, now: now) { return Insight(text: text, isAboutRain: true) }
        if let text = rainLater(snapshot, local: local, now: now) { return Insight(text: text, isAboutRain: true) }
        if let text = uvWarning(snapshot, local: local, now: now) { return Insight(text: text, isAboutRain: false) }
        if snapshot.current.windSpeed >= 40 {
            return Insight(text: "Viento fuerte: \(WeatherFormat.windText(snapshot.current.windSpeed))", isAboutRain: false)
        }
        if let text = tomorrowChange(snapshot, now: now) { return Insight(text: text, isAboutRain: false) }
        if let text = sunEvent(snapshot, local: local, now: now) { return Insight(text: text, isAboutRain: false) }
        if let text = versusYesterday(snapshot, now: now) { return Insight(text: text, isAboutRain: false) }
        return nil
    }

    /// Cuando no pasa nada destacable, igual queda una frase con el día.
    static func fallback(_ snapshot: WeatherSnapshot, now: Date = Date()) -> String {
        guard let today = snapshot.today(now: now) else {
            return WeatherCodes.description(snapshot.current.code)
        }
        let description = WeatherCodes.description(today.code).lowercased()
        return "Hoy: \(description), máxima de \(WeatherFormat.degrees(today.high)) y mínima de \(WeatherFormat.degrees(today.low))."
    }

    private static func isWet(_ code: Int) -> Bool { WeatherCodes.kind(code).isWet }

    /// Hoy con 3° o más de diferencia de máxima respecto de ayer.
    private static func versusYesterday(_ snapshot: WeatherSnapshot, now: Date) -> String? {
        guard let today = snapshot.today(now: now), let yesterday = snapshot.yesterday(now: now) else { return nil }
        let delta = today.high - yesterday.high
        if abs(delta) < 3 { return nil }
        let amount = WeatherFormat.degreesDelta(delta)
        return delta > 0 ? "Hoy \(amount) más cálido que ayer" : "Hoy \(amount) más fresco que ayer"
    }

    /// Si ahora llueve, a qué hora se espera que pare (dentro de 6 horas).
    private static func rainStops(_ snapshot: WeatherSnapshot, now: Date) -> String? {
        if !isWet(snapshot.current.code) && snapshot.current.precipitation < 0.1 { return nil }
        guard let dry = snapshot.upcomingHours(7, now: now).dropFirst().first(where: {
            !isWet($0.code) && ($0.precipitationProbability ?? 0) < 30
        }) else { return nil }
        // El valor de cada hora es de la hora anterior: el rato seco empieza una hora antes.
        let stopsAt = WeatherFormat.shortHour(dry.dateTime.addingTimeInterval(-3600))
        if WeatherCodes.kind(snapshot.current.code) == .snow {
            return "Para de nevar cerca de las \(stopsAt)"
        }
        return "Para de llover cerca de las \(stopsAt)"
    }

    /// Lluvia probable más tarde hoy (en las próximas 12 horas).
    private static func rainLater(_ snapshot: WeatherSnapshot, local: Date, now: Date) -> String? {
        let today = LocalTime.startOfDay(local)
        // La probabilidad de cada hora es la de la hora anterior: la lluvia empieza una hora antes.
        guard let rainy = snapshot.upcomingHours(14, now: now).dropFirst(2).first(where: {
            ($0.precipitationProbability ?? 0) >= 60
                && LocalTime.startOfDay($0.dateTime.addingTimeInterval(-3600)) == today
        }) else { return nil }
        return "Llevá paraguas: lluvia a las \(WeatherFormat.shortHour(rainy.dateTime.addingTimeInterval(-3600)))"
    }

    private static func uvWarning(_ snapshot: WeatherSnapshot, local: Date, now: Date) -> String? {
        if !snapshot.current.isDay || LocalTime.hour(local) >= 16 { return nil }
        guard let uv = snapshot.today(now: now)?.uvIndex, uv >= 6 else { return nil }
        return "UV \(WeatherCodes.uvLabel(uv)) al mediodía: usá protector"
    }

    /// Mañana con 5° o más de diferencia de máxima respecto de hoy.
    private static func tomorrowChange(_ snapshot: WeatherSnapshot, now: Date) -> String? {
        let days = snapshot.upcomingDays(2, now: now)
        guard days.count >= 2 else { return nil }
        let delta = days[1].high - days[0].high
        if abs(delta) < 5 { return nil }
        let amount = WeatherFormat.degreesDelta(delta)
        return delta < 0 ? "Mañana \(amount) más fresco" : "Mañana \(amount) más cálido"
    }

    private static func sunEvent(_ snapshot: WeatherSnapshot, local: Date, now: Date) -> String? {
        guard let today = snapshot.today(now: now),
              let sunrise = LocalTime.parse(today.sunrise),
              let sunset = LocalTime.parse(today.sunset) else { return nil }
        let toSunset = Int(sunset.timeIntervalSince(local) / 60)
        if (1...60).contains(toSunset) { return "Atardece en \(toSunset) min" }
        let toSunrise = Int(sunrise.timeIntervalSince(local) / 60)
        if (1...60).contains(toSunrise) { return "Amanece en \(toSunrise) min" }
        return nil
    }
}
