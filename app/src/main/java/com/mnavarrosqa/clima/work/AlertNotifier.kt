package com.mnavarrosqa.clima.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mnavarrosqa.clima.MainActivity
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.WeatherRepository

/** Notifica una vez cada alerta nueva del SMN para donde estás. */
object AlertNotifier {
    private const val CHANNEL_ID = "alertas"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Alertas del SMN", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Alertas meteorológicas oficiales para donde estás" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun check(context: Context) {
        val repository = WeatherRepository.get(context)
        if (!repository.smnAlertsEnabled || !RainNotifier.canNotify(context)) return
        val snapshot = repository.cached() ?: return
        val notified = repository.notifiedAlerts
        val fresh = snapshot.alerts.filter { it.id !in notified }
        if (fresh.isEmpty()) return

        val intent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fresh.forEach { alert ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_rain)
                .setContentTitle("Alerta: ${alert.event}")
                .setContentText(alert.headline ?: alert.description ?: "")
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.description ?: alert.headline ?: ""))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
        }
        // Se guardan solo las vigentes para que la lista no crezca sin fin.
        repository.notifiedAlerts = snapshot.alerts.map { it.id }.toSet()
    }
}
