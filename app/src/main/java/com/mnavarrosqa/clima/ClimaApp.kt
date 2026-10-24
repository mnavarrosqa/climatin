package com.mnavarrosqa.clima

import android.app.Application
import com.mnavarrosqa.clima.work.AlertNotifier
import com.mnavarrosqa.clima.work.RainNotifier
import com.mnavarrosqa.clima.work.TemperatureNotifier
import com.mnavarrosqa.clima.work.WeatherWorker

class ClimaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RainNotifier.createChannel(this)
        AlertNotifier.createChannel(this)
        TemperatureNotifier.createChannel(this)
        WeatherWorker.schedule(this)
    }
}
