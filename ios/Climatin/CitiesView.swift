import SwiftUI

/// Lista de ciudades: buscar con el geocoding de Open-Meteo, agregar, borrar y elegir.
struct CitiesView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [Place] = []
    @State private var searching = false
    @State private var searchFailed = false

    private var isSearching: Bool {
        !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            List {
                if isSearching {
                    searchResults
                } else {
                    savedPlaces
                }
            }
            .navigationTitle("Ciudades")
            .searchable(
                text: $query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Buscá una ciudad"
            )
            .autocorrectionDisabled()
            .task(id: query) {
                await search()
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Listo") { dismiss() }
                }
            }
        }
    }

    private var savedPlaces: some View {
        Section {
            ForEach(model.places) { place in
                Button {
                    model.selectedId = place.id
                    dismiss()
                } label: {
                    CityRow(place: place, snapshot: model.snapshots[place.id]?.withCurrentFromHours())
                }
                .listRowBackground(Sky.gradient(for: model.snapshots[place.id]?.withCurrentFromHours()))
                // La ubicación actual vuelve sola al abrir la app: no se borra desde acá.
                .deleteDisabled(place.isCurrentLocation)
            }
            .onDelete { offsets in
                model.remove(atOffsets: offsets)
            }
        } footer: {
            Text("Deslizá una ciudad hacia la izquierda para borrarla.")
        }
    }

    @ViewBuilder
    private var searchResults: some View {
        if searching && results.isEmpty {
            HStack(spacing: 10) {
                ProgressView()
                Text("Buscando…")
            }
        } else if searchFailed {
            Text("No se pudo buscar. Revisá la conexión.")
        } else if results.isEmpty {
            Text("No encontramos esa ciudad.")
        }
        ForEach(results) { place in
            Button {
                model.add(place)
                dismiss()
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(place.name)
                        .foregroundStyle(Color.primary)
                    if let detail = place.detail {
                        Text(detail)
                            .font(.caption)
                            .foregroundStyle(Color.secondary)
                    }
                }
            }
        }
    }

    /// Espera un poco entre teclas para no consultar por cada letra. `.task(id:)` cancela la
    /// búsqueda anterior cuando cambia el texto.
    @MainActor
    private func search() async {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.count >= 2 else {
            results = []
            searching = false
            searchFailed = false
            return
        }
        searching = true
        try? await Task.sleep(nanoseconds: 350_000_000)
        if Task.isCancelled { return }
        do {
            let found = try await OpenMeteoClient().searchCities(text)
            if Task.isCancelled { return }
            results = found
            searchFailed = false
        } catch {
            if Task.isCancelled { return }
            results = []
            searchFailed = true
        }
        searching = false
    }
}

/// Fila con el cielo de la ciudad de fondo, como la lista del Clima de Apple.
private struct CityRow: View {
    let place: Place
    let snapshot: WeatherSnapshot?

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 4) {
                    Text(place.name)
                        .font(.title3.weight(.semibold))
                        .lineLimit(1)
                    if place.isCurrentLocation {
                        Image(systemName: "location.fill")
                            .font(.caption)
                    }
                }
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(Color.white.opacity(0.85))
                    .lineLimit(1)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                if let snapshot = snapshot {
                    Text(WeatherFormat.degrees(snapshot.current.temperature))
                        .font(.system(size: 40, weight: .light))
                    if let today = snapshot.today() {
                        Text("Máx.: \(WeatherFormat.degrees(today.high))  Mín.: \(WeatherFormat.degrees(today.low))")
                            .font(.caption)
                    }
                }
            }
        }
        .foregroundStyle(Color.white)
        .padding(.vertical, 6)
    }

    private var subtitle: String {
        if let snapshot = snapshot {
            return WeatherCodes.description(snapshot.current.code)
        }
        return place.detail ?? " "
    }
}
