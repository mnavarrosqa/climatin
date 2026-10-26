import SwiftUI

/// Las ciudades en páginas que se pasan de costado, con el cielo de la ciudad elegida de fondo.
struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @State private var showingCities = false

    var body: some View {
        ZStack {
            Sky.gradient(for: model.snapshots[model.selectedId]?.withCurrentFromHours())
                .ignoresSafeArea()

            if model.places.isEmpty {
                EmptyPlacesView(locating: model.locating, locationDenied: model.locationDenied) {
                    showingCities = true
                }
            } else {
                VStack(spacing: 0) {
                    TabView(selection: $model.selectedId) {
                        ForEach(model.places) { place in
                            WeatherPageView(place: place)
                                .tag(place.id)
                        }
                    }
                    .tabViewStyle(.page(indexDisplayMode: .never))

                    bottomBar
                }
            }
        }
        .foregroundStyle(Color.white)
        .sheet(isPresented: $showingCities) {
            CitiesView()
                .environmentObject(model)
        }
        .onChange(of: scenePhase, initial: true) { _, phase in
            if phase == .active {
                Task { await model.becameActive() }
            }
        }
    }

    /// Puntos de página (la flecha es la ubicación actual) y el botón de la lista de ciudades.
    private var bottomBar: some View {
        HStack {
            Color.clear.frame(width: 44, height: 44)
            Spacer()
            HStack(spacing: 8) {
                ForEach(model.places) { place in
                    let selected = place.id == model.selectedId
                    if place.isCurrentLocation {
                        Image(systemName: "location.fill")
                            .font(.system(size: 9))
                            .foregroundStyle(selected ? Color.white : Color.white.opacity(0.4))
                    } else {
                        Circle()
                            .fill(selected ? Color.white : Color.white.opacity(0.4))
                            .frame(width: 7, height: 7)
                    }
                }
            }
            Spacer()
            Button {
                showingCities = true
            } label: {
                Image(systemName: "list.bullet")
                    .font(.title3)
                    .frame(width: 44, height: 44)
            }
            .tint(Color.white)
            .accessibilityLabel("Ciudades")
        }
        .padding(.horizontal, 16)
        .background(.ultraThinMaterial)
    }
}

/// Sin lugares: esperando la ubicación, o sin permiso y sin ciudades guardadas.
private struct EmptyPlacesView: View {
    let locating: Bool
    let locationDenied: Bool
    let onSearch: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            if locating {
                ProgressView()
                    .tint(Color.white)
                Text("Buscando dónde estás…")
                    .font(.headline)
            } else {
                Image(systemName: locationDenied ? "location.slash.fill" : "cloud.sun.fill")
                    .symbolRenderingMode(.multicolor)
                    .font(.system(size: 56))
                Text("Elegí una ciudad para ver el pronóstico")
                    .font(.title3.weight(.semibold))
                    .multilineTextAlignment(.center)
                if locationDenied {
                    Text("Para usar tu ubicación, activala en Ajustes > Climatin.")
                        .font(.subheadline)
                        .foregroundStyle(Color.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                }
                Button("Buscar ciudad", action: onSearch)
                    .buttonStyle(.borderedProminent)
                    .tint(Color.white.opacity(0.25))
            }
        }
        .padding(32)
    }
}
