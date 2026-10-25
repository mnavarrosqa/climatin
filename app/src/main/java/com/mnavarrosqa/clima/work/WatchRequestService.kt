package com.mnavarrosqa.clima.work

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.mnavarrosqa.clima.data.WEAR_REFRESH_PATH

/**
 * "Actualizar" en el reloj: el reloj le pide al teléfono el clima en vez de bajarlo por su cuenta
 * (gasta menos batería del reloj y usa la ubicación del teléfono). Se baja forzado y se le manda
 * enseguida, con prioridad.
 */
class WatchRequestService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WEAR_REFRESH_PATH) return
        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setInputData(workDataOf(WeatherWorker.FORCE to true, WeatherWorker.FOR_WATCH to true))
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork("reloj-pide", ExistingWorkPolicy.KEEP, request)
    }
}
