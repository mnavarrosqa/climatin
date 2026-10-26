import Foundation

/// Qué tan fuerte llueve, en mm por hora (umbrales habituales de los servicios meteorológicos).
enum RainIntensity {
    case light, moderate, heavy

    var label: String {
        switch self {
        case .light: return "débil"
        case .moderate: return "moderada"
        case .heavy: return "fuerte"
        }
    }

    static func of(_ mmPerHour: Double) -> RainIntensity {
        if mmPerHour >= 7.6 { return .heavy }
        if mmPerHour >= 2.5 { return .moderate }
        return .light
    }
}

/// Lo que va a pasar con la lluvia en las próximas horas.
enum RainOutlook: Equatable {
    /// Empieza en `inMinutes`; dura `lastsMinutes` (nil si sigue más allá de lo que se sabe).
    case starts(inMinutes: Int, lastsMinutes: Int?, intensity: RainIntensity)
    /// Está lloviendo y para en `inMinutes`.
    case stops(inMinutes: Int)

    /// "Lluvia moderada en unos 20 min" o "Para de llover en unos 15 min".
    var text: String {
        switch self {
        case .stops(let inMinutes):
            if inMinutes <= 20 { return "Para de llover en unos minutos" }
            return "Para de llover en unos \(Self.roundTo5(inMinutes)) min"
        case .starts(let inMinutes, _, let intensity):
            let start: String
            if inMinutes <= 20 {
                start = "en unos minutos"
            } else if inMinutes < 90 {
                start = "en unos \(Self.roundTo5(inMinutes)) min"
            } else {
                start = "en unas \((inMinutes + 30) / 60) horas"
            }
            return "Lluvia \(intensity.label) \(start)"
        }
    }

    /// Cuánto dura la lluvia que viene: "Por 1 h 30 min" o "Por un buen rato".
    var durationText: String? {
        guard case .starts(_, let lasts, _) = self else { return nil }
        guard let minutes = lasts else { return "Por un buen rato" }
        if minutes < 60 { return "Por unos \(minutes) min" }
        if minutes % 60 == 0 { return "Por \(minutes / 60) h" }
        return "Por \(minutes / 60) h \(minutes % 60) min"
    }

    private static func roundTo5(_ minutes: Int) -> Int { (minutes + 4) / 5 * 5 }
}

/// Un tramo del pronóstico con su lluvia en mm por hora.
private struct RainSlot {
    let start: Date
    let minutes: Int
    let mmPerHour: Double

    var end: Date { start.addingTimeInterval(TimeInterval(minutes * 60)) }

    var isWet: Bool { mmPerHour >= 0.4 || (minutes == 60 && mmPerHour >= 0.2) }
}

extension WeatherSnapshot {
    /// Mira la lluvia de cada 15 minutos (2 horas) y después la de cada hora, y dice si empieza o
    /// para dentro de `withinMinutes`. En Open-Meteo la lluvia de cada hora (y de cada cuarto de
    /// hora) es la del tramo anterior: el de las 15:00 es de 14:00 a 15:00. Una hora sin
    /// milímetros pero con 60% o más de probabilidad cuenta como lluvia débil.
    func rainOutlook(withinMinutes: Int = 120, now: Date = Date()) -> RainOutlook? {
        let local = nowAtPlace(now)
        var slots: [RainSlot] = []
        // Solo los tramos que terminan después de ahora.
        for item in minutely where item.dateTime > local {
            slots.append(RainSlot(start: item.dateTime.addingTimeInterval(-900), minutes: 15, mmPerHour: item.precipitation * 4))
        }
        let coveredUntil = slots.last?.end ?? local.addingTimeInterval(-60)
        for hour in upcomingHours(8, now: now) where hour.dateTime > coveredUntil && hour.dateTime > local {
            let probability = hour.precipitationProbability ?? 0
            let mm = hour.precipitation ?? (probability >= 60 ? 0.5 : 0)
            let wet = mm >= 0.2 || (hour.precipitation == nil && probability >= 60)
            let start = max(hour.dateTime.addingTimeInterval(-3600), coveredUntil)
            slots.append(RainSlot(start: start, minutes: 60, mmPerHour: wet ? max(mm, 0.2) : 0))
        }
        slots.sort { $0.start < $1.start }

        func minutesUntil(_ time: Date) -> Int {
            max(0, Int((time.timeIntervalSince(local) + 30) / 60))
        }

        let kind = WeatherCodes.kind(current.code)
        let rainingNow = current.precipitation >= 0.1 || kind == .rain || kind == .drizzle || kind == .storm
        if rainingNow {
            // Solo con el detalle de 15 minutos: la lluvia por hora es muy gruesa para "para en 20 min".
            guard let dry = slots.first(where: {
                $0.minutes == 15 && $0.start > local.addingTimeInterval(-60) && !$0.isWet
            }) else { return nil }
            let inMinutes = minutesUntil(dry.start)
            return inMinutes <= withinMinutes ? .stops(inMinutes: max(5, inMinutes)) : nil
        }

        guard let startIndex = slots.firstIndex(where: { $0.isWet && $0.end > local }) else { return nil }
        let start = slots[startIndex]
        let inMinutes = minutesUntil(start.start)
        if inMinutes > withinMinutes { return nil }
        var endIndex = startIndex
        var strongest = 0.0
        while endIndex < slots.count && slots[endIndex].isWet {
            strongest = max(strongest, slots[endIndex].mmPerHour)
            endIndex += 1
        }
        let lasts: Int? = endIndex < slots.count
            ? Int(slots[endIndex].start.timeIntervalSince(start.start) / 60)
            : nil
        return .starts(inMinutes: max(5, inMinutes), lastsMinutes: lasts, intensity: RainIntensity.of(strongest))
    }
}
