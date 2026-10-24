package com.mnavarrosqa.clima

import android.app.Application
import com.mnavarrosqa.clima.work.WeatherWorker

class ClimaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WeatherWorker.schedule(this)
    }
}
