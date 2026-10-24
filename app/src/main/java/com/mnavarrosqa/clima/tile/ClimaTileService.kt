package com.mnavarrosqa.clima.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mnavarrosqa.clima.MainActivity
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.degrees

/** Botón de ajustes rápidos con la temperatura actual; tocarlo abre la app. */
class ClimaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val snapshot = WeatherRepository.get(this).cached()
        if (snapshot == null) {
            tile.label = "Climatin"
            tile.state = Tile.STATE_INACTIVE
        } else {
            val current = snapshot.current
            tile.label = "${current.temperature.degrees()} ${WeatherCodes.description(current.code)}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = snapshot.place
            tile.icon = Icon.createWithResource(this, WeatherCodes.icon(current.code, current.isDay))
            tile.state = Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        /** Pide al sistema que vuelva a dibujar el botón con los datos nuevos. */
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, ClimaTileService::class.java))
            }
        }
    }
}
