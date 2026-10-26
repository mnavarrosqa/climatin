import SwiftUI

@main
struct ClimatinApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                // Texto blanco sobre el cielo y tarjetas de vidrio oscuras, como el Clima de Apple.
                .preferredColorScheme(.dark)
        }
    }
}
