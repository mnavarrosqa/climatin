package com.mnavarrosqa.clima.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mnavarrosqa.clima.data.NoLocationException
import com.mnavarrosqa.clima.data.WeatherRepository
import java.util.concurrent.TimeUnit

/**
 * Refresca el clima aunque la app esté cerrada: cada 15 minutos si el aviso de lluvia está
 * prendido (es lo mínimo que permite Android), si no cada 30.
 */
class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // "Actualizar" desde una notificación fuerza la descarga aunque se haya actualizado hace poco.
        val force = inputData.getBoolean(FORCE, false)
        val repository = WeatherRepository.get(applicationContext)
        // Sin conexión no se baja nada, pero los widgets se redibujan igual: así muestran que los
        // datos son viejos y la hora del pronóstico avanza (antes el trabajo ni corría).
        if (!online(applicationContext)) {
            afterRefresh(applicationContext)
            return Result.success()
        }
        // Los avisos solo miran "donde estoy": eso va cada vez, y las ciudades guardadas una vez
        // por hora (o al forzar). Antes se bajaban todas cada 15 minutos.
        val error = repository.refreshAll(force = force, cities = force || repository.citiesStale())
        afterRefresh(applicationContext)
        return when {
            // Sin ubicación ni ciudades no tiene sentido reintentar.
            error == null || error is NoLocationException -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }

    private fun online(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    companion object {
        private const val NAME = "clima-periodico"
        const val FORCE = "forzar"

        fun schedule(context: Context) {
            val minutes = if (WeatherRepository.get(context).rainAlertsEnabled) 15L else 30L
            // Sin pedir conexión: sin red el trabajo igual redibuja los widgets.
            val request = PeriodicWorkRequestBuilder<WeatherWorker>(minutes, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                // UPDATE cambia el intervalo sin perder el turno que ya estaba agendado.
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
