import Foundation

/// Caché en el contenedor del app group: la app escribe y el widget lee (y actualiza si está viejo).
/// Son archivos JSON chicos, uno por lugar, más la lista de lugares y cuál está elegido.
final class WeatherStore {
    static let appGroupId = "group.com.mnavarrosqa.climatin"
    static let shared = WeatherStore()

    private let directory: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init() {
        let fileManager = FileManager.default
        // Sin firma (CI, simulador sin equipo) el app group no existe: se usa la carpeta propia
        // para que la app funcione igual; el widget en ese caso no ve los datos.
        let base = fileManager.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroupId)
            ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        directory = base.appendingPathComponent("Climatin", isDirectory: true)
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    // MARK: Lugares

    private struct PlacesFile: Codable {
        var places: [Place]
        var selectedPlaceId: String?
    }

    private var stateURL: URL { directory.appendingPathComponent("places.json") }

    private func loadState() -> PlacesFile {
        guard let data = try? Data(contentsOf: stateURL),
              let state = try? decoder.decode(PlacesFile.self, from: data) else {
            return PlacesFile(places: [], selectedPlaceId: nil)
        }
        return state
    }

    private func saveState(_ state: PlacesFile) {
        guard let data = try? encoder.encode(state) else { return }
        try? data.write(to: stateURL, options: .atomic)
    }

    func loadPlaces() -> [Place] {
        loadState().places
    }

    func savePlaces(_ places: [Place], selectedPlaceId: String?) {
        saveState(PlacesFile(places: places, selectedPlaceId: selectedPlaceId))
    }

    var selectedPlaceId: String? {
        loadState().selectedPlaceId
    }

    /// El lugar que muestra el widget: el último que se miró en la app.
    func selectedPlace() -> Place? {
        let state = loadState()
        return state.places.first { $0.id == state.selectedPlaceId } ?? state.places.first
    }

    // MARK: Pronósticos

    private func snapshotURL(for placeId: String) -> URL {
        // "geo:3435910" → "geo_3435910": los dos puntos no van bien en nombres de archivo.
        let safe = placeId.map { $0.isLetter || $0.isNumber ? String($0) : "_" }.joined()
        return directory.appendingPathComponent("snapshot-\(safe).json")
    }

    func snapshot(for placeId: String) -> WeatherSnapshot? {
        guard let data = try? Data(contentsOf: snapshotURL(for: placeId)) else { return nil }
        // Si cambia el formato, la caché vieja se ignora y se vuelve a bajar.
        return try? decoder.decode(WeatherSnapshot.self, from: data)
    }

    func save(_ snapshot: WeatherSnapshot) {
        guard let data = try? encoder.encode(snapshot) else { return }
        try? data.write(to: snapshotURL(for: snapshot.placeId), options: .atomic)
    }

    func deleteSnapshot(for placeId: String) {
        try? FileManager.default.removeItem(at: snapshotURL(for: placeId))
    }

    /// El pronóstico guardado si es reciente; si no, uno nuevo. Si no hay red, el guardado.
    func snapshot(for place: Place, maxAge: TimeInterval, client: OpenMeteoClient = OpenMeteoClient()) async -> WeatherSnapshot? {
        let cached = snapshot(for: place.id)
        if let cached = cached, Date().timeIntervalSince(cached.fetchedAt) < maxAge {
            return cached
        }
        do {
            let fresh = try await client.snapshot(for: place)
            save(fresh)
            return fresh
        } catch {
            return cached
        }
    }
}
