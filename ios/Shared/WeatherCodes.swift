import Foundation

enum WeatherKind: String, Codable {
    case clear, partly, cloudy, fog, drizzle, rain, snow, storm

    /// Si llueve, llovizna, hay tormenta o nieva: la misma idea de "mojado" en todos los avisos.
    var isWet: Bool {
        self == .drizzle || self == .rain || self == .storm || self == .snow
    }
}

/// Códigos WMO que devuelve Open-Meteo.
enum WeatherCodes {
    static func kind(_ code: Int) -> WeatherKind {
        switch code {
        case 0, 1: return .clear
        case 2: return .partly
        case 3: return .cloudy
        case 45, 48: return .fog
        case 51, 53, 55, 56, 57: return .drizzle
        case 61, 63, 65, 66, 67, 80, 81, 82: return .rain
        case 71, 73, 75, 77, 85, 86: return .snow
        case 95, 96, 99: return .storm
        default: return .cloudy
        }
    }

    static func description(_ code: Int) -> String {
        switch code {
        case 0: return "Despejado"
        case 1: return "Mayormente despejado"
        case 2: return "Parcialmente nublado"
        case 3: return "Nublado"
        case 45, 48: return "Niebla"
        case 51, 53, 55: return "Llovizna"
        case 56, 57: return "Llovizna helada"
        case 61: return "Lluvia leve"
        case 63: return "Lluvia"
        case 65: return "Lluvia fuerte"
        case 66, 67: return "Lluvia helada"
        case 71: return "Nevada leve"
        case 73: return "Nevada"
        case 75: return "Nevada fuerte"
        case 77: return "Granizo fino"
        case 80, 81: return "Chaparrones"
        case 82: return "Chaparrones fuertes"
        case 85, 86: return "Chaparrones de nieve"
        case 95: return "Tormenta"
        case 96, 99: return "Tormenta con granizo"
        default: return "Sin datos"
        }
    }

    /// SF Symbol del estado. En Android son vectores propios; acá alcanza con los del sistema,
    /// que tienen versión multicolor.
    static func symbolName(code: Int, isDay: Bool) -> String {
        switch kind(code) {
        case .clear: return isDay ? "sun.max.fill" : "moon.stars.fill"
        case .partly: return isDay ? "cloud.sun.fill" : "cloud.moon.fill"
        case .cloudy: return "cloud.fill"
        case .fog: return "cloud.fog.fill"
        case .drizzle: return "cloud.drizzle.fill"
        case .rain: return (code == 65 || code == 82) ? "cloud.heavyrain.fill" : "cloud.rain.fill"
        case .snow: return "cloud.snow.fill"
        case .storm: return "cloud.bolt.rain.fill"
        }
    }

    static func uvLabel(_ uv: Double) -> String {
        if uv < 3 { return "bajo" }
        if uv < 6 { return "moderado" }
        if uv < 8 { return "alto" }
        if uv < 11 { return "muy alto" }
        return "extremo"
    }
}
