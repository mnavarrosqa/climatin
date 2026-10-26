import Foundation

/// Horas "de pared" del lugar del pronóstico.
///
/// Open-Meteo con timezone=auto manda horas locales sin zona ("2026-10-26T14:00"). Se guardan
/// como `Date` leídas en UTC: así se comparan y se formatean sin mezclarse con la zona del
/// teléfono (que puede ser otra si miramos una ciudad lejana) y sin saltos por horario de verano.
enum LocalTime {
    static let utc = TimeZone(secondsFromGMT: 0)!

    private static func makeFormatter(_ format: String) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = utc
        formatter.dateFormat = format
        return formatter
    }

    private static let dateTimeFormatter = makeFormatter("yyyy-MM-dd'T'HH:mm")
    private static let dateFormatter = makeFormatter("yyyy-MM-dd")

    /// "2026-10-26T14:00" o "2026-10-26".
    static func parse(_ text: String) -> Date? {
        dateTimeFormatter.date(from: text) ?? dateFormatter.date(from: text)
    }

    static func string(_ date: Date) -> String {
        dateTimeFormatter.string(from: date)
    }

    static func dayString(_ date: Date) -> String {
        dateFormatter.string(from: date)
    }

    /// Hora actual del lugar, expresada como hora de pared en UTC.
    static func wallClock(_ now: Date, zone: TimeZone) -> Date {
        now.addingTimeInterval(TimeInterval(zone.secondsFromGMT(for: now)))
    }

    static func startOfHour(_ date: Date) -> Date {
        Date(timeIntervalSince1970: (date.timeIntervalSince1970 / 3600).rounded(.down) * 3600)
    }

    static func startOfDay(_ date: Date) -> Date {
        Date(timeIntervalSince1970: (date.timeIntervalSince1970 / 86_400).rounded(.down) * 86_400)
    }

    /// Hora del día (0–23).
    static func hour(_ date: Date) -> Int {
        let secondsOfDay = date.timeIntervalSince1970 - startOfDay(date).timeIntervalSince1970
        return Int(secondsOfDay / 3600)
    }
}
