package com.mnavarrosqa.clima.work

import com.mnavarrosqa.clima.data.tr
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherAlert
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.work.Notify.muteAction
import com.mnavarrosqa.clima.work.LiveUpdate.requestLive
import android.app.Notification
import java.time.OffsetDateTime

/** Nivel de una alerta del SMN según su "severity" CAP. */
enum class AlertLevel(private val es: String, private val en: String, val color: Int) {
    YELLOW("amarilla", "yellow", 0xFFF2B705.toInt()),
    ORANGE("naranja", "orange", 0xFFF57C00.toInt()),
    RED("roja", "red", 0xFFD32F2F.toInt()),
    ;

    val label: String get() = tr(es, en)

    companion object {
        fun of(alert: WeatherAlert): AlertLevel = when {
            alert.severity.equals("Extreme", true) -> RED
            alert.severity.equals("Severe", true) -> ORANGE
            else -> YELLOW
        }
    }
}

/**
 * Alertas del SMN para donde estás: cada alerta nueva una vez, con su nivel y color, y un aviso
 * cuando termina. Las rojas suenan aunque sea horario de silencio o estén pausados los avisos.
 */
object AlertNotifier {
    private const val CHANNEL_ID = "alertas"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, tr("Alertas del SMN", "Official alerts (Argentina)"), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = tr("Alertas meteorológicas oficiales para donde estás", "Official weather alerts for where you are") }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** La app y el worker pueden revisar a la vez: así una alerta no suena dos veces. */
    @Synchronized
    fun check(context: Context) {
        val repository = WeatherRepository.get(context)
        if (!repository.smnAlertsEnabled || !RainNotifier.canNotify(context)) {
            // Al reactivarlas no tienen que aparecer "terminó" de alertas viejas.
            NotifyPrefs.setActiveAlerts(context, emptySet())
            return
        }
        val snapshot = repository.cached() ?: return
        val currentIds = snapshot.alerts.map { it.id }.toSet()
        val notified = repository.notifiedAlerts
        // Solo cuenta como avisada la que se mostró: si cae en horario de silencio o en pausa,
        // se avisa cuando se pueda (mientras siga vigente).
        val shown = snapshot.alerts.filter { it.id !in notified }.filter { alert ->
            Notify.allowed(context, urgent = AlertLevel.of(alert) == AlertLevel.RED).also { if (it) show(context, alert) }
        }.map { it.id }
        // Se guardan solo las vigentes para que la lista no crezca sin fin.
        repository.notifiedAlerts = (notified intersect currentIds) + shown
        // Las que ya se avisaron y siguen en pantalla como Live Update: se actualiza la barra.
        snapshot.alerts.filter { it.id in notified && isLive(context, it) && LiveUpdate.isShowing(context, it.id.hashCode()) }
            .forEach { show(context, it, update = true) }

        // Las que estaban y ya no están, terminaron. Si ahora no se puede avisar, quedan pendientes.
        val active = snapshot.alerts.map { it.id + "|" + it.event }.toSet()
        val ended = NotifyPrefs.activeAlerts(context) - active
        if (ended.isEmpty() || Notify.allowed(context)) {
            ended.forEach { entry ->
                val (id, event) = entry.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                showEnded(context, id, event)
            }
            NotifyPrefs.setActiveAlerts(context, active)
        } else {
            NotifyPrefs.setActiveAlerts(context, active + ended)
        }
    }

    fun test(context: Context) {
        show(
            context,
            WeatherAlert(
                id = "prueba",
                event = tr("Tormentas", "Thunderstorms"),
                severity = "Severe",
                headline = tr("Tormentas fuertes con ráfagas y caída de granizo", "Strong storms with gusts and hail"),
                description = tr(
                    "Esto es una alerta de prueba: así se ven las alertas del SMN. Tocala para abrir el detalle en la app.",
                    "This is a test alert: this is how official alerts look. Tap it to open the details in the app.",
                ),
                onset = java.time.OffsetDateTime.now().minusHours(1).toString(),
                expires = java.time.OffsetDateTime.now().plusHours(3).toString(),
            ),
        )
    }

    private fun millis(iso: String?): Long? =
        iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    /** Naranjas y rojas con fin conocido van en vivo (Android 16) si está prendido en Ajustes. */
    private fun isLive(context: Context, alert: WeatherAlert): Boolean =
        LiveUpdate.supported && NotifyPrefs.get(context).liveAlerts && AlertLevel.of(alert) != AlertLevel.YELLOW &&
            (millis(alert.expires) ?: 0L) > System.currentTimeMillis()

    @SuppressLint("MissingPermission")
    private fun show(context: Context, alert: WeatherAlert, update: Boolean = false) {
        val level = AlertLevel.of(alert)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setColor(level.color)
            .setContentTitle(tr("Alerta ${level.label}", "${level.label.replaceFirstChar { it.uppercase() }} alert") + ": ${alert.event}")
            .setContentText(alert.headline ?: alert.description ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(alert.headline, alert.description).joinToString("\n\n")))
            .setContentIntent(Notify.open(context, alert.id.hashCode(), CURRENT_PLACE_ID, alert = alert.id))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (level == AlertLevel.RED) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setSilent(update)
            .muteAction(context, alert.id.hashCode())
        val now = System.currentTimeMillis()
        val expires = millis(alert.expires)
        var notification = if (isLive(context, alert) && expires != null) {
            // En vivo: cuánto falta para que termine, en el chip y en la cuenta regresiva.
            builder.setWhen(expires)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setTimeoutAfter(expires - now + 10 * 60_000L)
                .requestLive("⚠ " + alert.event.take(14))
                .build()
        } else {
            builder.build()
        }
        if (isLive(context, alert) && expires != null && Build.VERSION.SDK_INT >= 36) {
            val onset = (millis(alert.onset) ?: now).coerceAtMost(now)
            val total = (expires - onset) / 60_000
            val style = Notification.ProgressStyle()
                .setStyledByProgress(false)
                .addProgressSegment(LiveUpdate.segment(total, level.color))
                .setProgress(((now - onset) / 60_000).toInt().coerceIn(0, total.toInt().coerceAtLeast(1)))
            notification = LiveUpdate.withProgress(context, notification, style)
        }
        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    private fun showEnded(context: Context, id: String, event: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setColor(0xFF2E9E5B.toInt())
            .setContentTitle(tr("Terminó la alerta", "Alert ended") + if (event.isNotBlank()) ": $event" else "")
            .setContentText(tr("El SMN ya no la tiene vigente para donde estás.", "It's no longer active where you are."))
            .setContentIntent(Notify.open(context, 20, CURRENT_PLACE_ID))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // Reemplaza la notificación de la alerta, si sigue ahí.
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }
}
