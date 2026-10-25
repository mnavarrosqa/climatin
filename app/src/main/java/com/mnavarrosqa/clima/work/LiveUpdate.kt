package com.mnavarrosqa.clima.work

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

/**
 * Live Updates de Android 16: notificaciones "en curso" que el sistema puede subir a un chip en
 * la barra de estado y a la parte de arriba de la pantalla de bloqueo (en Samsung, la Now Bar).
 * Para eso tienen que estar fijas, tener título, no usar diseño propio y pedirlo. El sistema (y
 * la persona, desde los ajustes de la app) decide si lo concede; si no, quedan como una
 * notificación común con la misma barra de avance.
 */
object LiveUpdate {
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
    private const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 36

    /** Pide el chip, con [chip] como texto corto ("12 min", "21°"). Antes de Android 16 no hace nada. */
    fun NotificationCompat.Builder.requestLive(chip: String?): NotificationCompat.Builder {
        if (!supported) return this
        return setOngoing(true).addExtras(
            Bundle().apply {
                putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                chip?.let { putString(EXTRA_SHORT_CRITICAL_TEXT, it) }
            },
        )
    }

    /** Le pone a la notificación una barra ProgressStyle (solo existe desde Android 16). */
    @RequiresApi(36)
    fun withProgress(context: Context, notification: Notification, style: Notification.ProgressStyle): Notification =
        Notification.Builder.recoverBuilder(context, notification).setStyle(style).build()

    /** Si la notificación sigue a la vista (para actualizarla en silencio en vez de avisar de nuevo). */
    fun isShowing(context: Context, id: Int): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == id }
    }.getOrDefault(false)

    /** Minutos redondeados para el chip: "12 min" o "2 h". */
    fun chipMinutes(minutes: Long): String = when {
        minutes < 60 -> "${minutes.coerceAtLeast(1)} min"
        else -> "${(minutes + 30) / 60} h"
    }

    /** Un tramo de la barra, en minutos. */
    @RequiresApi(36)
    fun segment(minutes: Long, color: Int): Notification.ProgressStyle.Segment =
        Notification.ProgressStyle.Segment(minutes.coerceAtLeast(1).toInt()).setColor(color)

    @RequiresApi(36)
    fun point(minute: Long, color: Int): Notification.ProgressStyle.Point =
        Notification.ProgressStyle.Point(minute.coerceAtLeast(1).toInt()).setColor(color)
}
