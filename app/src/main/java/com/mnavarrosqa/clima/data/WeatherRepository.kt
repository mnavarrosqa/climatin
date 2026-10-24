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
        val snapshot = OpenMeteo.toSnapshot(
            forecast = forecast,
            placeId = placeId,
            place = name,
            latitude = latitude,
            longitude = longitude,
            airQuality = air,
            fetchedAt = System.currentTimeMillis(),
        )
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

    /** Momento hasta el que no se vuelve a avisar de lluvia, para no repetir la notificación. */
    var rainNotifiedUntil: Long
        get() = prefs.getLong(KEY_RAIN_NOTIFIED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_RAIN_NOTIFIED_UNTIL, value).apply()

    private fun <T> prefsFlow(key: String, read: () -> T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        private const val KEY_PLACES = "places"
        private const val KEY_RAIN_ALERTS = "rain_alerts"
        private const val KEY_RAIN_NOTIFIED_UNTIL = "rain_notified_until"

        private fun snapshotKey(placeId: String) = "snapshot:$placeId"

        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }
    }
}
