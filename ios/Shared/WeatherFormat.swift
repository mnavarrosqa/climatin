import Foundation

/// Textos de grados, horas y días. Por ahora todo en métrico y 24 h, como el default de Android.
enum WeatherFormat {
    /// Redondeo como `roundToInt` de Kotlin (la mitad va hacia arriba), para que iOS y Android
    /// muestren el mismo número con el mismo dato.
    static func roundToInt(_ value: Double) -> Int {
        guard value.isFinite else { return 0 }
        return Int((value + 0.5).rounded(.down))
    }

    static func degrees(_ value: Double) -> String {
        "\(roundToInt(value))°"
    }

    /// Diferencia de temperatura sin signo ("6°").
    static func degreesDelta(_ value: Double) -> String {
        "\(roundToInt(abs(value)))°"
    }

    private static func makeFormatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "es_AR")
        // Las fechas del pronóstico son horas de pared guardadas en UTC (ver LocalTime).
        formatter.timeZone = LocalTime.utc
        formatter.dateFormat = format
        return formatter
    }

    private static let hourMinute = makeFormatter("HH:mm")
    private static let hourOnly = makeFormatter("HH")
    private static let weekdayFormatter = makeFormatter("EEE")

    static func shortHour(_ local: Date) -> String {
        hourMinute.string(from: local)
    }

    /// Etiqueta de la tira de horas: "15 h".
    static func hourLabel(_ local: Date) -> String {
        hourOnly.string(from: local) + " h"
    }

    /// "06:42" desde "2026-10-26T06:42".
    static func time(of iso: String) -> String {
        guard let date = LocalTime.parse(iso) else { return "--" }
        return shortHour(date)
    }

    /// "Sáb", "Dom"... sin el punto de la abreviatura.
    static func weekday(_ local: Date) -> String {
        var text = weekdayFormatter.string(from: local)
        while text.hasSuffix(".") { text.removeLast() }
        return text.capitalizedFirst
    }

    static func dayLabel(_ day: Date, today: Date) -> String {
        let start = LocalTime.startOfDay(today)
        let date = LocalTime.startOfDay(day)
        if date == start { return "Hoy" }
        if date == start.addingTimeInterval(86_400) { return "Mañana" }
        return weekday(day)
    }

    static func windText(_ kmh: Double) -> String {
        "\(roundToInt(kmh)) km/h"
    }

    /// Punto cardinal de donde viene el viento.
    static func windFrom(_ degrees: Double) -> String {
        let names = ["N", "NE", "E", "SE", "S", "SO", "O", "NO"]
        let normalized = (degrees.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
        return names[roundToInt(normalized / 45) % 8]
    }

    static func rainSoonText(_ minutes: Int) -> String {
        if minutes <= 20 { return "Lluvia en unos minutos" }
        if minutes < 55 { return "Lluvia en unos \((minutes + 4) / 5 * 5) min" }
        if minutes < 90 { return "Lluvia en la próxima hora" }
        return "Lluvia en menos de 2 horas"
    }

    /// Cuánto hace que se bajó el pronóstico: "recién", "hace 12 min", "hace 3 h" o "el 23/10".
    static func ageText(_ fetchedAt: Date, now: Date = Date()) -> String {
        let minutes = Int(now.timeIntervalSince(fetchedAt) / 60)
        if minutes < 1 { return "recién" }
        if minutes < 60 { return "hace \(minutes) min" }
        if minutes < 24 * 60 { return "hace \(minutes / 60) h" }
        let parts = Calendar.current.dateComponents([.day, .month], from: fetchedAt)
        return "el \(parts.day ?? 0)/\(parts.month ?? 0)"
    }
}

extension String {
    var capitalizedFirst: String {
        guard let first = first else { return self }
        return String(first).uppercased() + String(dropFirst())
    }
}
