package com.mnavarrosqa.clima.work

import android.content.Context
import com.mnavarrosqa.clima.widget.updateWeatherWidgets

/**
 * Lo que va después de bajar el clima, igual desde la app, el trabajo periódico o el ⟳ de un
 * widget: widgets, avisos y reloj. Cada aviso por separado: si uno falla, los demás igual salen.
 */
suspend fun afterRefresh(context: Context, urgentWear: Boolean = false) {
    runCatching { updateWeatherWidgets(context) }
    runCatching { RainNotifier.check(context) }
    runCatching { AlertNotifier.check(context) }
    runCatching { ChangeNotifier.check(context) }
    runCatching { TemperatureNotifier.update(context) }
    runCatching { SunNotifier.check(context) }
    runCatching { WearSync.send(context, urgent = urgentWear) }
    runCatching { scheduleMidnightRedraw(context) }
}

/**
 * Un repintado de los widgets apenas pasa la medianoche: la fecha y "Hoy" cambian aunque no
 * haya conexión ni toque actualizar.
 */
fun scheduleMidnightRedraw(context: Context) {
    val now = java.time.LocalDateTime.now()
    val next = now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(1)
    val delay = java.time.Duration.between(now, next).toMillis()
    val request = androidx.work.OneTimeWorkRequestBuilder<com.mnavarrosqa.clima.widget.WidgetRedrawWorker>()
        .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    androidx.work.WorkManager.getInstance(context)
        .enqueueUniqueWork("widgets-medianoche", androidx.work.ExistingWorkPolicy.REPLACE, request)
}

/** Cambio de hora o de zona horaria: los widgets se redibujan con la hora nueva. */
class TimeChangeReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        val app = context.applicationContext
        androidx.work.WorkManager.getInstance(app).enqueueUniqueWork(
            "widgets-hora",
            androidx.work.ExistingWorkPolicy.REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<com.mnavarrosqa.clima.widget.WidgetRedrawWorker>().build(),
        )
        runCatching { scheduleMidnightRedraw(app) }
    }
}
