package com.mnavarrosqa.clima.ui

import java.io.File
import com.mnavarrosqa.clima.data.tr
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mnavarrosqa.clima.data.AppRelease
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.UpdateInstaller
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
import com.mnavarrosqa.clima.work.WeatherWorker
import com.mnavarrosqa.clima.work.TemperatureNotifier
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Qué espera la primera carga: la ubicación o el pronóstico. */
enum class LoadPhase { LOCATING, DOWNLOADING }

data class SearchState(
    val loading: Boolean = false,
    val results: List<Place> = emptyList(),
    val error: String? = null,
    /** Lo que se buscó: hasta que llega la respuesta de lo que se escribe, no se dice "no encontré". */
    val query: String = "",
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

    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    /**
     * Qué lugar no se pudo actualizar y por qué. Va por lugar: si falla una ciudad recién agregada,
     * el aviso no aparece en las demás.
     */
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    private val connectivity = application.getSystemService(ConnectivityManager::class.java)

    private val _offline = MutableStateFlow(!isOnline())
    /** Sin conexión: no es un error, se muestra lo guardado y se reintenta al volver la red. */
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private val _loadPhase = MutableStateFlow<LoadPhase?>(null)
    /** Qué se está esperando mientras no hay pronóstico de "donde estoy" (para el esqueleto). */
    val loadPhase: StateFlow<LoadPhase?> = _loadPhase.asStateFlow()

    // Al volver la conexión se reintenta solo, si lo último fue sin red o con error.
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch { onNetworkBack() }
        }

        override fun onLost(network: Network) {
            viewModelScope.launch { _offline.value = !isOnline() }
        }
    }

    private val _update = MutableStateFlow<AppRelease?>(null)
    /** Versión nueva disponible (fuera de Play), o null. */
    val update: StateFlow<AppRelease?> = _update.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    /** Resultado de la última búsqueda manual de actualizaciones. */
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    private val _cards = MutableStateFlow(CardLayoutPrefs.load(application))
    /** Orden y visibilidad de las tarjetas del pronóstico. */
    val cards: StateFlow<CardLayout> = _cards.asStateFlow()

    fun setCards(layout: CardLayout) {
        _cards.value = layout
        CardLayoutPrefs.save(getApplication(), layout)
    }

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var searchJob: Job? = null

    private val _updatePrompt = MutableStateFlow<AppRelease?>(null)
    /** La versión nueva para el aviso flotante: la misma, salvo que se haya dicho "Ahora no". */
    val updatePrompt: StateFlow<AppRelease?> = _updatePrompt.asStateFlow()

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
        recheckUpdates()
        // Accesos directos a las primeras ciudades: siguen a la lista (agregar, quitar, ordenar, apodos).
        viewModelScope.launch(Dispatchers.IO) {
            places.collect { CityShortcuts.update(getApplication<Application>(), it) }
        }
    }

    /** Con el pronóstico nuevo, el ícono de cada acceso directo muestra el clima de ahora. */
    private suspend fun refreshShortcuts() = withContext(Dispatchers.IO) {
        CityShortcuts.update(getApplication<Application>(), places.value)
    }

    private var updatesJob: Job? = null

    /** Al abrir o volver a la app: consulta (cada 10 minutos como mucho) y muestra el aviso si corresponde. */
    fun recheckUpdates() {
        // Al abrir, el inicio y la vuelta a la pantalla piden a la vez: con una consulta alcanza.
        if (updatesJob?.isActive == true) return
        updatesJob = viewModelScope.launch { publishUpdate(Updates.check(getApplication<Application>(), force = false)) }
    }

    private fun publishUpdate(release: AppRelease?) {
        _update.value = release
        _updatePrompt.value = release?.takeUnless { Updates.isDismissed(getApplication<Application>(), it) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateMessage.value = tr("Buscando…", "Checking…")
            val release = Updates.check(getApplication<Application>(), force = true)
            publishUpdate(release)
            _updateMessage.value = if (release != null) {
                tr("Hay una versión nueva: ${release.versionName}", "New version available: ${release.versionName}")
            } else {
                tr("Tenés la última versión", "You're up to date")
            }
        }
    }

    private val _updateProgress = MutableStateFlow<Float?>(null)
    /** Avance de la descarga de la actualización (0 a 1), o null si no se está bajando. */
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    /**
     * APK ya bajado que espera el permiso de instalar apps. Se guarda también en las preferencias:
     * mientras se da el permiso, Android puede cerrar la app y al volver se perdía.
     */
    private var pendingApk: java.io.File?
        get() = getApplication<Application>().getSharedPreferences("clima", android.content.Context.MODE_PRIVATE)
            .getString(KEY_PENDING_APK, null)?.let { File(it) }?.takeIf { it.exists() }
        set(value) {
            getApplication<Application>().getSharedPreferences("clima", android.content.Context.MODE_PRIVATE)
                .edit().putString(KEY_PENDING_APK, value?.path).apply()
        }

    /** Baja la versión nueva y abre el instalador (pidiendo el permiso la primera vez). */
    fun downloadUpdate(release: AppRelease) {
        if (_updateProgress.value != null) return
        viewModelScope.launch {
            _updateProgress.value = 0f
            val app = getApplication<Application>()
            val file = runCatching {
                UpdateInstaller.download(app, release) { _updateProgress.value = it }
            }.getOrNull()
            _updateProgress.value = null
            if (file == null) {
                _updateMessage.value = tr("No pude bajar la actualización. Revisá la conexión.", "Couldn't download the update. Check your connection.")
                return@launch
            }
            pendingApk = file
            resumePendingInstall(askPermission = true)
        }
    }

    /** Si hay un APK bajado y Android ya da permiso, abre el instalador (se llama al volver a la app). */
    fun resumePendingInstall(askPermission: Boolean = false) {
        val file = pendingApk ?: return
        val app = getApplication<Application>()
        if (UpdateInstaller.canInstall(app)) {
            pendingApk = null
            UpdateInstaller.install(app, file)
        } else if (askPermission) {
            _updateMessage.value = tr(
                "Permití \"Instalar apps desconocidas\" para Climatin y volvé",
                "Allow \"Install unknown apps\" for Climatin and come back",
            )
            UpdateInstaller.openPermissionSettings(app)
        }
    }

    fun dismissUpdate() {
        _updatePrompt.value?.let { Updates.dismiss(getApplication<Application>(), it) }
        _updatePrompt.value = null
    }

    fun snapshots(placeId: String): Flow<WeatherSnapshot?> = repository.snapshots(placeId)

    fun cached(placeId: String): WeatherSnapshot? = repository.cached(placeId)

    /**
     * Refresca si a algún lugar le falta el pronóstico o tiene más de 15 minutos. Las ciudades que
     * se están bajando solas (recién agregadas) no cuentan: si no, se bajaba todo dos veces.
     */
    fun refreshIfStale() {
        val ids = listOf(CURRENT_PLACE_ID) + places.value.map { it.id }.filterNot { it in fetching }
        // Leer los pronósticos guardados es leer archivos: fuera del hilo principal (al abrir la
        // app con varias ciudades trababa la pantalla).
        viewModelScope.launch {
            val stale = withContext(Dispatchers.IO) {
                ids.any { id ->
                    val snapshot = repository.cached(id)
                    snapshot == null || System.currentTimeMillis() - snapshot.fetchedAt > 15 * 60_000
                }
            }
            if (stale) refresh()
        }
    }

    /** Ciudades recién agregadas que se están bajando. */
    private val fetching = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            // Los errores anteriores quedan hasta tener el resultado nuevo: borrarlos al empezar
            // hacía parpadear el aviso en cada actualización.
            try {
                if (!isOnline()) {
                    // Sin red no se intenta: no es un error, y al volver la conexión se reintenta.
                    _offline.value = true
                    return@launch
                }
                _offline.value = false
                val startedAt = System.currentTimeMillis()
                val phaseJob = trackLoadPhase()
                val error = try {
                    repository.refreshAll(force = true)
                } finally {
                    phaseJob?.cancel()
                    _loadPhase.value = null
                }
                // Widgets, avisos y reloj, lo mismo que el trabajo periódico. Un aviso que falla no
                // puede cerrar la app ni dejar el indicador girando.
                com.mnavarrosqa.clima.work.afterRefresh(getApplication<Application>())
                refreshShortcuts()
                when {
                    error == null -> _errors.value = emptyMap()
                    // Se cortó la red en el medio: es "sin conexión", no un error.
                    error !is NoLocationException && !isOnline() -> {
                        _offline.value = true
                        _errors.value = emptyMap()
                    }
                    else -> _errors.value = withContext(Dispatchers.IO) { failedPlaces(error, startedAt) }
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    /**
     * Los lugares que quedaron sin actualizar: todos se piden juntos, pero "donde estoy" puede
     * fallar sola (sin ubicación) y el resto llegar bien.
     */
    private fun failedPlaces(error: Throwable, startedAt: Long): Map<String, String> {
        val generic = tr("No pude actualizar el clima. Revisá la conexión.", "Couldn't update the weather. Check your connection.")
        val ids = listOf(CURRENT_PLACE_ID) + places.value.map { it.id }
        return ids.filter { id -> (repository.cached(id)?.fetchedAt ?: 0L) < startedAt }.associateWith { id ->
            if (id == CURRENT_PLACE_ID && error is NoLocationException) error.message ?: generic else generic
        }
    }

    /**
     * Sin pronóstico de "donde estoy", el esqueleto dice qué se espera. El repositorio no cuenta
     * en qué paso está: primero se busca la ubicación y, pasado lo que suele tardar, se asume que
     * ya se está bajando el pronóstico.
     */
    private fun trackLoadPhase(): Job? {
        val app = getApplication<Application>()
        if (repository.cached(CURRENT_PLACE_ID) != null) return null
        if (!com.mnavarrosqa.clima.data.LocationProvider.hasPermission(app)) {
            _loadPhase.value = LoadPhase.DOWNLOADING
            return null
        }
        _loadPhase.value = LoadPhase.LOCATING
        return viewModelScope.launch {
            delay(4_000)
            _loadPhase.value = LoadPhase.DOWNLOADING
        }
    }

    /** Volvió la red: si lo último fue sin conexión o con error, se reintenta. */
    private suspend fun onNetworkBack() {
        val retry = _offline.value || _errors.value.isNotEmpty()
        _offline.value = false
        if (!retry) return
        // Al conectarse, la red tarda un momento en andar de verdad (Wi-Fi con portal, datos).
        delay(1_500)
        if (isOnline()) refresh() else _offline.value = true
    }

    /** Lo mismo que mira el trabajo periódico: si hay una red con internet. */
    private fun isOnline(): Boolean = runCatching {
        val manager = connectivity ?: return@runCatching true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    override fun onCleared() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    fun searchCities(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _search.value = SearchState()
            return
        }
        searchJob = viewModelScope.launch {
            _search.value = _search.value.copy(loading = true, error = null)
            _search.value = try {
                SearchState(results = OpenMeteo.searchCities(query), query = query.trim())
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                // La canceló la letra siguiente: no es un error, ya viene la búsqueda nueva.
                throw cancel
            } catch (_: Exception) {
                SearchState(error = tr("No pude buscar. Revisá la conexión.", "Couldn't search. Check your connection."), query = query.trim())
            }
        }
    }

    fun addPlace(place: Place) {
        repository.addPlace(place)
        _search.value = SearchState()
        // Al deshacer un "Quitar" el pronóstico sigue guardado: no hace falta bajarlo.
        if (repository.cached(place.id) != null) {
            viewModelScope.launch { updateWeatherWidgets(getApplication<Application>()) }
            return
        }
        fetching += place.id
        viewModelScope.launch {
            val result = repository.refreshPlace(place)
            fetching -= place.id
            if (result.isFailure) {
                _errors.update {
                    it + (place.id to tr(
                        "No pude bajar el pronóstico de ${place.shownName}. Se reintenta en la próxima actualización.",
                        "Couldn't get the forecast for ${place.shownName}. It'll retry on the next update.",
                    ))
                }
            } else {
                _errors.update { it - place.id }
            }
            updateWeatherWidgets(getApplication<Application>())
            refreshShortcuts()
        }
    }

    /** Quita la ciudad; el pronóstico guardado queda hasta [discardForecast] por si se deshace. */
    fun removePlace(id: String) {
        repository.removePlace(id)
        viewModelScope.launch { updateWeatherWidgets(getApplication<Application>()) }
    }

    /** Ya no se puede deshacer: se borra el pronóstico de la ciudad quitada. */
    fun discardForecast(id: String) = repository.discardForecast(id)

    fun movePlace(id: String, offset: Int) {
        repository.movePlace(id, offset)
        // El widget de ciudades muestra el orden: se actualiza ya, no en la próxima descarga.
        viewModelScope.launch { updateWeatherWidgets(getApplication<Application>()) }
    }

    fun renamePlace(id: String, nickname: String?) {
        repository.renamePlace(id, nickname)
        viewModelScope.launch { updateWeatherWidgets(getApplication<Application>()) }
    }

    fun setUnits(units: UnitSettings) {
        repository.units = units
        viewModelScope.launch {
            updateWeatherWidgets(getApplication<Application>())
            // El reloj usa las mismas unidades.
            com.mnavarrosqa.clima.work.WearSync.send(getApplication<Application>(), urgent = true)
        }
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
        // Con el aviso de lluvia prendido se revisa cada 15 minutos.
        WeatherWorker.schedule(getApplication<Application>())
        if (enabled) RainNotifier.check(getApplication<Application>())
    }

    private companion object {
        const val KEY_PENDING_APK = "apk_pendiente"
    }
}
