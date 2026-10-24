package com.mnavarrosqa.clima.wear

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
import kotlinx.coroutines.launch

/** Pantalla mínima del reloj: pide la ubicación y muestra el clima actual. */
class MainActivity : ComponentActivity() {

    private lateinit var temperature: TextView
    private lateinit var detail: TextView

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

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
                setPadding(24, 24, 24, 24)
                addView(temperature)
                addView(detail)
            },
        )
        show(WearStore.cached(this))
        if (WearStore.hasPermission(this)) refresh() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun refresh() {
        lifecycleScope.launch {
            val result = WearStore.refresh(this@MainActivity)
            show(result.getOrNull() ?: WearStore.cached(this@MainActivity))
            if (result.isFailure && WearStore.cached(this@MainActivity) == null) {
                detail.text = "No pude obtener el clima"
            }
        }
    }

    private fun show(snapshot: WeatherSnapshot?) {
        if (snapshot == null) {
            temperature.text = "—"
            detail.text = "Buscando el clima…"
            return
        }
        val today = snapshot.today()
        temperature.text = snapshot.current.temperature.degrees()
        detail.text = buildString {
            append(WeatherCodes.description(snapshot.current.code))
            if (today != null) append("\nMáx ${today.max.degrees()} · Mín ${today.min.degrees()}")
        }
    }
}
