package com.mnavarrosqa.clima.wear

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.OpenMeteo
import com.mnavarrosqa.clima.data.WeatherSnapshot
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/** Caché del clima en el reloj y su actualización desde Open-Meteo. */
object WearStore {
    private val json = Json { ignoreUnknownKeys = true }
    private const val KEY = "snapshot"

    private fun prefs(context: Context) = context.getSharedPreferences("clima_wear", Context.MODE_PRIVATE)

    fun cached(context: Context): WeatherSnapshot? =
        prefs(context).getString(KEY, null)?.let {
            runCatching { json.decodeFromString(WeatherSnapshot.serializer(), it) }.getOrNull()
        }

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun refresh(context: Context): Result<WeatherSnapshot> = runCatching {
        val previous = cached(context)
        val location = if (hasPermission(context)) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            runCatching {
                withTimeoutOrNull(15_000) {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
                } ?: client.lastLocation.await()
            }.getOrNull()
        } else {
            null
        }
        val latitude = location?.latitude ?: previous?.latitude ?: error("Sin ubicación")
        val longitude = location?.longitude ?: previous?.longitude ?: error("Sin ubicación")
        val forecast = OpenMeteo.forecast(latitude, longitude)
        val snapshot = OpenMeteo.toSnapshot(
            forecast, CURRENT_PLACE_ID, previous?.place, latitude, longitude, null, System.currentTimeMillis(),
        )
        prefs(context).edit().putString(KEY, json.encodeToString(WeatherSnapshot.serializer(), snapshot)).apply()
        requestUpdates(context)
        snapshot
    }

    /** Avisa a la tarjeta y a la complicación que hay datos nuevos. */
    fun requestUpdates(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(WeatherTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, TemperatureComplicationService::class.java),
            ).requestUpdateAll()
        }
    }
}
