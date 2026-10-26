import WidgetKit
import SwiftUI

struct WeatherWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: WeatherEntry

    var body: some View {
        let snapshot = entry.snapshot?.withCurrentFromHours(now: entry.date)
        Group {
            if let snapshot = snapshot {
                switch family {
                case .systemSmall:
                    SmallWeatherView(snapshot: snapshot, now: entry.date)
                case .systemMedium:
                    MediumWeatherView(snapshot: snapshot, now: entry.date)
                default:
                    LargeWeatherView(snapshot: snapshot, now: entry.date)
                }
            } else {
                VStack(spacing: 6) {
                    Image(systemName: "cloud.sun.fill")
                        .symbolRenderingMode(.multicolor)
                        .font(.title)
                    Text("Abrí Climatin y elegí una ciudad")
                        .font(.caption.weight(.semibold))
                        .multilineTextAlignment(.center)
                }
            }
        }
        .foregroundStyle(Color.white)
        .containerBackground(for: .widget) {
            Sky.gradient(for: snapshot)
        }
    }
}

// MARK: - Partes

private struct PlaceTitle: View {
    let snapshot: WeatherSnapshot

    var body: some View {
        HStack(spacing: 3) {
            Text(snapshot.place ?? "Mi ubicación")
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
            if snapshot.placeId == currentPlaceId {
                Image(systemName: "location.fill")
                    .font(.system(size: 9))
            }
        }
    }
}

private struct HighLowText: View {
    let snapshot: WeatherSnapshot
    let now: Date

    var body: some View {
        if let today = snapshot.today(now: now) {
            Text("Máx. \(WeatherFormat.degrees(today.high))  Mín. \(WeatherFormat.degrees(today.low))")
                .font(.caption.weight(.medium))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
    }
}

/// Las próximas cinco horas, sin la actual (ya está arriba en grande).
private struct HourStrip: View {
    let snapshot: WeatherSnapshot
    let now: Date

    var body: some View {
        let hours = Array(snapshot.upcomingHours(6, now: now).dropFirst())
        HStack(spacing: 0) {
            ForEach(hours) { hour in
                VStack(spacing: 4) {
                    Text(WeatherFormat.hourLabel(hour.dateTime))
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(Color.white.opacity(0.85))
                    WeatherIcon(code: hour.code, isDay: hour.isDay)
                        .font(.callout)
                        .frame(height: 20)
                    Text(WeatherFormat.degrees(hour.temperature))
                        .font(.caption.weight(.semibold))
                }
                .frame(maxWidth: .infinity)
            }
        }
    }
}

// MARK: - Tamaños

private struct SmallWeatherView: View {
    let snapshot: WeatherSnapshot
    let now: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            PlaceTitle(snapshot: snapshot)
            Text(WeatherFormat.degrees(snapshot.current.temperature))
                .font(.system(size: 42, weight: .light))
                .minimumScaleFactor(0.7)
            Spacer(minLength: 0)
            WeatherIcon(code: snapshot.current.code, isDay: snapshot.current.isDay)
                .font(.title3)
            Text(WeatherCodes.description(snapshot.current.code))
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            HighLowText(snapshot: snapshot, now: now)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

/// Encabezado ancho para los tamaños mediano y grande.
private struct WideHeader: View {
    let snapshot: WeatherSnapshot
    let now: Date

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 0) {
                PlaceTitle(snapshot: snapshot)
                Text(WeatherFormat.degrees(snapshot.current.temperature))
                    .font(.system(size: 40, weight: .light))
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                WeatherIcon(code: snapshot.current.code, isDay: snapshot.current.isDay)
                    .font(.title3)
                Text(WeatherCodes.description(snapshot.current.code))
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                HighLowText(snapshot: snapshot, now: now)
            }
        }
    }
}

private struct MediumWeatherView: View {
    let snapshot: WeatherSnapshot
    let now: Date

    var body: some View {
        VStack(spacing: 6) {
            WideHeader(snapshot: snapshot, now: now)
            Spacer(minLength: 0)
            HourStrip(snapshot: snapshot, now: now)
        }
    }
}

private struct LargeWeatherView: View {
    let snapshot: WeatherSnapshot
    let now: Date

    var body: some View {
        let days = snapshot.upcomingDays(5, now: now)
        let rangeLow = days.map { $0.low }.min() ?? 0
        let rangeHigh = days.map { $0.high }.max() ?? 1
        VStack(spacing: 10) {
            WideHeader(snapshot: snapshot, now: now)
            HourStrip(snapshot: snapshot, now: now)
            Rectangle()
                .fill(Color.white.opacity(0.25))
                .frame(height: 0.5)
            VStack(spacing: 6) {
                ForEach(days) { day in
                    HStack(spacing: 8) {
                        Text(WeatherFormat.dayLabel(day.localDate, today: snapshot.nowAtPlace(now)))
                            .font(.caption.weight(.semibold))
                            .frame(width: 52, alignment: .leading)
                        WeatherIcon(code: day.code, isDay: true)
                            .font(.callout)
                            .frame(width: 26)
                        Text(WeatherFormat.degrees(day.low))
                            .font(.caption.weight(.medium))
                            .foregroundStyle(Color.white.opacity(0.7))
                            .frame(width: 30, alignment: .trailing)
                        TemperatureRangeBar(low: day.low, high: day.high, rangeLow: rangeLow, rangeHigh: rangeHigh, height: 4)
                        Text(WeatherFormat.degrees(day.high))
                            .font(.caption.weight(.semibold))
                            .frame(width: 30, alignment: .trailing)
                    }
                }
            }
            Spacer(minLength: 0)
        }
    }
}
