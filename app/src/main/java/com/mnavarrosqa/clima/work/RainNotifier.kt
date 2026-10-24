package com.mnavarrosqa.clima.work

import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.Notification
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.RainIntensity
import com.mnavarrosqa.clima.data.RainOutlook
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.rainDurationText
import com.mnavarrosqa.clima.data.rainOutlook
import com.mnavarrosqa.clima.data.rainOutlookText
import com.mnavarrosqa.clima.work.Notify.muteAction
import com.mnavarrosqa.clima.work.Notify.radarAction
import java.time.LocalDateTime

/**
 * Aviso de lluvia para donde estás: cuándo empieza (con la anticipación elegida), cuánto dura y
 * qué tan fuerte es, y cuándo para. La misma lluvia no se avisa dos veces.
 */
object RainNotifier {
    // Canal nuevo para estrenar la vibración de gotas (un canal no cambia su vibración después de creado).
    private const val CHANNEL_ID = "lluvia_gotas"
    private const val OLD_CHANNEL_ID = "lluvia"

    /** Tres gotas: golpecitos cortos que se acercan. */
    private val RAIN_VIBRATION = longArrayOf(0, 25, 110, 25, 90, 40)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNEL_ID)
        val channel = NotificationChannel(CHANNEL_ID, tr("Avisos de lluvia", "Rain alerts"), NotificationManager.IMPORTANCE_DEFAULT)
            .apply {
                description = tr("Te avisa cuando va a llover o cuando para", "Lets you know when rain is coming or stopping")
                enableVibration(true)
                vibrationPattern = RAIN_VIBRATION
            }
        manager.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun check(context: Context) {
        val repository = WeatherRepository.get(context)
        if (!repository.rainAlertsEnabled || !Notify.allowed(context)) return
        val snapshot = repository.cached() ?: return
        val settings = NotifyPrefs.get(context)
        val outlook = snapshot.rainOutlook(withinMinutes = settings.rainLead) ?: return
        val now = LocalDateTime.now()
        val key = if (outlook is RainOutlook.Stops) "lluvia_para" else "lluvia"
        if (outlook is RainOutlook.Stops && !settings.rainStops) return
        if (System.currentTimeMillis() < NotifyPrefs.until(context, key)) return
        show(context, outlook, snapshot.place)
        // No volver a avisar de esta misma lluvia: hasta que termine (o 3 horas si no se sabe).
        val quietFor = when (outlook) {
            is RainOutlook.Starts -> (outlook.inMinutes + (outlook.lastsMinutes ?: 150)).toLong().coerceAtLeast(90)
            is RainOutlook.Stops -> 180L
        }
        NotifyPrefs.setUntil(context, key, now.plusMinutes(quietFor))
    }

    /** Notificación de ejemplo para Ajustes → Notificaciones → Probar. */
    fun test(context: Context) {
        val place = WeatherRepository.get(context).cached()?.place
        show(context, RainOutlook.Starts(20, 90, RainIntensity.MODERATE), place)
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, outlook: RainOutlook, place: String?) {
        if (!canNotify(context)) return
        val where = place?.let { tr("en $it", "in $it") }
        val detail = when (outlook) {
            is RainOutlook.Starts -> listOfNotNull(rainDurationText(outlook), where).joinToString(" · ") +
                if (outlook.intensity == RainIntensity.LIGHT) "" else ". " + tr("Llevá paraguas.", "Bring an umbrella.")
            is RainOutlook.Stops -> listOfNotNull(tr("Se viene un rato seco", "A dry spell is coming"), where).joinToString(" · ")
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rain)
            .setColor(0xFF3F7BE8.toInt())
            .setContentTitle(rainOutlookText(outlook))
            .setContentText(detail)
            .setContentIntent(Notify.open(context, 10, CURRENT_PLACE_ID))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .radarAction(context, CURRENT_PLACE_ID, 11)
            .muteAction(context, Notify.ID_RAIN)
        if (outlook is RainOutlook.Starts) {
            // Cuenta regresiva hasta que empieza (el sistema la actualiza sola) y la notificación
            // se va cuando termina la lluvia.
            val startsAt = System.currentTimeMillis() + outlook.inMinutes * 60_000L
            builder.setWhen(startsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter((outlook.inMinutes + (outlook.lastsMinutes ?: 120)) * 60_000L)
                .setOnlyAlertOnce(true)
            // Android 16: pide mostrarse como Live Update (chip en la barra de estado). El sistema
            // decide si lo concede; si no, queda como notificación común.
            if (Build.VERSION.SDK_INT >= 36) {
                builder.setOngoing(true).addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            }
        }
        var notification = builder.build()
        if (outlook is RainOutlook.Starts && Build.VERSION.SDK_INT >= 36) {
            notification = withTimeline(context, notification, outlook)
        }
        NotificationManagerCompat.from(context).notify(Notify.ID_RAIN, notification)
    }

    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /**
     * Android 16: barra de las próximas horas con los tramos secos en gris y la lluvia en azul
     * (más intenso cuanto más fuerte llueve), y un punto donde empieza.
     */
    @RequiresApi(36)
    private fun withTimeline(context: Context, notification: Notification, outlook: RainOutlook.Starts): Notification {
        val lasts = outlook.lastsMinutes ?: 120
        val rainColor = when (outlook.intensity) {
            RainIntensity.LIGHT -> 0xFF7FB0F5.toInt()
            RainIntensity.MODERATE -> 0xFF3F7BE8.toInt()
            RainIntensity.HEAVY -> 0xFF1D4FB0.toInt()
        }
        val dry = 0xFFB0BCCB.toInt()
        val style = Notification.ProgressStyle()
            .setStyledByProgress(false)
            .addProgressSegment(Notification.ProgressStyle.Segment(outlook.inMinutes.coerceAtLeast(1)).setColor(dry))
            .addProgressSegment(Notification.ProgressStyle.Segment(lasts.coerceAtLeast(1)).setColor(rainColor))
            .addProgressSegment(Notification.ProgressStyle.Segment(30).setColor(dry))
            .addProgressPoint(Notification.ProgressStyle.Point(outlook.inMinutes.coerceAtLeast(1)).setColor(rainColor))
            .setProgress(0)
        return Notification.Builder.recoverBuilder(context, notification).setStyle(style).build()
    }
}
