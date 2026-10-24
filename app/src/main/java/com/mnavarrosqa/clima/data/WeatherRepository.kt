package com.mnavarrosqa.clima.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import kotlin.math.abs

class NoLocationException : Exception("Todavía no tengo tu ubicación")

/** Fuente única para la app y los widgets: pide el clima y lo deja en caché. */
class WeatherRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("clima", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun cached(): WeatherSnapshot? =
        prefs.getString(KEY_SNAPSHOT, null)?.let { raw ->
            runCatching { json.decodeFromString(WeatherSnapshot.serializer(), raw) }.getOrNull()
        }

    /** Emite la caché actual y cada vez que cambia. */
    val snapshots: Flow<WeatherSnapshot?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_SNAPSHOT) trySend(cached())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(cached())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    suspend fun refresh(): Result<WeatherSnapshot> = runCatching {
        val previous = cached()
        val coordinates = LocationProvider.current(context)
            ?: previous?.let { Coordinates(it.latitude, it.longitude) }
            ?: throw NoLocationException()

        val samePlace = previous != null &&
            abs(previous.latitude - coordinates.latitude) < 0.02 &&
            abs(previous.longitude - coordinates.longitude) < 0.02
        val place = LocationProvider.placeName(context, coordinates)
            ?: previous?.place?.takeIf { samePlace }

        val forecast = OpenMeteo.forecast(coordinates.latitude, coordinates.longitude)
        val snapshot = OpenMeteo.toSnapshot(
            forecast = forecast,
            place = place,
            latitude = coordinates.latitude,
            longitude = coordinates.longitude,
            fetchedAt = System.currentTimeMillis(),
        )
        prefs.edit().putString(KEY_SNAPSHOT, json.encodeToString(WeatherSnapshot.serializer(), snapshot)).apply()
        snapshot
    }

    companion object {
        private const val KEY_SNAPSHOT = "snapshot"

        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }
    }
}
