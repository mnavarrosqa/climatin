package com.mnavarrosqa.clima

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mnavarrosqa.clima.ui.DeepLinkTarget
import com.mnavarrosqa.clima.ui.WeatherRoute
import com.mnavarrosqa.clima.ui.theme.ClimaTheme
import com.mnavarrosqa.clima.work.Outing

class ClimaActivity : ComponentActivity() {

    /** Ciudad y día que pidió abrir un widget, hasta que la pantalla los usa. */
    private var deepLink by mutableStateOf<DeepLinkTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Al volver desde Recientes, Android entrega otra vez el intent original: no se repite el acceso directo.
        val fromHistory = ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromHistory) {
            startOuting(intent)
            deepLink = parse(intent)
        }
        addOnNewIntentListener {
            startOuting(it)
            deepLink = parse(it)
        }
        setContent {
            ClimaTheme {
                WeatherRoute(deepLink = deepLink, onDeepLinkHandled = { deepLink = null })
            }
        }
    }

    /** Acceso directo "Salgo ahora" del ícono de la app. */
    private fun startOuting(intent: Intent?) {
        if (intent?.action == Outing.ACTION_START) Outing.start(this)
    }

    private fun parse(intent: Intent?): DeepLinkTarget? {
        val place = intent?.getStringExtra("place") ?: return null
        return DeepLinkTarget(
            place,
            intent.getStringExtra("day"),
            radar = intent.getBooleanExtra("radar", false),
            alert = intent.getStringExtra("alert"),
        )
    }
}
