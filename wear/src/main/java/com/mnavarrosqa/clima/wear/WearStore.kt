package com.mnavarrosqa.clima.wear

import kotlinx.coroutines.delay
import com.mnavarrosqa.clima.data.WEAR_REFRESH_PATH
import com.google.android.gms.wearable.Wearable
import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
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
    /** Cuándo llegó lo último del teléfono (hora del reloj): solo para saber que contestó. */
    private const val FROM_PHONE_AT = "del_telefono"

    /** Cuándo bajó el teléfono lo último que mandó (su fetchedAt): esto dice si sirve. */
    private const val PHONE_DATA_AT = "dato_del_telefono"

    /** Con datos del teléfono de hace menos de esto, el reloj no descarga por su cuenta. */
    private const val PHONE_FRESH_MS = 45 * 60_000L

    /**
     * Al pedir "Actualizar", lo que manda el teléfono tiene que ser de hace menos de esto. Sin red
     * o si su descarga falla manda lo que tenía guardado, y eso no es una respuesta al pedido.
     */
    private const val ASKED_FRESH_MS = 10 * 60_000L

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

    /**
     * Avisa cuando cambia lo guardado (llegó algo del teléfono o se bajó solo), para que la
     * pantalla abierta se actualice. Android guarda el listener con referencia débil: quien lo
     * registra tiene que conservarlo.
     */
    fun listen(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).registerOnSharedPreferenceChangeListener(listener)

    fun stopListening(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)

    /** Las unidades que eligió la persona en el teléfono (°C/°F, viento, 12/24 h). */
    fun loadUnits(context: Context) {
        decodeUnits(prefs(context).getString(UNITS, null))?.let { Units.current = it }
    }

    fun saveFromPhone(context: Context, snapshot: String?, units: String?) {
        val incoming = snapshot?.let { text ->
            runCatching { json.decodeFromString(WeatherSnapshot.serializer(), text) }.getOrNull()?.let { text to it }
        }
        // Si el reloj bajó algo más nuevo por su cuenta, no se pisa con lo viejo del teléfono.
        val stored = cached(context)
        val newer = incoming?.takeIf { stored == null || it.second.fetchedAt >= stored.fetchedAt }
        prefs(context).edit().apply {
            newer?.let { putString(KEY, it.first) }
            incoming?.let { putLong(PHONE_DATA_AT, it.second.fetchedAt) }
            units?.let { putString(UNITS, it) }
            putLong(FROM_PHONE_AT, System.currentTimeMillis())
        }.apply()
        decodeUnits(units)?.let { Units.current = it }
        requestUpdates(context)
    }

    /**
     * Le pide al teléfono que actualice y espera hasta 20 s a que mande el clima nuevo. Null si no
     * hay teléfono conectado, no llegó a tiempo o lo que mandó es viejo (sin red o falló su descarga).
     */
    private suspend fun askPhone(context: Context): WeatherSnapshot? {
        val before = prefs(context).getLong(FROM_PHONE_AT, 0L)
        val sent = try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val client = Wearable.getMessageClient(context)
            nodes.count { node ->
                runCatching { client.sendMessage(node.id, WEAR_REFRESH_PATH, ByteArray(0)).await() }.isSuccess
            } > 0
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            false
        }
        if (!sent) return null
        repeat(40) {
            delay(500)
            if (prefs(context).getLong(FROM_PHONE_AT, 0L) > before) {
                // Se juzga por cuándo se bajó, no por cuándo llegó: el teléfono contesta enseguida
                // con lo guardado si no pudo bajar, y así el reloj baja por su cuenta sin esperar.
                val answer = cached(context) ?: return null
                return answer.takeIf { System.currentTimeMillis() - answer.fetchedAt < ASKED_FRESH_MS }
            }
        }
        return null
    }

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Si lo que mandó el teléfono se bajó hace poco, alcanza con eso. Por la hora de descarga y no
     * por la de llegada: el teléfono sin red reenvía datos viejos y no tienen que frenar al reloj.
     */
    fun phoneIsFresh(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(PHONE_DATA_AT, 0L) < PHONE_FRESH_MS

    @SuppressLint("MissingPermission")
    suspend fun refresh(context: Context, force: Boolean = false): Result<WeatherSnapshot> {
        if (!force && phoneIsFresh(context)) cached(context)?.let { return Result.success(it) }
        // A pedido: primero se le pide al teléfono, que ya tiene la ubicación y la red; el reloj
        // baja por su cuenta solo si no hay teléfono cerca o no contesta.
        if (force) askPhone(context)?.let { return Result.success(it) }
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
        // La app del reloj muestra la semana: con 7 días alcanza (antes bajaba 15 cada 30 minutos).
        val forecast = OpenMeteo.forecast(latitude, longitude, days = 7)
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
