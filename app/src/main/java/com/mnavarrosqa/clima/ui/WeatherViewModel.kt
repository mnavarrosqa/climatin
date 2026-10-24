package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mnavarrosqa.clima.data.AppRelease
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.Updates
import com.mnavarrosqa.clima.data.NoLocationException
import com.mnavarrosqa.clima.data.OpenMeteo
import com.mnavarrosqa.clima.data.Place
import com.mnavarrosqa.clima.data.UnitSettings
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.widget.updateWeatherWidgets
import com.mnavarrosqa.clima.work.AlertNotifier
import com.mnavarrosqa.clima.work.RainNotifier
import com.mnavarrosqa.clima.work.TemperatureNotifier
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

    val smnAlerts: StateFlow<Boolean> =
        repository.smnAlertsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, repository.smnAlertsEnabled)

    val statusTemperature: StateFlow<Boolean> = repository.statusTemperatureFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.statusTemperatureEnabled)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _update = MutableStateFlow<AppRelease?>(null)
    /** Versión nueva disponible (fuera de Play), o null. */
    val update: StateFlow<AppRelease?> = _update.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    /** Resultado de la última búsqueda manual de actualizaciones. */
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch { _update.value = Updates.check(getApplication<Application>(), force = false) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateMessage.value = tr("Buscando…", "Checking…")
            val release = Updates.check(getApplication<Application>(), force = true)
            _update.value = release
            _updateMessage.value = if (release != null) {
                tr("Hay una versión nueva: ${release.versionName}", "New version available: ${release.versionName}")
            } else {
                tr("Tenés la última versión", "You're up to date")
            }
        }
    }

    fun dismissUpdate() {
        _update.value = null
    }

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
            val error = repository.refreshAll(force = true)
            updateWeatherWidgets(getApplication<Application>())
            RainNotifier.check(getApplication<Application>())
            AlertNotifier.check(getApplication<Application>())
            TemperatureNotifier.update(getApplication<Application>())
            _error.value = when (error) {
                null -> null
                is NoLocationException -> error.message
                else -> tr("No pude actualizar el clima. Revisá la conexión.", "Couldn't update the weather. Check your connection.")
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
                onFailure = { SearchState(error = tr("No pude buscar. Revisá la conexión.", "Couldn't search. Check your connection.")) },
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

    fun setStatusTemperature(enabled: Boolean) {
        repository.statusTemperatureEnabled = enabled
        TemperatureNotifier.update(getApplication<Application>())
    }

    fun setSmnAlerts(enabled: Boolean) {
        repository.smnAlertsEnabled = enabled
        if (enabled) AlertNotifier.check(getApplication<Application>())
    }

    fun setRainAlerts(enabled: Boolean) {
        repository.rainAlertsEnabled = enabled
        if (enabled) RainNotifier.check(getApplication<Application>())
    }
}
