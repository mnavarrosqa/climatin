package com.mnavarrosqa.clima.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import com.mnavarrosqa.clima.ClimaActivity
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.work.Outing
import com.mnavarrosqa.clima.work.RainNotifier

/** Ajustes rápidos: "Salgo ahora" prende o apaga dos horas de clima en vivo. */
class SalgoTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val active = Outing.active(this)
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) tr("Siguiendo el clima", "Tracking weather") else tr("2 horas en vivo", "2 hours live")
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        // Sin permiso de notificaciones no hay dónde mostrarlo: se abre la app para pedirlo.
        if (!RainNotifier.canNotify(this)) {
            val intent = Intent(this, ClimaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        Outing.toggle(this)
        onStartListening()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, SalgoTileService::class.java)) }
        }
    }
}
