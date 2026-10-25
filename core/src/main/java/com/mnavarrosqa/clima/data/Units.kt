package com.mnavarrosqa.clima.data

import kotlin.math.roundToInt

enum class TemperatureUnit(val label: String) { CELSIUS("°C"), FAHRENHEIT("°F") }

enum class WindUnit(private val es: String, private val en: String) {
    KMH("km/h", "km/h"), MS("m/s", "m/s"), MPH("mph", "mph"), KNOTS("nudos", "kn");

    val label: String get() = tr(es, en)
}

enum class PressureUnit(val label: String) { HPA("hPa"), INHG("inHg"), MMHG("mmHg") }

/** Unidades elegidas. Los datos se guardan siempre en métrico y se convierten al mostrar. */
data class UnitSettings(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val wind: WindUnit = WindUnit.KMH,
    val pressure: PressureUnit = PressureUnit.HPA,
    val use24h: Boolean = true,
)

/** Texto corto para mandar las unidades del teléfono al reloj: "CELSIUS,KMH,HPA,true". */
fun UnitSettings.encode(): String = "${temperature.name},${wind.name},${pressure.name},$use24h"

fun decodeUnits(text: String?): UnitSettings? {
    val parts = text?.split(',') ?: return null
    if (parts.size != 4) return null
    return UnitSettings(
        temperature = TemperatureUnit.entries.firstOrNull { it.name == parts[0] } ?: return null,
        wind = WindUnit.entries.firstOrNull { it.name == parts[1] } ?: return null,
        pressure = PressureUnit.entries.firstOrNull { it.name == parts[2] } ?: return null,
        use24h = parts[3].toBooleanStrictOrNull() ?: return null,
    )
}

/** Ruta del dato que el teléfono comparte con el reloj (Wear OS Data Layer). */
const val WEAR_SYNC_PATH = "/clima"

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
    val names = if (Lang.isSpanish) listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    else listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
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
