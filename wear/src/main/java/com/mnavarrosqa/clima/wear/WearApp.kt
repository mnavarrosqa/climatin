package com.mnavarrosqa.clima.wear

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WearStore.loadUnits(this)
        // Cada hora alcanza: casi siempre el teléfono manda los datos y el trabajo no baja nada.
        val request = PeriodicWorkRequestBuilder<WearWorker>(60, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("clima-reloj", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class WearWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Sin permiso de ubicación ni datos guardados no hay nada que reintentar.
        if (!WearStore.hasPermission(applicationContext) && WearStore.cached(applicationContext) == null) return Result.success()
        return if (WearStore.refresh(applicationContext).isSuccess || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}
