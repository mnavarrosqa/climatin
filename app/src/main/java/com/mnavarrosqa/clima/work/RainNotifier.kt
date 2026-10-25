package com.mnavarrosqa.clima.work

import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import com.mnavarrosqa.clima.work.LiveUpdate.requestLive
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
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
        if (!repository.rainAlertsEnabled || !canNotify(context)) return
        val snapshot = repository.cached() ?: return
        val settings = NotifyPrefs.get(context)
        val outlook = snapshot.rainOutlook(withinMinutes = settings.rainLead)
        // Si el aviso sigue a la vista, se actualiza en silencio (cuenta, barra y "para en…"),
        // aunque sea horario de silencio: no suena, solo cambia lo que dice.
        val live = LiveUpdate.isShowing(context, Notify.ID_RAIN)
        val raining = snapshot.current.precipitation >= 0.1 || WeatherCodes.kind(snapshot.current.code).let {
            it == WeatherKind.RAIN || it == WeatherKind.DRIZZLE || it == WeatherKind.STORM
        }
        val now = System.currentTimeMillis()
        when {
            outlook == null -> when {
                // Llueve y no se sabe hasta cuándo: queda "Está lloviendo".
                live && raining -> show(context, null, snapshot.place, update = true)
                // El pronóstico cambió y la lluvia avisada ya no viene: se saca.
                live -> NotificationManagerCompat.from(context).cancel(Notify.ID_RAIN)
            }
            live -> show(context, outlook, snapshot.place, update = true)
            !Notify.allowed(context) -> Unit
            outlook is RainOutlook.Starts -> if (now >= NotifyPrefs.until(context, "lluvia")) {
                show(context, outlook, snapshot.place, update = false)
                // No volver a avisar de esta misma lluvia: hasta que termine (o 2 h y media si no se sabe).
                val quietFor = (outlook.inMinutes + (outlook.lastsMinutes ?: 150)).toLong().coerceAtLeast(90)
                NotifyPrefs.setUntil(context, "lluvia", LocalDateTime.now().plusMinutes(quietFor))
            }
            outlook is RainOutlook.Stops -> if (settings.rainStops && now >= NotifyPrefs.until(context, "lluvia_para")) {
                show(context, outlook, snapshot.place, update = false)
                NotifyPrefs.setUntil(context, "lluvia_para", LocalDateTime.now().plusMinutes(180))
            }
        }
    }

    /** Notificación de ejemplo para Ajustes → Notificaciones → Probar. */
    fun test(context: Context) {
        val place = WeatherRepository.get(context).cached()?.place
        show(context, RainOutlook.Starts(20, 90, RainIntensity.MODERATE), place, update = false)
    }

    /**
     * [outlook] null es "está lloviendo" sin final conocido. Con [update] la notificación ya estaba:
     * se conserva el comienzo de la barra para que el punto de "ahora" avance.
     */
    @SuppressLint("MissingPermission")
    private fun show(context: Context, outlook: RainOutlook?, place: String?, update: Boolean) {
        if (!canNotify(context)) return
        val now = System.currentTimeMillis()
        // Comienzo de la barra y de la lluvia, guardados para las actualizaciones.
        if (!update) {
            NotifyPrefs.setTime(context, "lluvia_origen", now)
            NotifyPrefs.setTime(context, "lluvia_inicio", if (outlook is RainOutlook.Starts) now + outlook.inMinutes * 60_000L else now)
        }
        if (outlook is RainOutlook.Starts) {
            NotifyPrefs.setTime(context, "lluvia_inicio", now + outlook.inMinutes * 60_000L)
        }
        val where = place?.let { tr("en $it", "in $it") }
        val title = outlook?.let(::rainOutlookText) ?: tr("Está lloviendo", "It's raining")
        val detail = when (outlook) {
            is RainOutlook.Starts -> listOfNotNull(rainDurationText(outlook), where).joinToString(" · ") +
                if (outlook.intensity == RainIntensity.LIGHT) "" else ". " + tr("Llevá paraguas.", "Bring an umbrella.")
            is RainOutlook.Stops -> listOfNotNull(tr("Se viene un rato seco", "A dry spell is coming"), where).joinToString(" · ")
            null -> listOfNotNull(tr("Sigue un buen rato", "It'll keep going for a while"), where).joinToString(" · ")
        }
        val live = NotifyPrefs.get(context).liveRain
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rain)
            .setColor(0xFF3F7BE8.toInt())
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(Notify.open(context, 10, CURRENT_PLACE_ID))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(update)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .radarAction(context, CURRENT_PLACE_ID, 11)
            .muteAction(context, Notify.ID_RAIN)
        // Cuenta regresiva (la actualiza el sistema) hasta que empieza o hasta que para.
        val countdownTo = when (outlook) {
            is RainOutlook.Starts -> now + outlook.inMinutes * 60_000L
            is RainOutlook.Stops -> now + outlook.inMinutes * 60_000L
            null -> null
        }
        if (countdownTo != null) {
            builder.setWhen(countdownTo)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        // Se va sola un rato después de que termina la lluvia (o empieza, si no se sabe cuánto dura):
        // después la cuenta quedaría en negativo. Mientras sigue, cada revisión la renueva.
        val timeoutMinutes = when (outlook) {
            is RainOutlook.Starts -> outlook.inMinutes + (outlook.lastsMinutes ?: 0) + 10
            is RainOutlook.Stops -> outlook.inMinutes + 10
            null -> 40
        }
        builder.setTimeoutAfter(timeoutMinutes * 60_000L)
        if (live) {
            val minutes = countdownTo?.let { (it - now + 30_000) / 60_000 }
            builder.requestLive(
                when (outlook) {
                    is RainOutlook.Starts -> minutes?.let(LiveUpdate::chipMinutes)
                    is RainOutlook.Stops -> minutes?.let { tr("Para ", "Stops ") + LiveUpdate.chipMinutes(it) }
                    null -> tr("Lluvia", "Rain")
                },
            )
        }
        var notification = builder.build()
        if (LiveUpdate.supported) {
            notification = withTimeline(context, notification, outlook, now)
        }
        NotificationManagerCompat.from(context).notify(Notify.ID_RAIN, notification)
    }

    /**
     * Android 16: barra con los tramos secos en gris y la lluvia en azul (más intenso cuanto más
     * fuerte llueve), un punto donde empieza y el avance desde que salió el aviso.
     */
    @RequiresApi(36)
    private fun withTimeline(context: Context, notification: Notification, outlook: RainOutlook?, now: Long): Notification {
        val origin = NotifyPrefs.time(context, "lluvia_origen").takeIf { it in 1..now } ?: now
        val rainStart = NotifyPrefs.time(context, "lluvia_inicio").coerceIn(origin, Long.MAX_VALUE)
        fun minutes(from: Long, to: Long) = ((to - from) / 60_000).coerceAtLeast(0)
        val rainEnd = when (outlook) {
            is RainOutlook.Starts -> rainStart + (outlook.lastsMinutes ?: 120) * 60_000L
            is RainOutlook.Stops -> now + outlook.inMinutes * 60_000L
            null -> now + 60 * 60_000L
        }
        val intensity = (outlook as? RainOutlook.Starts)?.intensity ?: RainIntensity.MODERATE
        val rainColor = when (intensity) {
            RainIntensity.LIGHT -> 0xFF7FB0F5.toInt()
            RainIntensity.MODERATE -> 0xFF3F7BE8.toInt()
            RainIntensity.HEAVY -> 0xFF1D4FB0.toInt()
        }
        val dry = 0xFFB0BCCB.toInt()
        val style = Notification.ProgressStyle().setStyledByProgress(false)
        val beforeRain = minutes(origin, rainStart)
        if (beforeRain > 0) style.addProgressSegment(LiveUpdate.segment(beforeRain, dry))
        style.addProgressSegment(LiveUpdate.segment(minutes(maxOf(origin, rainStart), rainEnd), rainColor))
            .addProgressSegment(LiveUpdate.segment(30, dry))
        if (beforeRain > 0) style.addProgressPoint(LiveUpdate.point(beforeRain, rainColor))
        style.setProgress(minutes(origin, now).toInt())
        if (outlook == null) style.setProgressIndeterminate(true)
        return LiveUpdate.withProgress(context, notification, style)
    }
}
