package com.mnavarrosqa.clima.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class Coordinates(val latitude: Double, val longitude: Double)

object LocationProvider {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Android 10+ pide un permiso aparte para usar la ubicación con la app cerrada. */
    fun hasBackgroundPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Ubicación aproximada (alcanza para el clima). Desde segundo plano Android puede no darla:
     * en ese caso devuelve null y se usa la última guardada.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Coordinates? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        // Si vence el tiempo se cancela el pedido a Play Services: si no, sigue buscando y gasta batería.
        val token = CancellationTokenSource()
        val location = try {
            runCatching {
                withTimeoutOrNull(15_000) {
                    // Una ubicación de hasta 15 minutos alcanza para el clima: si el sistema ya
                    // tiene una, no se prende el Wi-Fi ni la red para buscar otra en cada actualización.
                    val request = CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                        .setMaxUpdateAgeMillis(15 * 60_000L)
                        .setDurationMillis(15_000L)
                        .build()
                    client.getCurrentLocation(request, token.token).await()
                } ?: client.lastLocation.await()
            }.getOrNull()
        } finally {
            token.cancel()
        } ?: return null
        return Coordinates(location.latitude, location.longitude)
    }

    /**
     * La última ubicación que ya tiene el sistema, sin prender nada, si no tiene más de
     * [maxAgeMillis]. Contesta al toque: sirve para arrancar sin esperar una ubicación nueva.
     */
    @SuppressLint("MissingPermission")
    suspend fun lastKnown(context: Context, maxAgeMillis: Long): Coordinates? {
        if (!hasPermission(context)) return null
        val location = try {
            withTimeoutOrNull(2_000) { LocationServices.getFusedLocationProviderClient(context).lastLocation.await() }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            null
        } ?: return null
        // Por el reloj desde el arranque: la hora del sistema puede cambiar y la edad no.
        val ageMillis = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (ageMillis !in 0..maxAgeMillis) return null
        return Coordinates(location.latitude, location.longitude)
    }

    /** Nombre de la ciudad o barrio, si el teléfono tiene geocodificador. */
    suspend fun placeName(context: Context, coordinates: Coordinates): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(10_000) {
                    suspendCancellableCoroutine<String?> { cont ->
                        geocoder.getFromLocation(
                            coordinates.latitude,
                            coordinates.longitude,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (cont.isActive) cont.resume(addresses.firstOrNull()?.label())
                                }

                                override fun onError(errorMessage: String?) {
                                    if (cont.isActive) cont.resume(null)
                                }
                            },
                        )
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)
                    ?.firstOrNull()
                    ?.label()
            }
        }.getOrNull()
    }

    private fun Address.label(): String? = locality ?: subAdminArea ?: adminArea
}
