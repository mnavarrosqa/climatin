package com.mnavarrosqa.clima

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Guarda el último error en un archivo del teléfono (nada sale de ahí solo). La próxima vez que se
 * abre la app, ofrece compartirlo para poder arreglarlo: sin esto, un cierre no deja pistas.
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "ultimo_error.txt")

    /** Cierres de la app: se anota y después sigue el manejo normal de Android. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(app, error, "cierre en ${thread.name}")
            previous?.uncaughtException(thread, error)
        }
    }

    @Volatile private var caughtAt = 0L

    /**
     * Anota un error que se atajó (como un cuadro del fondo de pantalla). A lo sumo uno cada 10
     * minutos, porque un error que se repite en cada cuadro escribiría el archivo 60 veces por
     * segundo, y nunca encima de un cierre de verdad, que es el que más importa ver.
     */
    fun recordCaught(context: Context, error: Throwable, where: String) {
        val now = System.currentTimeMillis()
        if (now - caughtAt < 10 * 60_000L) return
        caughtAt = now
        if (last(context)?.contains("· cierre en ") == true) return
        record(context, error, where)
    }

    /** Anota un error: lo último que pasó queda en el archivo. */
    fun record(context: Context, error: Throwable, where: String) {
        runCatching {
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            val (name, code) = runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                info.versionName to androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)
            }.getOrDefault(null to 0L)
            file(context).writeText(
                "Climatin $name ($code) · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                    "${Build.MANUFACTURER} ${Build.MODEL}\n${LocalDateTime.now()} · $where\n\n" + trace.take(12_000),
            )
        }
    }

    /** El último error guardado, si hay. */
    fun last(context: Context): String? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        file(context).delete()
    }
}
