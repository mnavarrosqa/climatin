package com.mnavarrosqa.clima

import android.app.Application
import android.content.res.Configuration
import com.mnavarrosqa.clima.widget.updateWeatherWidgets
import androidx.work.ExistingWorkPolicy
import com.mnavarrosqa.clima.work.AlertNotifier
import com.mnavarrosqa.clima.work.Notify
import com.mnavarrosqa.clima.work.SummaryNotifier
import com.mnavarrosqa.clima.work.RainNotifier
import com.mnavarrosqa.clima.work.TemperatureNotifier
import com.mnavarrosqa.clima.work.WeatherWorker
import com.mnavarrosqa.clima.work.SunNotifier
import com.mnavarrosqa.clima.work.Outing
import com.mnavarrosqa.clima.data.tr
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClimaApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var nightMode = 0

    override fun onCreate() {
        super.onCreate()
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        RainNotifier.createChannel(this)
        AlertNotifier.createChannel(this)
        TemperatureNotifier.createChannel(this)
        Notify.createChannels(this)
        SunNotifier.createChannel(this)
        Outing.createChannel(this)
        addOutingShortcut()
        WeatherWorker.schedule(this)
        SummaryNotifier.schedule(this, ExistingWorkPolicy.KEEP)
    }

    /** "Salgo ahora" al mantener apretado el ícono de la app. */
    private fun addOutingShortcut() = runCatching {
        val shortcut = ShortcutInfoCompat.Builder(this, "salgo")
            .setShortLabel(getString(R.string.tile_outing_name))
            .setLongLabel(tr("Salgo ahora: 2 horas de clima en vivo", "Heading out: 2 hours of live weather"))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_outing))
            .setIntent(Intent(this, ClimaActivity::class.java).setAction(Outing.ACTION_START))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }

    /**
     * Al pasar el teléfono a modo claro u oscuro se repintan los widgets: los que tienen opacidad
     * propia calculan su color una vez y no lo cambiarían solos.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (night != nightMode) {
            nightMode = night
            scope.launch { runCatching { updateWeatherWidgets(this@ClimaApp) } }
        }
    }
}
