package com.mnavarrosqa.clima.data

import kotlin.math.roundToInt

enum class TemperatureUnit(val label: String) { CELSIUS("°C"), FAHRENHEIT("°F") }

enum class WindUnit(val label: String) { KMH("km/h"), MS("m/s"), MPH("mph"), KNOTS("nudos") }

enum class PressureUnit(val label: String) { HPA("hPa"), INHG("inHg"), MMHG("mmHg") }

/** Unidades elegidas. Los datos se guardan siempre en métrico y se convierten al mostrar. */
data class UnitSettings(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val wind: WindUnit = WindUnit.KMH,
    val pressure: PressureUnit = PressureUnit.HPA,
    val use24h: Boolean = true,
)

/** Unidades vigentes para los formateadores; las carga y actualiza WeatherRepository. */
object Units {
    @Volatile
    var current: UnitSettings = UnitSettings()
}

fun Double.degrees(): String {
    val value = if (Units.current.temperature == TemperatureUnit.FAHRENHEIT) this * 9 / 5 + 32 else this
    return "${value.roundToInt()}°"
}

/** Diferencia de temperatura ("6°"), convertida si se usan Fahrenheit. */
fun Double.degreesDelta(): String {
    val value = if (Units.current.temperature == TemperatureUnit.FAHRENHEIT) this * 9 / 5 else this
    return "${kotlin.math.abs(value).roundToInt()}°"
}

/** Punto cardinal de donde viene el viento. */
fun windFrom(degrees: Double): String {
    val names = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    return names[(((degrees % 360) + 360) % 360 / 45.0).roundToInt() % 8]
}

fun windText(kmh: Double): String {
    val unit = Units.current.wind
    val value = when (unit) {
        WindUnit.KMH -> kmh
        WindUnit.MS -> kmh / 3.6
        WindUnit.MPH -> kmh / 1.609344
        WindUnit.KNOTS -> kmh / 1.852
    }
    return "${value.roundToInt()} ${unit.label}"
}

fun pressureText(hpa: Double): String = when (Units.current.pressure) {
    PressureUnit.HPA -> "${hpa.roundToInt()} hPa"
    PressureUnit.INHG -> String.format(java.util.Locale.US, "%.2f inHg", hpa * 0.0295299830714)
    PressureUnit.MMHG -> "${(hpa * 0.750061683).roundToInt()} mmHg"
}
