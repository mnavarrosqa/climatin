package com.mnavarrosqa.clima.work

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.mnavarrosqa.clima.data.Units
import com.mnavarrosqa.clima.data.WEAR_SYNC_PATH
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.encode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Le pasa al reloj el pronóstico de donde estás, con las unidades y el nombre (o apodo) que usa
 * el teléfono. Así el reloj no tiene que bajar todo por su cuenta ni pedir ubicación: solo lo hace
 * si el teléfono no está cerca. Sin reloj (o sin servicios de Google) no hace nada.
 */
object WearSync {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Manda lo último que haya. Solo si cambió algo desde el último envío, y "urgente" (despierta
     * la radio del reloj) solo cuando lo pide el usuario: si no, lo entrega el sistema cuando le
     * conviene, sin gastar batería del reloj cada 15 minutos.
     */
    suspend fun send(context: Context, urgent: Boolean = false) = withContext(Dispatchers.IO) {
        val snapshot = WeatherRepository.get(context).cached() ?: return@withContext
        val units = Units.current.encode()
        val stamp = "${snapshot.fetchedAt}|$units|${snapshot.place}"
        val prefs = context.getSharedPreferences("clima", Context.MODE_PRIVATE)
        if (!urgent && prefs.getString(KEY_SENT, null) == stamp) return@withContext
        try {
            // Lo que muestra el reloj: 2 días por hora y una semana. Entra holgado en el límite
            // de 100 KB de la Data Layer.
            val light: WeatherSnapshot = snapshot.copy(hours = snapshot.upcomingHours(48), days = snapshot.days.take(8))
            val request = PutDataMapRequest.create(WEAR_SYNC_PATH).apply {
                dataMap.putString("snapshot", json.encodeToString(WeatherSnapshot.serializer(), light))
                dataMap.putString("units", units)
                dataMap.putLong("at", snapshot.fetchedAt)
            }.asPutDataRequest().apply { if (urgent) setUrgent() }
            Wearable.getDataClient(context).putDataItem(request).await()
            prefs.edit().putString(KEY_SENT, stamp).apply()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Sin reloj o sin servicios de Google: no pasa nada.
        }
    }

    private const val KEY_SENT = "reloj_enviado"
}
