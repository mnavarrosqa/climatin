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
        WeatherWorker.schedule(this)
        SummaryNotifier.schedule(this, ExistingWorkPolicy.KEEP)
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
