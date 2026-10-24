package com.mnavarrosqa.clima.data

import com.mnavarrosqa.clima.data.tr
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

class NoLocationException : Exception(tr("Todavía no tengo tu ubicación", "I don't have your location yet"))

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

    @Volatile
    private var placesDecoded: Pair<String, List<Place>>? = null

    /** Ciudades guardadas; se decodifican una sola vez mientras no cambien (los widgets las leen seguido). */
    fun places(): List<Place> {
        val raw = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        placesDecoded?.let { (text, list) -> if (text == raw) return list }
        val list = runCatching { json.decodeFromString(placesSerializer, raw) }.getOrNull().orEmpty()
        placesDecoded = raw to list
        return list
    }

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

    // Los widgets leen la caché a cada rato: se guarda decodificada mientras el texto no cambie.
    private val decoded = java.util.concurrent.ConcurrentHashMap<String, Pair<String, WeatherSnapshot>>()

    fun cached(placeId: String = CURRENT_PLACE_ID): WeatherSnapshot? {
        val raw = prefs.getString(snapshotKey(placeId), null) ?: return null
        decoded[placeId]?.let { (text, snapshot) -> if (text == raw) return snapshot }
        val snapshot = runCatching { json.decodeFromString(WeatherSnapshot.serializer(), raw) }.getOrNull() ?: return null
        decoded[placeId] = raw to snapshot
        return snapshot
    }

    /** Emite la caché del lugar y cada vez que cambia. */
    fun snapshots(placeId: String = CURRENT_PLACE_ID): Flow<WeatherSnapshot?> =
        prefsFlow(snapshotKey(placeId)) { cached(placeId) }

    /** "Donde estoy" y todas las ciudades guardadas que tengan pronóstico, en orden. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun allSnapshots(): Flow<List<WeatherSnapshot>> = placesFlow.flatMapLatest { places ->
        val ids = listOf(CURRENT_PLACE_ID) + places.map { it.id }
        combine(ids.map { snapshots(it) }) { list -> list.filterNotNull() }
    }

    fun allCached(): List<WeatherSnapshot> =
        (listOf(CURRENT_PLACE_ID) + places().map { it.id }).mapNotNull { cached(it) }

    private val refreshLock = Mutex()
    @Volatile
    private var lastRefreshAt = 0L

    /**
     * Actualiza "donde estoy" y todas las ciudades guardadas, en paralelo. Devuelve el primer error,
     * si hubo. Si ya hay una actualización en curso se espera a esa, y si terminó hace menos de un
     * minuto no se vuelve a pedir (la app, el widget y el refresco periódico pueden coincidir).
     */
    suspend fun refreshAll(force: Boolean = false): Throwable? = refreshLock.withLock {
        if (!force && System.currentTimeMillis() - lastRefreshAt < 60_000) return@withLock null
        val (current, others) = coroutineScope {
            val currentJob = async { refreshCurrent() }
            val placeJobs = places().map { place -> async { refreshPlace(place) } }
            currentJob.await() to placeJobs.awaitAll()
        }
        val firstError = (listOf(current.exceptionOrNull()?.takeIf { it !is NoLocationException }) +
            others.map { it.exceptionOrNull() }).firstOrNull { it != null }
            ?: current.exceptionOrNull()?.takeIf { others.isEmpty() }
        if (firstError == null) lastRefreshAt = System.currentTimeMillis()
        firstError
    }

    // Las alertas del SMN se piden una vez y sirven para todos los lugares durante 10 minutos.
    private var capCache: Pair<Long, List<CapAlert>>? = null

    private val capLock = Mutex()

    private suspend fun capAlerts(): List<CapAlert> = capLock.withLock {
        capCache?.let { (at, alerts) -> if (System.currentTimeMillis() - at < 10 * 60_000) return@withLock alerts }
        val alerts = runCatching { SmnAlerts.fetchAll(context.cacheDir) }
            .getOrElse { return@withLock capCache?.second.orEmpty() }
        capCache = System.currentTimeMillis() to alerts
        alerts
    }

    suspend fun refreshCurrent(): Result<WeatherSnapshot> = runCatching {
        val previous = cached(CURRENT_PLACE_ID)
        val coordinates = LocationProvider.current(context)
            ?: previous?.let { Coordinates(it.latitude, it.longitude) }
            ?: throw NoLocationException()

        val samePlace = previous != null &&
            abs(previous.latitude - coordinates.latitude) < 0.02 &&
            abs(previous.longitude - coordinates.longitude) < 0.02
        // Mismo lugar con nombre conocido: no hace falta volver a preguntarle al geocodificador.
        val name = previous?.place?.takeIf { samePlace }
            ?: LocationProvider.placeName(context, coordinates)
        fetch(CURRENT_PLACE_ID, name, coordinates.latitude, coordinates.longitude)
    }

    suspend fun refreshPlace(place: Place): Result<WeatherSnapshot> = runCatching {
        fetch(place.id, place.name, place.latitude, place.longitude)
    }

    private suspend fun fetch(placeId: String, name: String?, latitude: Double, longitude: Double): WeatherSnapshot {
        val (forecast, air, alerts) = coroutineScope {
            val forecast = async { OpenMeteo.forecast(latitude, longitude) }
            val air = async { OpenMeteo.airQuality(latitude, longitude) }
            val alerts = async {
                if (Geo.isInArgentina(latitude, longitude)) {
                    capAlerts().filter { it.covers(latitude, longitude) }.map { it.alert }
                } else {
                    emptyList()
                }
            }
            Triple(forecast.await(), air.await(), alerts.await())
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

    var statusTemperatureEnabled: Boolean
        get() = prefs.getBoolean(KEY_STATUS_TEMP, false)
        set(value) = prefs.edit().putBoolean(KEY_STATUS_TEMP, value).apply()

    val statusTemperatureFlow: Flow<Boolean> = prefsFlow(KEY_STATUS_TEMP) { statusTemperatureEnabled }

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
        private const val KEY_STATUS_TEMP = "status_temperature"
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
