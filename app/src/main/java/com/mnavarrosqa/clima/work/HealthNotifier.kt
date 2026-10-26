package com.mnavarrosqa.clima.work

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.mainPollutant
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.data.trend
import com.mnavarrosqa.clima.data.uvWindow
import com.mnavarrosqa.clima.work.Notify.muteAction
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * UV muy alto y aire contaminado para donde estás, cada uno una vez por día. Se revisa después de
 * cada actualización: el UV a la mañana, el aire mientras haya gente afuera.
 */
object HealthNotifier {
    /** Mismo aviso del día que el UV de "Cambios bruscos": si ya salió uno, no sale el otro. */
    private const val KEY_UV = "uv"
    private const val KEY_AIR = "aire"

    /** Índice europeo desde el que el aire es "malo". */
    private const val BAD_AIR = 60

    /**
     * Con datos más viejos que esto no se avisa: sin red el trabajo corre igual con lo guardado, y
     * un "aire malo hoy" o un UV de hace horas confunde más de lo que ayuda.
     */
    private const val MAX_AGE_MS = 2 * 60 * 60_000L

    fun check(context: Context) {
        val settings = NotifyPrefs.get(context)
        if (!settings.uvAlert && !settings.airAlert) return
        if (!Notify.allowed(context)) return
        val snapshot = WeatherRepository.get(context).cached() ?: return
        // Copia local: es del núcleo.
        val fetchedAt = snapshot.fetchedAt
        if (System.currentTimeMillis() - fetchedAt > MAX_AGE_MS) return
        val now = snapshot.nowAtPlace()
        val thisHour = now.truncatedTo(ChronoUnit.HOURS)
        // Horas enteras desde la descarga, en la hora del lugar: lo por hora arranca en esa hora.
        val fetchedHour = Instant.ofEpochMilli(fetchedAt).atZone(snapshot.zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
        val hoursSince = ChronoUnit.HOURS.between(fetchedHour, thisHour).toInt().coerceAtLeast(0)

        // UV: desde la primera actualización después de las 7 hasta el mediodía; más tarde ya no sirve.
        if (settings.uvAlert && now.hour in 7..12 && !NotifyPrefs.alreadyToday(context, KEY_UV)) {
            val window = snapshot.uvWindow(threshold = 8.0)
            if (window != null) {
                val max = snapshot.hoursOf(now.toLocalDate()).maxOfOrNull { it.uvIndex ?: 0.0 } ?: 8.0
                show(
                    context, Notify.ID_UV, 0xFFE8833A.toInt(),
                    title = tr("UV muy alto de ${shortHour(window.from)} a ${shortHour(window.until)}", "Very high UV from ${shortHour(window.from)} to ${shortHour(window.until)}"),
                    text = tr("Hasta ${max.roundToInt()}: protector, anteojos y sombra", "Up to ${max.roundToInt()}: sunscreen, sunglasses and shade"),
                )
                NotifyPrefs.markToday(context, KEY_UV)
            }
        }

        // Aire: malo ahora, o empeorando hasta malo en las próximas horas. Copia local: es del núcleo.
        val downloaded = snapshot.airQuality
        if (settings.airAlert && downloaded != null && now.hour in 7..21 && !NotifyPrefs.alreadyToday(context, KEY_AIR)) {
            // upcomingAqi[0] es la hora de la descarga: se corre a la actual, así [0] es ahora y
            // cada siguiente una hora más. Pasada una hora, "ahora" sale de esa lista.
            val air = downloaded.copy(upcomingAqi = downloaded.upcomingAqi.drop(hoursSince))
            val current = if (hoursSince == 0) air.europeanAqi else air.upcomingAqi.firstOrNull() ?: air.europeanAqi
            val pollutant = air.mainPollutant()?.let { " ($it)" } ?: ""
            val worseAt = if (air.trend() == 1) air.upcomingAqi.indexOfFirst { (it ?: 0) >= BAD_AIR }.takeIf { it > 0 } else null
            when {
                current != null && current >= BAD_AIR -> {
                    show(
                        context, Notify.ID_AIR, 0xFF8E6E53.toInt(),
                        title = tr("Calidad del aire ${WeatherCodes.airQualityLabel(current).lowercase()} hoy$pollutant", "Air quality ${WeatherCodes.airQualityLabel(current).lowercase()} today$pollutant"),
                        text = tr("Índice $current. Mejor evitar el ejercicio al aire libre", "Index $current. Better to avoid exercising outdoors"),
                    )
                    NotifyPrefs.markToday(context, KEY_AIR)
                }
                worseAt != null -> {
                    val at = thisHour.plusHours(worseAt.toLong())
                    show(
                        context, Notify.ID_AIR, 0xFF8E6E53.toInt(),
                        title = tr("La calidad del aire empeora hoy$pollutant", "Air quality is getting worse today$pollutant"),
                        text = tr("Mala desde las ${shortHour(at)}. Si vas a hacer ejercicio afuera, mejor antes", "Poor from ${shortHour(at)}. If you exercise outdoors, go earlier"),
                    )
                    NotifyPrefs.markToday(context, KEY_AIR)
                }
            }
        }
    }

    /** Ejemplos para Ajustes → Probar. */
    fun testUv(context: Context) = show(
        context, Notify.ID_UV, 0xFFE8833A.toInt(),
        title = tr("UV muy alto de 11:00 a 16:00", "Very high UV from 11 AM to 4 PM"),
        text = tr("Esto es un aviso de prueba de UV.", "This is a test of UV alerts."),
    )

    fun testAir(context: Context) = show(
        context, Notify.ID_AIR, 0xFF8E6E53.toInt(),
        title = tr("Calidad del aire mala hoy (PM2,5)", "Poor air quality today (PM2.5)"),
        text = tr("Esto es un aviso de prueba de calidad del aire.", "This is a test of air quality alerts."),
    )

    @SuppressLint("MissingPermission")
    private fun show(context: Context, id: Int, color: Int, title: String, text: String) {
        if (!RainNotifier.canNotify(context)) return
        val notification = NotificationCompat.Builder(context, Notify.CHANNEL_HEALTH)
            .setSmallIcon(R.drawable.ic_notification_weather)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notify.open(context, 60 + id, CURRENT_PLACE_ID))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .muteAction(context, id)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
