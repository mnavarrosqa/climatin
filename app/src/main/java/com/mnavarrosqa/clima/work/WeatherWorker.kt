package com.mnavarrosqa.clima.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.widget.updateWeatherWidgets
import java.util.concurrent.TimeUnit

/**
 * Refresca el clima aunque la app esté cerrada: cada 15 minutos si el aviso de lluvia está
 * prendido (es lo mínimo que permite Android), si no cada 30.
 */
class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val error = WeatherRepository.get(applicationContext).refreshAll()
        updateWeatherWidgets(applicationContext)
        RainNotifier.check(applicationContext)
        AlertNotifier.check(applicationContext)
        ChangeNotifier.check(applicationContext)
        TemperatureNotifier.update(applicationContext)
        return when {
            error == null -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val NAME = "clima-periodico"

        fun schedule(context: Context) {
            val minutes = if (WeatherRepository.get(context).rainAlertsEnabled) 15L else 30L
            val request = PeriodicWorkRequestBuilder<WeatherWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                // UPDATE cambia el intervalo sin perder el turno que ya estaba agendado.
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
