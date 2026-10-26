import SwiftUI

/// Colores del cielo (arriba, medio, horizonte) según el clima y si es de día.
/// Son los mismos de Sky.kt en Android.
enum Sky {
    static func colors(kind: WeatherKind, isDay: Bool) -> [Color] {
        switch kind {
        case .clear:
            return isDay ? sky(0x1F5FCF, 0x7DB9FF, 0xBFE0FF) : sky(0x060B1F, 0x16224A, 0x2C3F70)
        case .partly:
            return isDay ? sky(0x2A64C8, 0x86BDF5, 0xC8E2FA) : sky(0x0A1128, 0x1D2A50, 0x33456F)
        case .cloudy:
            return isDay ? sky(0x4C6684, 0x93A9C0, 0xC2D0DE) : sky(0x151C28, 0x283344, 0x3C495C)
        case .fog:
            return isDay ? sky(0x7A8796, 0xB3BDC8, 0xD6DCE2) : sky(0x22272F, 0x383F49, 0x4D5560)
        case .drizzle, .rain:
            return isDay ? sky(0x33465C, 0x62778D, 0x8396AA) : sky(0x0E141D, 0x1E2836, 0x2E3B4C)
        case .snow:
            return isDay ? sky(0x6A80A0, 0xAFC2D8, 0xE2EAF3) : sky(0x18202D, 0x2E3A4D, 0x475468)
        case .storm:
            return isDay ? sky(0x1F2533, 0x3A4356, 0x535D72) : sky(0x090C13, 0x171C27, 0x262D3B)
        }
    }

    static func gradient(kind: WeatherKind, isDay: Bool) -> LinearGradient {
        LinearGradient(colors: colors(kind: kind, isDay: isDay), startPoint: .top, endPoint: .bottom)
    }

    /// Sin pronóstico todavía, un cielo despejado de día.
    static func gradient(for snapshot: WeatherSnapshot?) -> LinearGradient {
        guard let snapshot = snapshot else { return gradient(kind: .clear, isDay: true) }
        return gradient(kind: WeatherCodes.kind(snapshot.current.code), isDay: snapshot.current.isDay)
    }

    /// Color de una temperatura para las barras de rango: frío azul, templado verde, calor rojo.
    static func temperatureColor(_ celsius: Double) -> Color {
        if celsius < 0 { return Color(hex: 0x6FA8FF) }
        if celsius < 10 { return Color(hex: 0x5CC8E8) }
        if celsius < 18 { return Color(hex: 0x7ED9A0) }
        if celsius < 24 { return Color(hex: 0xF2D65C) }
        if celsius < 30 { return Color(hex: 0xF5A04A) }
        return Color(hex: 0xEF6448)
    }

    private static func sky(_ top: UInt32, _ middle: UInt32, _ bottom: UInt32) -> [Color] {
        [Color(hex: top), Color(hex: middle), Color(hex: bottom)]
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
