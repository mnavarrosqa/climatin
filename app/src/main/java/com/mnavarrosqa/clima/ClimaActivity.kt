package com.mnavarrosqa.clima

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        val splash = installSplashScreen()
        // Se queda hasta que termine de dibujarse el ícono (850 ms), así la animación se ve entera;
        // mientras, los pronósticos guardados se terminan de leer en segundo plano.
        val shownAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { savedInstanceState == null && SystemClock.uptimeMillis() - shownAt < 850 }
        // Al salir, el ícono se agranda y todo se desvanece hacia el pronóstico.
        splash.setOnExitAnimationListener { provider ->
            val icon = provider.iconView
            val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            val grow = ObjectAnimator.ofPropertyValuesHolder(
                icon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f),
            )
            AnimatorSet().apply {
                playTogether(fade, grow)
                duration = 320
                interpolator = AccelerateInterpolator()
                doOnEnd { provider.remove() }
                start()
            }
        }
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
