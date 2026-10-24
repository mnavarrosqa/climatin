package com.mnavarrosqa.clima.work

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Ajustes de los avisos (los interruptores de lluvia, SMN y barra de estado siguen en el repositorio). */
data class NotifySettings(
    /** Cuántos minutos antes avisar que empieza a llover. */
    val rainLead: Int = 30,
    /** Avisar también cuando para de llover. */
    val rainStops: Boolean = true,
    val summary: Boolean = false,
    /** Hora del resumen diario (0–23). Desde las 17 resume el día siguiente. */
    val summaryHour: Int = 7,
    val changes: Boolean = false,
    val changeTemperature: Boolean = true,
    val changeFrost: Boolean = true,
    val changeWind: Boolean = true,
    val changeUv: Boolean = true,
    val quiet: Boolean = true,
    val quietStart: Int = 23,
    val quietEnd: Int = 7,
    /** Hasta cuándo están pausados los avisos ("No avisar más hoy"), en milisegundos. */
    val mutedUntil: Long = 0L,
)

/**
 * Guardado en SharedPreferences y expuesto como estado de Compose, así Ajustes se actualiza solo
 * cuando un botón de una notificación (por ejemplo "No avisar más hoy") cambia algo.
 */
object NotifyPrefs {
    private const val FILE = "avisos"

    var settings by mutableStateOf(NotifySettings())
        private set
    private var loaded = false

    fun get(context: Context): NotifySettings {
        if (!loaded) load(context)
        return settings
    }

    @Synchronized
    private fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = NotifySettings()
        settings = NotifySettings(
            rainLead = p.getInt("lluvia_anticipacion", d.rainLead),
            rainStops = p.getBoolean("lluvia_para", d.rainStops),
            summary = p.getBoolean("resumen", d.summary),
            summaryHour = p.getInt("resumen_hora", d.summaryHour),
            changes = p.getBoolean("cambios", d.changes),
            changeTemperature = p.getBoolean("cambios_temperatura", d.changeTemperature),
            changeFrost = p.getBoolean("cambios_helada", d.changeFrost),
            changeWind = p.getBoolean("cambios_viento", d.changeWind),
            changeUv = p.getBoolean("cambios_uv", d.changeUv),
            quiet = p.getBoolean("silencio", d.quiet),
            quietStart = p.getInt("silencio_desde", d.quietStart),
            quietEnd = p.getInt("silencio_hasta", d.quietEnd),
            mutedUntil = p.getLong("pausa_hasta", d.mutedUntil),
        )
        loaded = true
    }

    fun update(context: Context, change: (NotifySettings) -> NotifySettings) {
        val value = change(get(context))
        settings = value
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("lluvia_anticipacion", value.rainLead)
            .putBoolean("lluvia_para", value.rainStops)
            .putBoolean("resumen", value.summary)
            .putInt("resumen_hora", value.summaryHour)
            .putBoolean("cambios", value.changes)
            .putBoolean("cambios_temperatura", value.changeTemperature)
            .putBoolean("cambios_helada", value.changeFrost)
            .putBoolean("cambios_viento", value.changeWind)
            .putBoolean("cambios_uv", value.changeUv)
            .putBoolean("silencio", value.quiet)
            .putInt("silencio_desde", value.quietStart)
            .putInt("silencio_hasta", value.quietEnd)
            .putLong("pausa_hasta", value.mutedUntil)
            .apply()
    }

    /** Pausa los avisos hasta la medianoche de hoy. */
    fun muteToday(context: Context) {
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        update(context) { it.copy(mutedUntil = midnight) }
    }

    fun isMuted(context: Context) = System.currentTimeMillis() < get(context).mutedUntil

    /** Si ahora es horario de silencio (con la hora del teléfono). */
    fun inQuietHours(context: Context, now: LocalTime = LocalTime.now()): Boolean {
        val s = get(context)
        if (!s.quiet || s.quietStart == s.quietEnd) return false
        val hour = now.hour
        return if (s.quietStart < s.quietEnd) hour in s.quietStart until s.quietEnd else hour >= s.quietStart || hour < s.quietEnd
    }

    // --- Avisos que salen una sola vez por día ---

    fun alreadyToday(context: Context, key: String): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("hecho_$key", null) == LocalDate.now().toString()

    fun markToday(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("hecho_$key", LocalDate.now().toString()).apply()
    }

    /** Hasta cuándo no repetir un aviso (lluvia que empieza o que para). */
    fun until(context: Context, key: String): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong("hasta_$key", 0L)

    fun setUntil(context: Context, key: String, time: LocalDateTime) {
        val millis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong("hasta_$key", millis).apply()
    }

    /** Alertas del SMN vigentes la última vez, "id|evento", para avisar cuando terminan. */
    fun activeAlerts(context: Context): Set<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet("alertas_activas", emptySet()).orEmpty()

    fun setActiveAlerts(context: Context, value: Set<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet("alertas_activas", value).apply()
    }
}
