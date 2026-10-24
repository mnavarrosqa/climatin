package com.mnavarrosqa.clima.work

import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mnavarrosqa.clima.MainActivity
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.rainSoonText

/** Notificación "va a llover" para donde estás. Avisa una vez cada 3 horas como mucho. */
object RainNotifier {
    private const val CHANNEL_ID = "lluvia"
    private const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, tr("Avisos de lluvia", "Rain alerts"), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = tr("Te avisa cuando va a llover en las próximas horas", "Lets you know when rain is coming in the next hours") }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun check(context: Context) {
        val repository = WeatherRepository.get(context)
        if (!repository.rainAlertsEnabled || !canNotify(context)) return
        val snapshot = repository.cached() ?: return
        val minutes = snapshot.minutesUntilRain() ?: return
        val now = System.currentTimeMillis()
        if (now < repository.rainNotifiedUntil) return

        val intent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val where = snapshot.place?.let { " " + tr("en", "in") + " $it" } ?: ""
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rain)
            .setContentTitle(rainSoonText(minutes))
            .setContentText(tr("Llevá paraguas", "Bring an umbrella") + "$where.")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        repository.rainNotifiedUntil = now + 3 * 60 * 60_000L
    }
}
