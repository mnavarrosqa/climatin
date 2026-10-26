import SwiftUI

/// Ícono del estado con los colores propios del símbolo (sol amarillo, gota celeste).
struct WeatherIcon: View {
    let code: Int
    let isDay: Bool

    var body: some View {
        Image(systemName: WeatherCodes.symbolName(code: code, isDay: isDay))
            .symbolRenderingMode(.multicolor)
    }
}

/// Barra de mínima a máxima de un día, ubicada dentro del rango de toda la semana.
struct TemperatureRangeBar: View {
    let low: Double
    let high: Double
    let rangeLow: Double
    let rangeHigh: Double
    var height: CGFloat = 5

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            // Rango mínimo de 1° para no dividir por cero en una semana pareja.
            let span = max(rangeHigh - rangeLow, 1)
            let start = CGFloat((low - rangeLow) / span) * width
            let barWidth = min(width, max(CGFloat((high - low) / span) * width, height))
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.black.opacity(0.18))
                Capsule()
                    .fill(LinearGradient(
                        colors: [Sky.temperatureColor(low), Sky.temperatureColor(high)],
                        startPoint: .leading,
                        endPoint: .trailing
                    ))
                    .frame(width: barWidth)
                    .offset(x: max(0, min(start, width - barWidth)))
            }
        }
        .frame(height: height)
    }
}
