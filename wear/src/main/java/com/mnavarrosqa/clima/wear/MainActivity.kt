package com.mnavarrosqa.clima.wear

import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.ageText
import kotlinx.coroutines.launch

/** Pantalla mínima del reloj: pide la ubicación y muestra el clima actual. */
class MainActivity : ComponentActivity() {

    private lateinit var temperature: TextView
    private lateinit var detail: TextView

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && WearStore.cached(this) == null) {
            detail.text = tr("Sin permiso de ubicación: abrí Climatin en el teléfono o permitila en Ajustes", "No location permission: open Climatin on your phone or allow it in Settings")
        } else {
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        temperature = TextView(this).apply {
            textSize = 40f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        detail = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                // En dp (no píxeles) y con margen para las pantallas redondas.
                val pad = (24 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                addView(temperature)
                addView(detail)
            },
        )
        show(WearStore.cached(this))
        // Con los datos del teléfono no hace falta pedir la ubicación del reloj.
        if (!WearStore.hasPermission(this) && !WearStore.phoneIsFresh(this)) permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    override fun onResume() {
        super.onResume()
        // Al volver a la app, si los datos tienen más de 15 minutos, se actualizan.
        val cached = WearStore.cached(this)
        if (cached == null || System.currentTimeMillis() - cached.fetchedAt > 15 * 60_000L) refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            val result = WearStore.refresh(this@MainActivity)
            show(result.getOrNull() ?: WearStore.cached(this@MainActivity))
            if (result.isFailure && WearStore.cached(this@MainActivity) == null) {
                detail.text = tr("No pude obtener el clima", "Couldn't get the weather")
            }
        }
    }

    private fun show(snapshot: WeatherSnapshot?) {
        if (snapshot == null) {
            temperature.text = "—"
            detail.text = tr("Buscando el clima…", "Getting the weather…")
            return
        }
        val today = snapshot.today()
        temperature.text = snapshot.current.temperature.degrees()
        detail.text = buildString {
            snapshot.place?.let { append(it + "\n") }
            append(WeatherCodes.description(snapshot.current.code))
            if (today != null) append("\n" + tr("Máx", "High") + " ${today.max.degrees()} · " + tr("Mín", "Low") + " ${today.min.degrees()}")
            append("\n" + tr("Actualizado ", "Updated ") + ageText(snapshot.fetchedAt))
        }
    }
}
