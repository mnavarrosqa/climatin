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
import kotlinx.coroutines.CancellationException
import com.mnavarrosqa.clima.data.Units
import com.mnavarrosqa.clima.data.decodeUnits

/**
 * Caché del clima en el reloj. Lo normal es que lo mande el teléfono (con sus unidades y el nombre
 * del lugar); si hace rato que no llega nada, el reloj lo baja solo de Open-Meteo.
 */
object WearStore {
    private val json = Json { ignoreUnknownKeys = true }
    private const val KEY = "snapshot"
    private const val UNITS = "unidades"
    private const val FROM_PHONE_AT = "del_telefono"

    /** Con datos del teléfono de hace menos de esto, el reloj no descarga por su cuenta. */
    private const val PHONE_FRESH_MS = 45 * 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences("clima_wear", Context.MODE_PRIVATE)

    // Decodificado una sola vez mientras no cambie: la tarjeta y la complicación lo leen seguido.
    @Volatile
    private var decoded: Pair<String, WeatherSnapshot>? = null

    fun cached(context: Context): WeatherSnapshot? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        decoded?.let { (text, snapshot) -> if (text == raw) return snapshot }
        return runCatching { json.decodeFromString(WeatherSnapshot.serializer(), raw) }.getOrNull()
            ?.also { decoded = raw to it }
    }

    /** Las unidades que eligió la persona en el teléfono (°C/°F, viento, 12/24 h). */
    fun loadUnits(context: Context) {
        decodeUnits(prefs(context).getString(UNITS, null))?.let { Units.current = it }
    }

    fun saveFromPhone(context: Context, snapshot: String?, units: String?) {
        val valid = snapshot?.takeIf { runCatching { json.decodeFromString(WeatherSnapshot.serializer(), it) }.isSuccess }
        prefs(context).edit().apply {
            valid?.let { putString(KEY, it) }
            units?.let { putString(UNITS, it) }
            putLong(FROM_PHONE_AT, System.currentTimeMillis())
        }.apply()
        decodeUnits(units)?.let { Units.current = it }
        requestUpdates(context)
    }

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Si el teléfono mandó datos hace poco, alcanza con esos. */
    fun phoneIsFresh(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(FROM_PHONE_AT, 0L) < PHONE_FRESH_MS

    @SuppressLint("MissingPermission")
    suspend fun refresh(context: Context, force: Boolean = false): Result<WeatherSnapshot> {
        if (!force && phoneIsFresh(context)) cached(context)?.let { return Result.success(it) }
        return try {
            Result.success(download(context))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun download(context: Context): WeatherSnapshot {
        val previous = cached(context)
        val location = if (hasPermission(context)) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            // Primero la última conocida (gratis); si no hay, una nueva con tiempo máximo. El
            // pedido se cancela al vencer: si no, seguía buscando y gastando batería.
            client.lastLocation.awaitOrNull() ?: run {
                val cancel = CancellationTokenSource()
                try {
                    withTimeoutOrNull(15_000) {
                        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token).await()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    null
                } finally {
                    cancel.cancel()
                }
            }
        } else {
            null
        }
        val latitude = location?.latitude ?: previous?.latitude ?: error("Sin ubicación")
        val longitude = location?.longitude ?: previous?.longitude ?: error("Sin ubicación")
        // El reloj muestra pocos días: con 5 alcanza (antes bajaba 15 cada 30 minutos).
        val forecast = OpenMeteo.forecast(latitude, longitude, days = 5)
        val samePlace = previous != null && kotlin.math.abs(previous.latitude - latitude) < 0.05 && kotlin.math.abs(previous.longitude - longitude) < 0.05
        val snapshot = OpenMeteo.toSnapshot(
            forecast, CURRENT_PLACE_ID, previous?.place?.takeIf { samePlace }, latitude, longitude, null, System.currentTimeMillis(),
        )
        prefs(context).edit().putString(KEY, json.encodeToString(WeatherSnapshot.serializer(), snapshot)).apply()
        requestUpdates(context)
        return snapshot
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? = try {
        await()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        null
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
