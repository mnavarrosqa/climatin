package com.mnavarrosqa.clima.work

import com.mnavarrosqa.clima.data.tr
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mnavarrosqa.clima.MainActivity
import com.mnavarrosqa.clima.R

/** Lo que comparten todos los avisos: canales, horario de silencio, pausa y botones. */
object Notify {
    const val CHANNEL_SUMMARY = "resumen"
    const val CHANNEL_CHANGES = "cambios"

    const val ID_RAIN = 1
    const val ID_STATUS = 3
    const val ID_SUMMARY = 4
    const val ID_CHANGES_BASE = 10

    private const val ACTION_MUTE = "com.mnavarrosqa.clima.NO_AVISAR_HOY"
    private const val ACTION_REFRESH = "com.mnavarrosqa.clima.ACTUALIZAR"
    private const val EXTRA_ID = "id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, tr("Resumen diario", "Daily summary"), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = tr("El pronóstico del día a la hora que elijas", "The day's forecast at the time you choose") },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHANGES, tr("Cambios bruscos", "Big changes"), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = tr("Bajón o subida de temperatura, heladas, viento fuerte y UV muy alto", "Big temperature swings, frost, strong wind and very high UV") },
        )
    }

    /**
     * Si se puede avisar ahora: hay permiso, no está pausado y no es horario de silencio.
     * Lo [urgent] (alertas rojas) pasa igual.
     */
    fun allowed(context: Context, urgent: Boolean = false): Boolean {
        if (!RainNotifier.canNotify(context)) return false
        if (urgent) return true
        return !NotifyPrefs.isMuted(context) && !NotifyPrefs.inQuietHours(context)
    }

    /** Abre la app en una ciudad; opcionalmente en el mapa de lluvia o en una alerta. */
    fun open(context: Context, requestCode: Int, placeId: String? = null, radar: Boolean = false, alert: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        placeId?.let { intent.putExtra("place", it) }
        if (radar) intent.putExtra("radar", true)
        alert?.let { intent.putExtra("alert", it) }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun broadcast(context: Context, action: String, notificationId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode() + notificationId,
            Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun NotificationCompat.Builder.radarAction(context: Context, placeId: String, requestCode: Int) =
        addAction(R.drawable.ic_notification_rain, tr("Ver mapa de lluvia", "Rain map"), open(context, requestCode, placeId, radar = true))

    fun NotificationCompat.Builder.muteAction(context: Context, notificationId: Int) =
        addAction(0, tr("No avisar más hoy", "Mute for today"), broadcast(context, ACTION_MUTE, notificationId))

    fun NotificationCompat.Builder.refreshAction(context: Context, notificationId: Int) =
        addAction(0, tr("Actualizar", "Refresh"), broadcast(context, ACTION_REFRESH, notificationId))

    /** Pide una actualización ya, sin esperar a la periódica. */
    fun refreshNow(context: Context) {
        // Sin "expedited": en Android 11 o anterior exigiría un servicio en primer plano.
        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setInputData(workDataOf(WeatherWorker.FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("clima-ahora", ExistingWorkPolicy.REPLACE, request)
    }

    internal fun handle(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        when (intent.action) {
            ACTION_MUTE -> {
                NotifyPrefs.muteToday(context)
                NotificationManagerCompat.from(context).cancel(id)
            }
            ACTION_REFRESH -> refreshNow(context)
        }
    }
}

/** Recibe los botones de las notificaciones ("No avisar más hoy", "Actualizar"). */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Notify.handle(context, intent)
}
