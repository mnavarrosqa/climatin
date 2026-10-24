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

    fun check(context: Context) {
        val repository = WeatherRepository.get(context)
        if (!repository.smnAlertsEnabled || !RainNotifier.canNotify(context)) return
        val snapshot = repository.cached() ?: return
        val notified = repository.notifiedAlerts
        snapshot.alerts.filter { it.id !in notified }.forEach { alert ->
            if (Notify.allowed(context, urgent = AlertLevel.of(alert) == AlertLevel.RED)) show(context, alert)
        }
        // Las que estaban y ya no están, terminaron.
        val active = snapshot.alerts.map { it.id + "|" + it.event }.toSet()
        val ended = NotifyPrefs.activeAlerts(context) - active
        if (Notify.allowed(context)) {
            ended.forEach { entry ->
                val (id, event) = entry.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                showEnded(context, id, event)
            }
        }
        NotifyPrefs.setActiveAlerts(context, active)
        // Se guardan solo las vigentes para que la lista no crezca sin fin.
        repository.notifiedAlerts = snapshot.alerts.map { it.id }.toSet()
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
                onset = null,
                expires = null,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, alert: WeatherAlert) {
        val level = AlertLevel.of(alert)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setColor(level.color)
            .setContentTitle(tr("Alerta ${level.label}", "${level.label.replaceFirstChar { it.uppercase() }} alert") + ": ${alert.event}")
            .setContentText(alert.headline ?: alert.description ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(alert.headline, alert.description).joinToString("\n\n")))
            .setContentIntent(Notify.open(context, alert.id.hashCode(), CURRENT_PLACE_ID, alert = alert.id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (level == AlertLevel.RED) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .muteAction(context, alert.id.hashCode())
            .build()
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
