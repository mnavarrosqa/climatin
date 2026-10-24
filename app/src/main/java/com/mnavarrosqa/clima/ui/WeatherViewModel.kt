package com.mnavarrosqa.clima.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mnavarrosqa.clima.data.NoLocationException
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.widget.updateWeatherWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeatherUiState(
    val snapshot: WeatherSnapshot? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository.get(application)
    private val _state = MutableStateFlow(WeatherUiState(snapshot = repository.cached()))
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.snapshots.collect { snapshot -> _state.update { it.copy(snapshot = snapshot) } }
        }
    }

    /** Refresca si no hay datos o tienen más de 15 minutos. */
    fun refreshIfStale() {
        val snapshot = _state.value.snapshot
        if (snapshot == null || System.currentTimeMillis() - snapshot.fetchedAt > 15 * 60_000) refresh()
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            val result = repository.refresh()
            updateWeatherWidgets(getApplication<Application>())
            _state.update {
                it.copy(
                    refreshing = false,
                    error = result.exceptionOrNull()?.let { e ->
                        if (e is NoLocationException) e.message else "No pude actualizar el clima. Revisá la conexión."
                    },
                )
            }
        }
    }
}
