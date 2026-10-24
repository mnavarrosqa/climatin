package com.mnavarrosqa.clima.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mnavarrosqa.clima.data.NoLocationException
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.widget.updateWeatherWidgets
import java.util.concurrent.TimeUnit

/** Refresca el clima cada 30 minutos aunque la app esté cerrada. */
class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = WeatherRepository.get(applicationContext).refresh()
        updateWeatherWidgets(applicationContext)
        return when {
            result.isSuccess -> Result.success()
            result.exceptionOrNull() is NoLocationException -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val NAME = "clima-periodico"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
