package com.mnavarrosqa.clima.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.NoLocationException
import com.mnavarrosqa.clima.data.OpenMeteo
import com.mnavarrosqa.clima.data.Place
import com.mnavarrosqa.clima.data.UnitSettings
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.widget.updateWeatherWidgets
import com.mnavarrosqa.clima.work.RainNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchState(
    val loading: Boolean = false,
    val results: List<Place> = emptyList(),
    val error: String? = null,
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    val repository = WeatherRepository.get(application)

    val places: StateFlow<List<Place>> =
        repository.placesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, repository.places())
    val rainAlerts: StateFlow<Boolean> =
        repository.rainAlertsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, repository.rainAlertsEnabled)

    val units: StateFlow<UnitSettings> =
        repository.unitsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, repository.units)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var searchJob: Job? = null

    fun snapshots(placeId: String): Flow<WeatherSnapshot?> = repository.snapshots(placeId)

    fun cached(placeId: String): WeatherSnapshot? = repository.cached(placeId)

    /** Refresca si falta algún lugar o el de "donde estoy" tiene más de 15 minutos. */
    fun refreshIfStale() {
        val ids = listOf(CURRENT_PLACE_ID) + places.value.map { it.id }
        val stale = ids.any { id ->
            val snapshot = repository.cached(id)
            snapshot == null || System.currentTimeMillis() - snapshot.fetchedAt > 15 * 60_000
        }
        if (stale) refresh()
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            val error = repository.refreshAll()
            updateWeatherWidgets(getApplication<Application>())
            RainNotifier.check(getApplication<Application>())
            _error.value = when (error) {
                null -> null
                is NoLocationException -> error.message
                else -> "No pude actualizar el clima. Revisá la conexión."
            }
            _refreshing.value = false
        }
    }

    fun searchCities(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _search.value = SearchState()
            return
        }
        searchJob = viewModelScope.launch {
            _search.value = _search.value.copy(loading = true, error = null)
            _search.value = runCatching { OpenMeteo.searchCities(query) }.fold(
                onSuccess = { SearchState(results = it) },
                onFailure = { SearchState(error = "No pude buscar. Revisá la conexión.") },
            )
        }
    }

    fun addPlace(place: Place) {
        repository.addPlace(place)
        _search.value = SearchState()
        viewModelScope.launch {
            repository.refreshPlace(place)
            updateWeatherWidgets(getApplication<Application>())
        }
    }

    fun removePlace(id: String) {
        repository.removePlace(id)
        viewModelScope.launch { updateWeatherWidgets(getApplication<Application>()) }
    }

    fun movePlace(id: String, offset: Int) = repository.movePlace(id, offset)

    fun setUnits(units: UnitSettings) {
        repository.units = units
        viewModelScope.launch { updateWeatherWidgets(getApplication<Application>()) }
    }

    fun setRainAlerts(enabled: Boolean) {
        repository.rainAlertsEnabled = enabled
        if (enabled) RainNotifier.check(getApplication<Application>())
    }
}
