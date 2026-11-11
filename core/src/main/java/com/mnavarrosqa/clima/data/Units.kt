package com.mnavarrosqa.clima.data

import kotlin.math.roundToInt

enum class TemperatureUnit(val label: String) { CELSIUS("°C"), FAHRENHEIT("°F") }

enum class WindUnit(private val es: String, private val en: String) {
    KMH("km/h", "km/h"), MS("m/s", "m/s"), MPH("mph", "mph"), KNOTS("nudos", "kn");

    val label: String get() = tr(es, en)
}

enum class PressureUnit(val label: String) { HPA("hPa"), INHG("inHg"), MMHG("mmHg") }

enum class PrecipitationUnit(val label: String) { MM("mm"), INCHES("in") }

enum class DistanceUnit(val label: String) { KM("km"), MILES("mi") }

/** Unidades elegidas. Los datos se guardan siempre en métrico y se convierten al mostrar. */
data class UnitSettings(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val wind: WindUnit = WindUnit.KMH,
    val pressure: PressureUnit = PressureUnit.HPA,
    val use24h: Boolean = true,
    val precipitation: PrecipitationUnit = PrecipitationUnit.MM,
    val distance: DistanceUnit = DistanceUnit.KM,
)

/**
 * Unidades para una instalación nueva, según el país del teléfono. Estados Unidos y sus
 * territorios (y los pocos países que también usan Fahrenheit) van con °F, mph, pulgadas y
 * millas; el Reino Unido, con °C pero millas. El reloj de 12 o 24 horas lo dice el sistema.
 */
fun defaultUnitsFor(country: String?, use24h: Boolean): UnitSettings = when (country?.uppercase()) {
    "US", "PR", "GU", "VI", "AS", "MP", "UM", "BS", "BZ", "KY", "PW", "FM", "MH", "LR" -> UnitSettings(
        temperature = TemperatureUnit.FAHRENHEIT,
        wind = WindUnit.MPH,
        pressure = PressureUnit.INHG,
        use24h = use24h,
        precipitation = PrecipitationUnit.INCHES,
        distance = DistanceUnit.MILES,
    )
    "GB" -> UnitSettings(wind = WindUnit.MPH, use24h = use24h, distance = DistanceUnit.MILES)
    else -> UnitSettings(use24h = use24h)
}

/**
 * Texto corto para mandar las unidades del teléfono al reloj: "CELSIUS,KMH,HPA,true". La lluvia
 * y la distancia no van: el reloj no las muestra, y uno con una versión anterior solo entiende
 * cuatro partes.
 */
fun UnitSettings.encode(): String = "${temperature.name},${wind.name},${pressure.name},$use24h"

/** Acepta partes de más (lluvia y distancia), por si un teléfono más nuevo las manda. */
fun decodeUnits(text: String?): UnitSettings? {
    val parts = text?.split(',') ?: return null
    if (parts.size < 4) return null
    return UnitSettings(
        temperature = TemperatureUnit.entries.firstOrNull { it.name == parts[0] } ?: return null,
        wind = WindUnit.entries.firstOrNull { it.name == parts[1] } ?: return null,
        pressure = PressureUnit.entries.firstOrNull { it.name == parts[2] } ?: return null,
        use24h = parts[3].toBooleanStrictOrNull() ?: return null,
        precipitation = PrecipitationUnit.entries.firstOrNull { it.name == parts.getOrNull(4) } ?: PrecipitationUnit.MM,
        distance = DistanceUnit.entries.firstOrNull { it.name == parts.getOrNull(5) } ?: DistanceUnit.KM,
    )
}

/** Ruta del dato que el teléfono comparte con el reloj (Wear OS Data Layer). */
const val WEAR_SYNC_PATH = "/clima"

/** Mensaje del reloj al teléfono: "actualizá y mandame el clima". */
const val WEAR_REFRESH_PATH = "/actualizar"

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

/**
 * Lluvia en la unidad elegida. Milímetros: con decimal si es poco ("2.5 mm"), redondeado si es
 * mucho ("34 mm"). Pulgadas: dos decimales ("0.10 in"), uno desde 10.
 */
fun mmText(mm: Double): String = when (Units.current.precipitation) {
    PrecipitationUnit.MM -> when {
        mm >= 10 -> "${mm.roundToInt()} mm"
        else -> String.format(java.util.Locale.US, "%.1f", mm).removeSuffix(".0") + " mm"
    }
    PrecipitationUnit.INCHES -> {
        val inches = mm / 25.4
        String.format(java.util.Locale.US, if (inches >= 10) "%.1f in" else "%.2f in", inches)
    }
}

/** Distancia en la unidad elegida: "12 km" o "7 mi"; con un decimal si es menos de 10 ("0.6 mi"). */
fun distanceText(km: Double): String {
    val unit = Units.current.distance
    val value = if (unit == DistanceUnit.MILES) km / 1.609344 else km
    val number = if (value >= 10) value.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", value).removeSuffix(".0")
    return "$number ${unit.label}"
}

/**
 * Visibilidad (llega en metros). "+10 km" cuando se ve lejos y en metros con niebla; en millas,
 * "+6 mi" y pies por debajo de un décimo de milla.
 */
fun visibilityText(meters: Double): String = when (Units.current.distance) {
    DistanceUnit.KM -> {
        val km = meters / 1000
        if (km >= 10) "+10 km" else if (km < 1) "${meters.roundToInt()} m" else "${km.roundToInt()} km"
    }
    DistanceUnit.MILES -> {
        val miles = meters / 1609.344
        when {
            miles >= 6 -> "+6 mi"
            miles >= 1 -> "${miles.roundToInt()} mi"
            miles >= 0.1 -> String.format(java.util.Locale.US, "%.1f mi", miles)
            // De a 50 pies: más detalle no dice nada.
            else -> "${((meters * 3.28084) / 50).roundToInt() * 50} ft"
        }
    }
}
