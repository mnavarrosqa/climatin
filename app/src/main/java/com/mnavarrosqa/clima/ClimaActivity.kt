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
        // Se queda hasta que esté leído el pronóstico guardado (en otro hilo), con un mínimo para
        // que se vea el ícono y nunca más de 850 ms. Desde un widget, una notificación o un acceso
        // directo no hay ícono que mostrar: solo se espera el pronóstico, poco.
        val shownAt = SystemClock.uptimeMillis()
        val fromLauncher = intent?.action == Intent.ACTION_MAIN && intent?.hasCategory(Intent.CATEGORY_LAUNCHER) == true
        // Sin mínimo: si el pronóstico ya está, se entra enseguida.
        val minimum = 0L
        val maximum = if (fromLauncher) 850L else 300L
        val ready = java.util.concurrent.atomic.AtomicBoolean(savedInstanceState != null)
        if (!ready.get()) {
            Thread {
                runCatching { com.mnavarrosqa.clima.data.WeatherRepository.get(applicationContext).cached() }
                ready.set(true)
            }.start()
        }
        splash.setKeepOnScreenCondition {
            val elapsed = SystemClock.uptimeMillis() - shownAt
            savedInstanceState == null && elapsed < maximum && (!ready.get() || elapsed < minimum)
        }
        // Al salir, el ícono se agranda y todo se desvanece hacia el pronóstico.
        splash.setOnExitAnimationListener { provider ->
            // Abierta desde un widget o una notificación, Android 13+ muestra la pantalla de inicio
            // sin ícono: pedirlo cerraba la app. Sin ícono, solo se desvanece.
            val icon = runCatching { provider.iconView }.getOrNull()
            runCatching {
                val fade = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
                val grow = icon?.let {
                    ObjectAnimator.ofPropertyValuesHolder(
                        it,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.35f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.35f),
                    )
                }
                AnimatorSet().apply {
                    if (grow != null) playTogether(fade, grow) else play(fade)
                    duration = 320
                    interpolator = AccelerateInterpolator()
                    doOnEnd { runCatching { provider.remove() } }
                    start()
                }
            }.onFailure { runCatching { provider.remove() } }
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
        // Desde el acceso directo de una ciudad: el launcher aprende cuáles se usan más.
        if (intent.action == Intent.ACTION_VIEW) {
            runCatching { androidx.core.content.pm.ShortcutManagerCompat.reportShortcutUsed(this, "ciudad:$place") }
        }
        return DeepLinkTarget(
            place,
            intent.getStringExtra("day"),
            radar = intent.getBooleanExtra("radar", false),
            alert = intent.getStringExtra("alert"),
        )
    }
}
