package com.mnavarrosqa.clima

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mnavarrosqa.clima.ui.WeatherRoute
import com.mnavarrosqa.clima.ui.theme.ClimaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ClimaTheme {
                WeatherRoute()
            }
        }
    }
}
