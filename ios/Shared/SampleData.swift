import Foundation

extension WeatherSnapshot {
    /// Pronóstico inventado para la vista previa del widget y los previews de SwiftUI.
    static func sample(now: Date = Date()) -> WeatherSnapshot {
        let zone = TimeZone.current
        let local = LocalTime.wallClock(now, zone: zone)
        let firstHour = LocalTime.startOfHour(local)
        let today = LocalTime.startOfDay(local)

        let hours: [Hour] = (0..<30).map { index in
            let time = firstHour.addingTimeInterval(TimeInterval(index * 3600))
            let hourOfDay = LocalTime.hour(time)
            let temperature = 18 + 6 * sin(Double(hourOfDay - 9) / 24 * 2 * Double.pi)
            return Hour(
                time: LocalTime.string(time),
                dateTime: time,
                temperature: temperature,
                code: index < 8 ? 1 : 2,
                precipitationProbability: index * 2 % 40,
                isDay: (7..<20).contains(hourOfDay)
            )
        }

        let codes = [1, 2, 3, 61, 2, 0, 80, 1, 3, 2]
        let days: [Day] = (0..<10).map { index in
            let date = today.addingTimeInterval(TimeInterval(index * 86_400))
            let day = LocalTime.dayString(date)
            return Day(
                date: day,
                localDate: date,
                code: codes[index],
                high: 22 + Double(index % 4),
                low: 11 + Double(index % 3),
                sunrise: day + "T06:40",
                sunset: day + "T19:50",
                uvIndex: 7,
                precipitationProbability: codes[index] >= 61 ? 70 : 10
            )
        }

        return WeatherSnapshot(
            placeId: "sample",
            place: "Córdoba",
            latitude: -31.42,
            longitude: -64.18,
            utcOffsetSeconds: zone.secondsFromGMT(for: now),
            timezone: zone.identifier,
            fetchedAt: now,
            current: Current(
                time: LocalTime.string(firstHour),
                temperature: hours.first?.temperature ?? 20,
                feelsLike: 19,
                humidity: 55,
                isDay: hours.first?.isDay ?? true,
                code: 1,
                windSpeed: 14,
                precipitation: 0,
                pressure: 1013,
                uvIndex: 4,
                windDirection: 30
            ),
            hours: hours,
            pastHours: [],
            days: days,
            minutely: []
        )
    }
}
