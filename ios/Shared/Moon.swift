import Foundation

/// Fase lunar calculada a partir de una luna nueva conocida (6/1/2000 18:14 UTC).
enum Moon {
    private static let synodicDays = 29.530588853
    private static let referenceNewMoon: TimeInterval = 947_182_440

    /// 0 = luna nueva, 0.5 = llena, 1 = nueva otra vez. `localDay` es un día de pared del lugar.
    static func age(localDay: Date) -> Double {
        // Mediodía del día, como en Android (atTime(12, 0) en UTC).
        let noon = LocalTime.startOfDay(localDay).timeIntervalSince1970 + 12 * 3600
        let cycles = (noon - referenceNewMoon) / 86_400 / synodicDays
        return cycles - cycles.rounded(.down)
    }

    static func phaseName(age: Double) -> String {
        if age < 0.03 || age >= 0.97 { return "Luna nueva" }
        if age < 0.22 { return "Creciente" }
        if age < 0.28 { return "Cuarto creciente" }
        if age < 0.47 { return "Gibosa creciente" }
        if age < 0.53 { return "Luna llena" }
        if age < 0.72 { return "Gibosa menguante" }
        if age < 0.78 { return "Cuarto menguante" }
        return "Menguante"
    }

    static func symbolName(age: Double) -> String {
        if age < 0.03 || age >= 0.97 { return "moonphase.new.moon" }
        if age < 0.22 { return "moonphase.waxing.crescent" }
        if age < 0.28 { return "moonphase.first.quarter" }
        if age < 0.47 { return "moonphase.waxing.gibbous" }
        if age < 0.53 { return "moonphase.full.moon" }
        if age < 0.72 { return "moonphase.waning.gibbous" }
        if age < 0.78 { return "moonphase.last.quarter" }
        return "moonphase.waning.crescent"
    }

    /// Parte iluminada, de 0 (nueva) a 1 (llena).
    static func illumination(age: Double) -> Double {
        (1 - cos(2 * Double.pi * age)) / 2
    }
}
