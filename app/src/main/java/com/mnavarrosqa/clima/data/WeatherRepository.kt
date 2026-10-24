package com.mnavarrosqa.clima.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

class NoLocationException : Exception("Todavía no tengo tu ubicación")

/**
 * Fuente única para la app y los widgets. Guarda las ciudades y el último pronóstico de cada
 * lugar ("current" es donde estás) en SharedPreferences.
 */
class WeatherRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("clima", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val placesSerializer = ListSerializer(Place.serializer())

    init {
        Units.current = readUnits()
    }

    // --- Lugares ---

    fun places(): List<Place> =
        prefs.getString(KEY_PLACES, null)?.let { raw ->
            runCatching { json.decodeFromString(placesSerializer, raw) }.getOrNull()
        }.orEmpty()

    val placesFlow: Flow<List<Place>> = prefsFlow(KEY_PLACES) { places() }

    fun addPlace(place: Place) {
        val current = places()
        if (current.any { it.id == place.id }) return
        savePlaces(current + place)
    }

    fun removePlace(id: String) {
        savePlaces(places().filterNot { it.id == id })
        prefs.edit().remove(snapshotKey(id)).apply()
    }

    fun movePlace(id: String, offset: Int) {
        val list = places().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + offset
        if (index < 0 || target !in list.indices) return
        list.add(target, list.removeAt(index))
        savePlaces(list)
    }

    private fun savePlaces(list: List<Place>) {
        prefs.edit().putString(KEY_PLACES, json.encodeToString(placesSerializer, list)).apply()
    }

    /** Si la ciudad de un widget se borró, vuelve a "donde estoy". */
    fun resolvePlaceId(id: String?): String =
        if (id == null || id == CURRENT_PLACE_ID || places().none { it.id == id }) CURRENT_PLACE_ID else id

    // --- Pronósticos ---

    fun cached(placeId: String = CURRENT_PLACE_ID): WeatherSnapshot? =
        prefs.getString(snapshotKey(placeId), null)?.let { raw ->
            runCatching { json.decodeFromString(WeatherSnapshot.serializer(), raw) }.getOrNull()
        }

    /** Emite la caché del lugar y cada vez que cambia. */
    fun snapshots(placeId: String = CURRENT_PLACE_ID): Flow<WeatherSnapshot?> =
        prefsFlow(snapshotKey(placeId)) { cached(placeId) }

    /** Actualiza "donde estoy" y todas las ciudades guardadas. Devuelve el primer error, si hubo. */
    suspend fun refreshAll(): Throwable? {
        var firstError: Throwable? = null
        val current = refreshCurrent()
        if (current.isFailure && current.exceptionOrNull() !is NoLocationException) {
            firstError = current.exceptionOrNull()
        }
        for (place in places()) {
            val result = refreshPlace(place)
            if (firstError == null) firstError = result.exceptionOrNull()
        }
        if (firstError == null && places().isEmpty()) firstError = current.exceptionOrNull()
        return firstError
    }

    // Las alertas del SMN se piden una vez y sirven para todos los lugares durante 10 minutos.
    private var capCache: Pair<Long, List<CapAlert>>? = null

    private suspend fun capAlerts(): List<CapAlert> {
        capCache?.let { (at, alerts) -> if (System.currentTimeMillis() - at < 10 * 60_000) return alerts }
        val alerts = runCatching { SmnAlerts.fetchAll() }.getOrElse { return capCache?.second.orEmpty() }
        capCache = System.currentTimeMillis() to alerts
        return alerts
    }

    suspend fun refreshCurrent(): Result<WeatherSnapshot> = runCatching {
        val previous = cached(CURRENT_PLACE_ID)
        val coordinates = LocationProvider.current(context)
            ?: previous?.let { Coordinates(it.latitude, it.longitude) }
            ?: throw NoLocationException()

        val samePlace = previous != null &&
            abs(previous.latitude - coordinates.latitude) < 0.02 &&
            abs(previous.longitude - coordinates.longitude) < 0.02
        val name = LocationProvider.placeName(context, coordinates)
            ?: previous?.place?.takeIf { samePlace }
        fetch(CURRENT_PLACE_ID, name, coordinates.latitude, coordinates.longitude)
    }

    suspend fun refreshPlace(place: Place): Result<WeatherSnapshot> = runCatching {
        fetch(place.id, place.name, place.latitude, place.longitude)
    }

    private suspend fun fetch(placeId: String, name: String?, latitude: Double, longitude: Double): WeatherSnapshot {
        val forecast = OpenMeteo.forecast(latitude, longitude)
        val air = OpenMeteo.airQuality(latitude, longitude)
        val alerts = if (Geo.isInArgentina(latitude, longitude)) {
            capAlerts().filter { it.covers(latitude, longitude) }.map { it.alert }
        } else {
            emptyList()
        }
        val snapshot = OpenMeteo.toSnapshot(
            forecast = forecast,
            placeId = placeId,
            place = name,
            latitude = latitude,
            longitude = longitude,
            airQuality = air,
            fetchedAt = System.currentTimeMillis(),
        ).copy(alerts = alerts)
        prefs.edit()
            .putString(snapshotKey(placeId), json.encodeToString(WeatherSnapshot.serializer(), snapshot))
            .apply()
        return snapshot
    }

    // --- Ajustes ---

    var rainAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_RAIN_ALERTS, false)
        set(value) = prefs.edit().putBoolean(KEY_RAIN_ALERTS, value).apply()

    val rainAlertsFlow: Flow<Boolean> = prefsFlow(KEY_RAIN_ALERTS) { rainAlertsEnabled }

    var smnAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMN_ALERTS, false)
        set(value) = prefs.edit().putBoolean(KEY_SMN_ALERTS, value).apply()

    val smnAlertsFlow: Flow<Boolean> = prefsFlow(KEY_SMN_ALERTS) { smnAlertsEnabled }

    /** Ids de alertas ya notificadas. */
    var notifiedAlerts: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFIED_ALERTS, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_NOTIFIED_ALERTS, value).apply()

    /** Momento hasta el que no se vuelve a avisar de lluvia, para no repetir la notificación. */
    var rainNotifiedUntil: Long
        get() = prefs.getLong(KEY_RAIN_NOTIFIED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_RAIN_NOTIFIED_UNTIL, value).apply()

    // --- Unidades ---

    private fun readUnits() = UnitSettings(
        temperature = enumOr(prefs.getString(KEY_UNIT_TEMP, null), TemperatureUnit.CELSIUS),
        wind = enumOr(prefs.getString(KEY_UNIT_WIND, null), WindUnit.KMH),
        pressure = enumOr(prefs.getString(KEY_UNIT_PRESSURE, null), PressureUnit.HPA),
        use24h = prefs.getBoolean(KEY_USE_24H, true),
    )

    var units: UnitSettings
        get() = Units.current
        set(value) {
            Units.current = value
            prefs.edit()
                .putString(KEY_UNIT_TEMP, value.temperature.name)
                .putString(KEY_UNIT_WIND, value.wind.name)
                .putString(KEY_UNIT_PRESSURE, value.pressure.name)
                .putBoolean(KEY_USE_24H, value.use24h)
                .apply()
        }

    val unitsFlow: Flow<UnitSettings> = prefsFlow(KEY_USE_24H, KEY_UNIT_TEMP, KEY_UNIT_WIND, KEY_UNIT_PRESSURE) { readUnits() }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private fun <T> prefsFlow(vararg keys: String, read: () -> T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed in keys) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        private const val KEY_PLACES = "places"
        private const val KEY_RAIN_ALERTS = "rain_alerts"
        private const val KEY_RAIN_NOTIFIED_UNTIL = "rain_notified_until"
        private const val KEY_SMN_ALERTS = "smn_alerts"
        private const val KEY_NOTIFIED_ALERTS = "notified_alerts"
        private const val KEY_UNIT_TEMP = "unit_temp"
        private const val KEY_UNIT_WIND = "unit_wind"
        private const val KEY_UNIT_PRESSURE = "unit_pressure"
        private const val KEY_USE_24H = "use_24h"

        private fun snapshotKey(placeId: String) = "snapshot:$placeId"

        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }
    }
}
