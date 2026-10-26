import WidgetKit
import SwiftUI

struct WeatherEntry: TimelineEntry {
    let date: Date
    let snapshot: WeatherSnapshot?
}

/// Lee el pronóstico que dejó la app en el app group y, si tiene más de media hora, baja uno nuevo.
struct WeatherProvider: TimelineProvider {
    /// Cada cuánto se renueva el widget (iOS puede estirarlo según su presupuesto).
    static let refreshInterval: TimeInterval = 30 * 60

    func placeholder(in context: Context) -> WeatherEntry {
        WeatherEntry(date: Date(), snapshot: WeatherSnapshot.sample())
    }

    func getSnapshot(in context: Context, completion: @escaping (WeatherEntry) -> Void) {
        let store = WeatherStore.shared
        let cached = store.selectedPlace().flatMap { store.snapshot(for: $0.id) }
        // En la galería de widgets, si todavía no hay datos, se muestra el de ejemplo.
        completion(WeatherEntry(date: Date(), snapshot: cached ?? (context.isPreview ? WeatherSnapshot.sample() : nil)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WeatherEntry>) -> Void) {
        Task {
            let store = WeatherStore.shared
            var snapshot: WeatherSnapshot?
            if let place = store.selectedPlace() {
                snapshot = await store.snapshot(for: place, maxAge: Self.refreshInterval)
            }
            let now = Date()
            let entry = WeatherEntry(date: now, snapshot: snapshot)
            let next = now.addingTimeInterval(Self.refreshInterval)
            completion(Timeline(entries: [entry], policy: .after(next)))
        }
    }
}

struct CurrentWeatherWidget: Widget {
    let kind = "ClimatinCurrentWeather"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WeatherProvider()) { entry in
            WeatherWidgetView(entry: entry)
        }
        .configurationDisplayName("Clima actual")
        .description("La temperatura y el pronóstico de la ciudad que elegiste en Climatin.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

@main
struct ClimatinWidgetBundle: WidgetBundle {
    var body: some Widget {
        CurrentWeatherWidget()
    }
}
