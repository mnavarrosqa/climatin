import SwiftUI

/// Una ciudad: encabezado grande, resumen, próximas horas, próximos días y detalles.
struct WeatherPageView: View {
    @EnvironmentObject private var model: AppModel
    let place: Place

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                if let snapshot = model.snapshots[place.id]?.withCurrentFromHours() {
                    WeatherContent(place: place, snapshot: snapshot, error: model.errors[place.id])
                } else if let error = model.errors[place.id] {
                    VStack(spacing: 12) {
                        Text(place.name)
                            .font(.largeTitle)
                        Text(error)
                            .multilineTextAlignment(.center)
                        Button("Tocá para reintentar") {
                            Task { await model.refresh(place.id) }
                        }
                        .buttonStyle(.bordered)
                        .tint(Color.white)
                    }
                    .padding(.top, 120)
                } else {
                    VStack(spacing: 12) {
                        Text(place.name)
                            .font(.largeTitle)
                        ProgressView()
                            .tint(Color.white)
                    }
                    .padding(.top, 120)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        .refreshable {
            await model.refresh(place.id)
        }
        .task {
            if model.snapshots[place.id] == nil {
                await model.refresh(place.id)
            }
        }
    }
}

private struct WeatherContent: View {
    let place: Place
    let snapshot: WeatherSnapshot
    let error: String?

    var body: some View {
        HeaderView(place: place, snapshot: snapshot)
        SummaryCard(snapshot: snapshot, error: error)
        HourlyCard(snapshot: snapshot)
        DailyCard(snapshot: snapshot)
        DetailGrid(snapshot: snapshot)
    }
}

// MARK: - Encabezado

private struct HeaderView: View {
    let place: Place
    let snapshot: WeatherSnapshot

    var body: some View {
        VStack(spacing: 2) {
            HStack(spacing: 6) {
                if place.isCurrentLocation {
                    Image(systemName: "location.fill")
                        .font(.footnote)
                }
                Text(place.name)
                    .font(.title.weight(.medium))
                    .lineLimit(1)
            }
            Text(WeatherFormat.degrees(snapshot.current.temperature))
                .font(.system(size: 96, weight: .thin))
            Text(WeatherCodes.description(snapshot.current.code))
                .font(.title3.weight(.medium))
                .foregroundStyle(Color.white.opacity(0.85))
            if let today = snapshot.today() {
                Text("Máx.: \(WeatherFormat.degrees(today.high))   Mín.: \(WeatherFormat.degrees(today.low))")
                    .font(.title3.weight(.medium))
            }
        }
        .padding(.top, 36)
        .padding(.bottom, 16)
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Tarjetas

/// Tarjeta de vidrio con su título chico arriba, como las del Clima de Apple.
struct GlassCard<Content: View>: View {
    let title: String
    let systemImage: String
    let content: Content

    init(_ title: String, systemImage: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.systemImage = systemImage
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(title.uppercased(), systemImage: systemImage)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.white.opacity(0.65))
            content
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

private struct SummaryCard: View {
    let snapshot: WeatherSnapshot
    let error: String?

    var body: some View {
        let insight = Insights.headline(snapshot)
        let outlook = snapshot.rainOutlook()
        GlassCard("Resumen", systemImage: "text.alignleft") {
            Text(insight?.text ?? Insights.fallback(snapshot))
                .font(.body.weight(.medium))
                .fixedSize(horizontal: false, vertical: true)
            // La lluvia de la próxima hora, salvo que la frase ya hable de lluvia.
            if let outlook = outlook, insight?.isAboutRain != true {
                Label(outlook.text, systemImage: "umbrella.fill")
                    .font(.subheadline)
            }
            if let duration = outlook?.durationText {
                Text(duration)
                    .font(.subheadline)
                    .foregroundStyle(Color.white.opacity(0.8))
            }
            Text(error ?? "Actualizado \(WeatherFormat.ageText(snapshot.fetchedAt))")
                .font(.caption)
                .foregroundStyle(Color.white.opacity(0.6))
        }
    }
}

private struct HourlyCard: View {
    let snapshot: WeatherSnapshot

    var body: some View {
        let hours = snapshot.upcomingHours(25)
        GlassCard("Próximas 24 horas", systemImage: "clock") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 20) {
                    ForEach(Array(hours.enumerated()), id: \.offset) { index, hour in
                        VStack(spacing: 8) {
                            Text(index == 0 ? "Ahora" : WeatherFormat.hourLabel(hour.dateTime))
                                .font(.subheadline.weight(.medium))
                            WeatherIcon(code: hour.code, isDay: hour.isDay)
                                .font(.title3)
                                .frame(height: 28)
                            Text(rainText(hour))
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(Color(hex: 0x82D0FF))
                            Text(WeatherFormat.degrees(hour.temperature))
                                .font(.headline)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    /// Solo desde 20%: por debajo es ruido. El espacio queda igual para que no salten las filas.
    private func rainText(_ hour: Hour) -> String {
        guard let probability = hour.precipitationProbability, probability >= 20 else { return " " }
        return "\(probability)%"
    }
}

private struct DailyCard: View {
    let snapshot: WeatherSnapshot

    var body: some View {
        let days = snapshot.upcomingDays(10)
        let rangeLow = days.map { $0.low }.min() ?? 0
        let rangeHigh = days.map { $0.high }.max() ?? 1
        let today = snapshot.nowAtPlace()
        GlassCard("Pronóstico de \(days.count) días", systemImage: "calendar") {
            VStack(spacing: 0) {
                ForEach(Array(days.enumerated()), id: \.offset) { index, day in
                    if index > 0 {
                        Rectangle()
                            .fill(Color.white.opacity(0.2))
                            .frame(height: 0.5)
                    }
                    HStack(spacing: 10) {
                        Text(WeatherFormat.dayLabel(day.localDate, today: today))
                            .font(.body.weight(.medium))
                            .frame(width: 64, alignment: .leading)
                        VStack(spacing: 0) {
                            WeatherIcon(code: day.code, isDay: true)
                                .font(.body)
                            if let probability = day.precipitationProbability, probability >= 20 {
                                Text(verbatim: "\(probability)%")
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(Color(hex: 0x82D0FF))
                            }
                        }
                        .frame(width: 34)
                        Text(WeatherFormat.degrees(day.low))
                            .foregroundStyle(Color.white.opacity(0.65))
                            .frame(width: 38, alignment: .trailing)
                        TemperatureRangeBar(low: day.low, high: day.high, rangeLow: rangeLow, rangeHigh: rangeHigh)
                        Text(WeatherFormat.degrees(day.high))
                            .frame(width: 38, alignment: .trailing)
                    }
                    .font(.body.weight(.medium))
                    .padding(.vertical, 8)
                }
            }
        }
    }
}

// MARK: - Detalles

private struct DetailGrid: View {
    let snapshot: WeatherSnapshot

    var body: some View {
        let current = snapshot.current
        let today = snapshot.today()
        let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]
        LazyVGrid(columns: columns, spacing: 12) {
            DetailTile(
                title: "Sensación",
                systemImage: "thermometer.medium",
                value: WeatherFormat.degrees(current.feelsLike),
                note: feelsLikeNote(current)
            )
            DetailTile(
                title: "Humedad",
                systemImage: "humidity",
                value: "\(current.humidity)%",
                note: dewPointNote
            )
            DetailTile(
                title: "Viento",
                systemImage: "wind",
                value: WeatherFormat.windText(current.windSpeed),
                note: windNote(current)
            )
            DetailTile(
                title: "Índice UV",
                systemImage: "sun.max",
                value: current.uvIndex.map { "\(WeatherFormat.roundToInt($0))" } ?? "--",
                note: uvNote(current: current.uvIndex, today: today)
            )
            sunTile
            moonTile
        }
    }

    private func feelsLikeNote(_ current: Current) -> String {
        let delta = current.feelsLike - current.temperature
        if delta >= 2 { return "Se siente más cálido que la temperatura real." }
        if delta <= -2 { return "Se siente más fresco que la temperatura real." }
        return "Parecida a la temperatura real."
    }

    private var dewPointNote: String {
        guard let dewPoint = snapshot.upcomingHours(1).first?.dewPoint else { return " " }
        return "Punto de rocío: \(WeatherFormat.degrees(dewPoint))."
    }

    private func windNote(_ current: Current) -> String {
        var parts: [String] = []
        if let direction = current.windDirection {
            parts.append("Del \(WeatherFormat.windFrom(direction))")
        }
        if let gusts = current.windGusts {
            parts.append("ráfagas de \(WeatherFormat.windText(gusts))")
        }
        return parts.isEmpty ? " " : parts.joined(separator: ", ").capitalizedFirst + "."
    }

    private func uvNote(current: Double?, today: Day?) -> String {
        var text = current.map { WeatherCodes.uvLabel($0).capitalizedFirst + "." } ?? ""
        if let max = today?.uvIndex {
            text += (text.isEmpty ? "" : " ") + "Máximo de hoy: \(WeatherFormat.roundToInt(max))."
        }
        return text.isEmpty ? " " : text
    }

    /// Muestra lo próximo: el amanecer si todavía no salió el sol, si no el atardecer, y de
    /// noche el amanecer de mañana.
    private var sunTile: some View {
        let local = snapshot.nowAtPlace()
        let days = snapshot.upcomingDays(2)
        var title = "Amanecer"
        var symbol = "sunrise.fill"
        var value = "--"
        var note = " "
        if let today = days.first,
           let sunrise = LocalTime.parse(today.sunrise),
           let sunset = LocalTime.parse(today.sunset) {
            if local < sunrise {
                value = WeatherFormat.shortHour(sunrise)
                note = "Atardecer: \(WeatherFormat.shortHour(sunset))."
            } else if local < sunset {
                title = "Atardecer"
                symbol = "sunset.fill"
                value = WeatherFormat.shortHour(sunset)
                note = "Amaneció a las \(WeatherFormat.shortHour(sunrise))."
            } else {
                value = days.count > 1 ? WeatherFormat.time(of: days[1].sunrise) : "--"
                note = "Mañana. Hoy atardeció a las \(WeatherFormat.shortHour(sunset))."
            }
        }
        return DetailTile(title: title, systemImage: symbol, value: value, note: note)
    }

    private var moonTile: some View {
        let age = Moon.age(localDay: snapshot.nowAtPlace())
        let lit = WeatherFormat.roundToInt(Moon.illumination(age: age) * 100)
        return DetailTile(
            title: "Luna",
            systemImage: Moon.symbolName(age: age),
            value: Moon.phaseName(age: age),
            note: "Iluminada al \(lit)%.",
            valueFont: .title3.weight(.medium)
        )
    }
}

private struct DetailTile: View {
    let title: String
    let systemImage: String
    let value: String
    let note: String
    var valueFont: Font = .title.weight(.medium)

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(title.uppercased(), systemImage: systemImage)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.white.opacity(0.65))
            Text(value)
                .font(valueFont)
                .lineLimit(2)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 0)
            Text(note)
                .font(.footnote)
                .foregroundStyle(Color.white.opacity(0.85))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .frame(maxWidth: .infinity, minHeight: 150, alignment: .topLeading)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}
