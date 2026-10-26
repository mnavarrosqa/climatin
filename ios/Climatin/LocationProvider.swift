import CoreLocation

enum LocationError: LocalizedError, Equatable {
    case denied
    case busy

    var errorDescription: String? {
        switch self {
        case .denied: return "Sin permiso de ubicación."
        case .busy: return "Ya se está buscando la ubicación."
        }
    }
}

/// Una sola lectura de ubicación ("cuando se usa la app"), con async/await.
/// Precisión de un kilómetro: alcanza para el clima y responde más rápido que el GPS fino.
/// Se crea y se usa desde el hilo principal (AppModel): ahí llegan también los avisos de
/// CoreLocation. Las continuaciones viven en el MainActor para que no haya carreras.
final class LocationProvider: NSObject, CLLocationManagerDelegate {
    private let manager: CLLocationManager
    @MainActor private var authorizationWaiter: CheckedContinuation<Void, Never>?
    @MainActor private var locationWaiter: CheckedContinuation<CLLocation, Error>?

    override init() {
        manager = CLLocationManager()
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    @MainActor
    var isDenied: Bool {
        let status = manager.authorizationStatus
        return status == .denied || status == .restricted
    }

    @MainActor
    func currentLocation() async throws -> CLLocation {
        if manager.authorizationStatus == .notDetermined {
            await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                authorizationWaiter = continuation
                manager.requestWhenInUseAuthorization()
            }
        }
        let status = manager.authorizationStatus
        guard status == .authorizedWhenInUse || status == .authorizedAlways else {
            throw LocationError.denied
        }
        guard locationWaiter == nil else { throw LocationError.busy }
        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<CLLocation, Error>) in
            locationWaiter = continuation
            manager.requestLocation()
        }
    }

    /// Nombre de la ciudad para la ubicación actual. Si falla, la app muestra "Mi ubicación".
    @MainActor
    func placeName(for location: CLLocation) async -> String? {
        let placemarks = try? await CLGeocoder().reverseGeocodeLocation(location)
        guard let placemark = placemarks?.first else { return nil }
        return placemark.locality ?? placemark.subAdministrativeArea ?? placemark.administrativeArea ?? placemark.name
    }

    // Los avisos de CoreLocation vuelven al MainActor, donde viven las continuaciones.

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            // Al asignar el delegate llega un primer aviso con "sin decidir": se ignora.
            guard status != .notDetermined else { return }
            self.authorizationWaiter?.resume()
            self.authorizationWaiter = nil
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor in
            self.locationWaiter?.resume(returning: location)
            self.locationWaiter = nil
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            self.locationWaiter?.resume(throwing: error)
            self.locationWaiter = nil
        }
    }
}
