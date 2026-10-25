package com.mnavarrosqa.clima.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat

/**
 * Pedir la ubicación. Si la persona ya dijo que no dos veces (o marcó "No volver a preguntar"),
 * Android ya no muestra el diálogo y el botón no hacía nada: entonces se abren los ajustes de la
 * app, donde se puede dar el permiso a mano.
 */
object LocationAsk {
    private const val ASKED = "ubicacion_pedida"

    fun request(context: Context, launcher: ActivityResultLauncher<String>) {
        val prefs = context.getSharedPreferences("clima", Context.MODE_PRIVATE)
        val activity = context.findActivity()
        val blocked = prefs.getBoolean(ASKED, false) && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (blocked) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        prefs.edit().putBoolean(ASKED, true).apply()
        launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
