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

class MainActivity : ComponentActivity() {

    /** Ciudad y día que pidió abrir un widget, hasta que la pantalla los usa. */
    private var deepLink by mutableStateOf<DeepLinkTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deepLink = parse(intent)
        addOnNewIntentListener { deepLink = parse(it) }
        setContent {
            ClimaTheme {
                WeatherRoute(deepLink = deepLink, onDeepLinkHandled = { deepLink = null })
            }
        }
    }

    private fun parse(intent: Intent?): DeepLinkTarget? {
        val place = intent?.getStringExtra("place") ?: return null
        return DeepLinkTarget(place, intent.getStringExtra("day"), radar = intent.getBooleanExtra("radar", false))
    }
}
