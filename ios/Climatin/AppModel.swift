import SwiftUI
import WidgetKit
import CoreLocation

/// Estado de la app: lugares, pronósticos y qué ciudad se está mirando.
@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var places: [Place]
    @Published private(set) var snapshots: [String: WeatherSnapshot]
    @Published private(set) var loading: Set<String> = []
    @Published private(set) var errors: [String: String] = [:]
    @Published private(set) var locating = false
    @Published private(set) var locationDenied = false

    /// Página del TabView. Se guarda para que el widget muestre la misma ciudad.
    @Published var selectedId: String {
        didSet {
            if oldValue != selectedId { persist() }
        }
    }

    private let store: WeatherStore
    private let client: OpenMeteoClient
    private let location: LocationProvider

    /// Pasado este tiempo, volver a la app baja el pronóstico de nuevo.
    private let maxAge: TimeInterval = 15 * 60

    init() {
        let store = WeatherStore.shared
        let places = store.loadPlaces()
        var cached: [String: WeatherSnapshot] = [:]
        for place in places {
            if let snapshot = store.snapshot(for: place.id) { cached[place.id] = snapshot }
        }
        let saved = store.selectedPlaceId
        self.store = store
        self.client = OpenMeteoClient()
        self.location = LocationProvider()
        self.places = places
        self.snapshots = cached
        self.selectedId = places.first(where: { $0.id == saved })?.id ?? places.first?.id ?? ""
    }

    /// Al abrir o volver a la app: relee la ubicación (puede haberse movido) y baja solo lo viejo.
    func becameActive() async {
        await updateCurrentLocation()
        await refreshAll()
    }

    func refreshAll() async {
        for place in places {
            if let snapshot = snapshots[place.id], Date().timeIntervalSince(snapshot.fetchedAt) < maxAge {
                continue
            }
            await refresh(place.id)
        }
    }

    func refresh(_ id: String) async {
        guard !loading.contains(id), let place = places.first(where: { $0.id == id }) else { return }
        loading.insert(id)
        defer { loading.remove(id) }
        do {
            let snapshot = try await client.snapshot(for: place)
            // El lugar pudo borrarse mientras se bajaba.
            guard places.contains(where: { $0.id == id }) else { return }
            snapshots[id] = snapshot
            errors[id] = nil
            store.save(snapshot)
            WidgetCenter.shared.reloadAllTimelines()
        } catch {
            // Un pull to refresh que se suelta cancela el pedido: no es un error para mostrar.
            if error is CancellationError || (error as? URLError)?.code == .cancelled { return }
            errors[id] = "No se pudo actualizar. Revisá la conexión."
        }
    }

    func updateCurrentLocation() async {
        if location.isDenied {
            locationDenied = true
            return
        }
        locating = true
        defer { locating = false }
        do {
            let found = try await location.currentLocation()
            locationDenied = false
            let name = await location.placeName(for: found) ?? "Mi ubicación"
            let place = Place(
                id: currentPlaceId,
                name: name,
                detail: "Mi ubicación",
                latitude: found.coordinate.latitude,
                longitude: found.coordinate.longitude
            )
            // Si se movió más de 2 km, el pronóstico guardado es de otro lado: se baja ya.
            var moved = true
            if let index = places.firstIndex(where: { $0.isCurrentLocation }) {
                let previous = places[index]
                moved = CLLocation(latitude: previous.latitude, longitude: previous.longitude).distance(from: found) > 2_000
                places[index] = place
            } else {
                places.insert(place, at: 0)
            }
            if selectedId.isEmpty {
                selectedId = place.id
            }
            persist()
            if moved {
                await refresh(currentPlaceId)
            }
        } catch {
            if (error as? LocationError) == .denied { locationDenied = true }
        }
    }

    func add(_ place: Place) {
        if !places.contains(where: { $0.id == place.id }) {
            places.append(place)
        }
        selectedId = place.id
        persist()
        Task { await refresh(place.id) }
    }

    func remove(atOffsets offsets: IndexSet) {
        let removed = offsets.map { places[$0] }
        places.remove(atOffsets: offsets)
        for place in removed {
            snapshots[place.id] = nil
            errors[place.id] = nil
            store.deleteSnapshot(for: place.id)
        }
        if !places.contains(where: { $0.id == selectedId }) {
            selectedId = places.first?.id ?? ""
        }
        persist()
    }

    /// Guarda la lista y avisa al widget, que muestra la ciudad elegida.
    private func persist() {
        store.savePlaces(places, selectedPlaceId: selectedId.isEmpty ? nil : selectedId)
        WidgetCenter.shared.reloadAllTimelines()
    }
}
