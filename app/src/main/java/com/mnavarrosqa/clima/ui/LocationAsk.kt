package com.mnavarrosqa.clima.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.mnavarrosqa.clima.data.tr

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

/**
 * Aviso previo al permiso de ubicación en segundo plano. Google Play lo exige antes del diálogo
 * del sistema: qué se recoge, para qué y qué se hace con eso. El permiso solo se pide desde
 * [onContinue]; cerrar el aviso o tocar "Ahora no" no pide nada.
 */
@Composable
fun BackgroundLocationDisclosure(onDismiss: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
        title = { Text(tr("Ubicación con la app cerrada", "Location while the app is closed")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    tr(
                        "Climatin recoge tu ubicación aproximada en segundo plano, incluso cuando la app está cerrada o no la estás usando.",
                        "Climatin collects your approximate location in the background, even when the app is closed or not in use.",
                    ),
                )
                Text(
                    tr(
                        "Sirve para que los widgets, los avisos y el fondo de pantalla sigan el lugar donde estás sin tener que abrir la app.",
                        "This lets widgets, alerts and the wallpaper follow where you are without opening the app.",
                    ),
                )
                Text(
                    tr(
                        "Solo se usa para pedirle a Open-Meteo el pronóstico de ese lugar. No se guarda en servidores de Climatin ni se usa para publicidad.",
                        "It's only used to ask Open-Meteo for the forecast of that place. It isn't stored on Climatin servers or used for advertising.",
                    ),
                )
                Text(
                    tr(
                        "Si seguís, en el paso que viene elegí \"Permitir todo el tiempo\".",
                        "If you continue, choose \"Allow all the time\" in the next step.",
                    ),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Ahora no", "Not now")) } },
        confirmButton = { Button(onClick = onContinue) { Text(tr("Continuar", "Continue")) } },
    )
}
