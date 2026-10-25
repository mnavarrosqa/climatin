package com.mnavarrosqa.clima.data

import com.mnavarrosqa.clima.data.tr
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

class NoLocationException : Exception(tr("Todavía no tengo tu ubicación", "I don't have your location yet"))

/**
 * Fuente única para la app y los widgets. Guarda las ciudades y los ajustes en SharedPreferences,
 * y el último pronóstico de cada lugar ("current" es donde estás) en un archivo propio: así abrir
 * la app no lee todos los pronósticos juntos (antes iban en el mismo archivo que los ajustes).
 */
class WeatherRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("clima", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val placesSerializer = ListSerializer(Place.serializer())

    init {
        Units.current = readUnits()
    }

    // --- Lugares ---

    @Volatile
    private var placesDecoded: Pair<String, List<Place>>? = null

    /** Ciudades guardadas; se decodifican una sola vez mientras no cambien (los widgets las leen seguido). */
    fun places(): List<Place> {
        val raw = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        placesDecoded?.let { (text, list) -> if (text == raw) return list }
        val list = runCatching { json.decodeFromString(placesSerializer, raw) }.getOrNull().orEmpty()
        placesDecoded = raw to list
        return list
    }

    val placesFlow: Flow<List<Place>> = prefsFlow(KEY_PLACES) { places() }

    fun addPlace(place: Place) {
        val current = places()
        if (current.any { it.id == place.id }) return
        savePlaces(current + place)
    }

    /**
     * Pone o saca el apodo de una ciudad. También cambia el nombre en el pronóstico guardado, así
     * el pronóstico, los widgets y los avisos lo muestran sin esperar a la próxima actualización.
     */
    fun renamePlace(id: String, nickname: String?) {
        val clean = nickname?.trim()?.takeIf { it.isNotEmpty() }
        val list = places().map { if (it.id == id) it.copy(nickname = clean?.takeIf { name -> name != it.name }) else it }
        val place = list.firstOrNull { it.id == id } ?: return
        savePlaces(list)
        cached(id)?.let { snapshot -> writeSnapshot(id, snapshot.copy(place = place.shownName)) }
    }

    /**
     * Saca la ciudad de la lista. El pronóstico queda guardado hasta [discardForecast] (o la
     * próxima actualización completa): así "Deshacer" la devuelve con su pronóstico.
     */
    fun removePlace(id: String) {
        savePlaces(places().filterNot { it.id == id })
        changes.tryEmit(id)
    }

    /** Borra el pronóstico de una ciudad que ya no está en la lista. */
    fun discardForecast(id: String) {
        if (id == CURRENT_PLACE_ID || places().any { it.id == id }) return
        file(id).delete()
        decoded.remove(id)
        prefs.edit().remove(snapshotKey(id)).apply()
    }

    /** Pronósticos de ciudades quitadas que quedaron (por ejemplo, si se cerró la app antes de tiempo). */
    private fun discardOrphans() {
        val keep = (listOf(CURRENT_PLACE_ID) + places().map { it.id }).map { file(it).name }.toSet()
        snapshotDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".json") && file.name !in keep) file.delete()
        }
    }

    fun movePlace(id: String, offset: Int) {
        val list = places().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        val target = index + offset
        if (index < 0 || target !in list.indices) return
        list.add(target, list.removeAt(index))
        savePlaces(list)
    }

    private fun savePlaces(list: List<Place>) {
        prefs.edit().putString(KEY_PLACES, json.encodeToString(placesSerializer, list)).apply()
    }

    /** Si la ciudad de un widget se borró, vuelve a "donde estoy". */
    fun resolvePlaceId(id: String?): String =
        if (id == null || id == CURRENT_PLACE_ID || places().none { it.id == id }) CURRENT_PLACE_ID else id

    // --- Pronósticos ---

    private val snapshotDir = File(context.filesDir, "pronosticos")

    private fun file(placeId: String) = File(snapshotDir, placeId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    /** Avisa qué lugar cambió, para los que observan su pronóstico. */
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 64)

    // Los widgets leen la caché a cada rato: se guarda decodificada mientras el archivo no cambie.
    private val decoded = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, WeatherSnapshot>>()

    fun cached(placeId: String = CURRENT_PLACE_ID): WeatherSnapshot? {
        val file = file(placeId)
        if (!file.exists()) return migrate(placeId)
        val stamp = file.lastModified()
        decoded[placeId]?.let { (at, snapshot) -> if (at == stamp) return snapshot }
        val snapshot = runCatching { json.decodeFromString(WeatherSnapshot.serializer(), file.readText()) }.getOrNull() ?: return null
        decoded[placeId] = stamp to snapshot
        return snapshot
    }

    /** Versiones anteriores guardaban el pronóstico en las preferencias: se pasa a su archivo una vez. */
    private fun migrate(placeId: String): WeatherSnapshot? {
        val raw = prefs.getString(snapshotKey(placeId), null) ?: return null
        val snapshot = runCatching { json.decodeFromString(WeatherSnapshot.serializer(), raw) }.getOrNull()
        snapshot?.let { writeSnapshot(placeId, it, notify = false) }
        prefs.edit().remove(snapshotKey(placeId)).apply()
        return snapshot
    }

    /** Escribe en un archivo aparte y lo renombra: un corte a mitad de camino no deja uno roto. */
    @Synchronized
    private fun writeSnapshot(placeId: String, snapshot: WeatherSnapshot, notify: Boolean = true) {
        snapshotDir.mkdirs()
        val target = file(placeId)
        val temp = File(snapshotDir, target.name + ".tmp")
        temp.writeText(json.encodeToString(WeatherSnapshot.serializer(), snapshot))
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
        decoded[placeId] = target.lastModified() to snapshot
        if (notify) changes.tryEmit(placeId)
    }

    /** Emite la caché del lugar y cada vez que cambia. Lee el archivo fuera del hilo principal. */
    fun snapshots(placeId: String = CURRENT_PLACE_ID): Flow<WeatherSnapshot?> = flow {
        emit(cached(placeId))
        changes.filter { it == placeId }.collect { emit(cached(placeId)) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    /** "Donde estoy" y todas las ciudades guardadas que tengan pronóstico, en orden. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun allSnapshots(): Flow<List<WeatherSnapshot>> = placesFlow.flatMapLatest { places ->
        val ids = listOf(CURRENT_PLACE_ID) + places.map { it.id }
        combine(ids.map { snapshots(it) }) { list -> list.filterNotNull() }
    }

    fun allCached(): List<WeatherSnapshot> =
        (listOf(CURRENT_PLACE_ID) + places().map { it.id }).mapNotNull { cached(it) }

    private val refreshLock = Mutex()
    /** Última actualización de "donde estoy" y de todo (con las ciudades), en milisegundos. */
    @Volatile
    private var lastRefreshAt = 0L
    @Volatile
    private var lastFullRefreshAt = 0L

    /**
     * Si las ciudades guardadas tienen más de una hora. Se guarda en las preferencias porque el
     * trabajo periódico suele correr en un proceso nuevo cada vez.
     */
    fun citiesStale(): Boolean =
        places().isNotEmpty() && System.currentTimeMillis() - prefs.getLong(KEY_CITIES_AT, 0L) > 55 * 60_000L

    /**
     * Actualiza "donde estoy" y, con [cities], todas las ciudades guardadas, en paralelo. Devuelve
     * el primer error, si hubo. Si ya hay una actualización en curso se espera a esa, y si terminó
     * hace menos de un minuto no se vuelve a pedir (la app, el widget y el refresco periódico
     * pueden coincidir).
     */
    suspend fun refreshAll(force: Boolean = false, cities: Boolean = true): Throwable? {
        val requestedAt = System.currentTimeMillis()
        return refreshLock.withLock { refreshLocked(force, cities, requestedAt) }
    }

    private suspend fun refreshLocked(force: Boolean, cities: Boolean, requestedAt: Long): Throwable? {
        // Si otra actualización que cubre lo mismo terminó mientras esta esperaba, ya está fresco
        // (aunque sea forzada).
        val last = if (cities) lastFullRefreshAt else lastRefreshAt
        if (last >= requestedAt) return null
        if (!force && System.currentTimeMillis() - last < 60_000) return null
        // Todos los lugares en un solo pedido a Open-Meteo (acepta varias coordenadas): con cinco
        // ciudades, 2 pedidos por actualización en vez de 12.
        val here = runCatchingCancellable { currentTarget() }
        val saved = if (cities) places().map { Target(it.id, it.shownName, it.latitude, it.longitude) } else emptyList()
        val targets = listOfNotNull(here.getOrNull()) + saved
        if (targets.isEmpty()) return here.exceptionOrNull()
        val result = runCatchingCancellable { fetchAll(targets) }
        val firstError = result.exceptionOrNull()
            ?: here.exceptionOrNull()?.takeIf { it !is NoLocationException }
        if (result.isSuccess) {
            val now = System.currentTimeMillis()
            if (here.isSuccess) lastRefreshAt = now
            if (cities) {
                lastFullRefreshAt = now
                prefs.edit().putLong(KEY_CITIES_AT, now).apply()
                runCatching { discardOrphans() }
            }
        }
        return firstError
    }

    // Las alertas del SMN se piden una vez y sirven para todos los lugares durante 10 minutos.
    private var capCache: Pair<Long, List<CapAlert>>? = null

    private val capLock = Mutex()

    /** Null si el SMN no respondió y no hay nada en memoria: no es lo mismo que "no hay alertas". */
    private suspend fun capAlerts(): List<CapAlert>? = capLock.withLock {
        capCache?.let { (at, alerts) -> if (System.currentTimeMillis() - at < 10 * 60_000) return@withLock alerts }
        val alerts = runCatching { SmnAlerts.fetchAll(context.cacheDir) }
            .getOrElse { return@withLock capCache?.second }
        capCache = System.currentTimeMillis() to alerts
        alerts
    }

    /**
     * La calidad del aire cambia despacio: se guarda una hora por lugar. Con el trabajo cada 15
     * minutos eran cuatro pedidos por hora que daban casi lo mismo.
     */
    private val airCache = java.util.concurrent.ConcurrentHashMap<String, Triple<Long, Pair<Double, Double>, AirQuality>>()

    private suspend fun airQualities(targets: List<Target>): List<AirQuality?> {
        val now = System.currentTimeMillis()
        fun fresh(t: Target) = airCache[t.id]?.takeIf { (at, point) ->
            now - at < 60 * 60_000L && abs(point.first - t.latitude) < 0.02 && abs(point.second - t.longitude) < 0.02
        }?.third
        val missing = targets.filter { fresh(it) == null }
        val fetched = OpenMeteo.airQualities(missing.map { it.latitude to it.longitude })
        missing.forEachIndexed { i, t ->
            fetched.getOrNull(i)?.let { airCache[t.id] = Triple(now, t.latitude to t.longitude, it) }
        }
        // Si falló, mejor la de la última vez que nada, pero solo si es del mismo lugar: si no, al
        // moverse se mostraba el aire de donde se estaba antes.
        fun near(lat: Double, lon: Double, t: Target) = abs(lat - t.latitude) < 0.02 && abs(lon - t.longitude) < 0.02
        return targets.map { t ->
            fresh(t)
                ?: airCache[t.id]?.takeIf { (_, point) -> near(point.first, point.second, t) }?.third
                ?: cached(t.id)?.takeIf { near(it.latitude, it.longitude, t) }?.airQuality
        }
    }

    /** Un lugar para pedir: id, nombre a mostrar y coordenadas. */
    private data class Target(val id: String, val name: String?, val latitude: Double, val longitude: Double)

    suspend fun refreshCurrent(): Result<WeatherSnapshot> = runCatchingCancellable {
        fetchAll(listOf(currentTarget())).first().also { lastRefreshAt = System.currentTimeMillis() }
    }

    /** Dónde estás ahora (o la última ubicación conocida), con su nombre. */
    private suspend fun currentTarget(): Target {
        val previous = cached(CURRENT_PLACE_ID)
        val coordinates = LocationProvider.current(context)
            ?: previous?.let { Coordinates(it.latitude, it.longitude) }
            ?: throw NoLocationException()

        val samePlace = previous != null &&
            abs(previous.latitude - coordinates.latitude) < 0.02 &&
            abs(previous.longitude - coordinates.longitude) < 0.02
        // Mismo lugar con nombre conocido: no hace falta volver a preguntarle al geocodificador.
        val name = previous?.place?.takeIf { samePlace }
            ?: LocationProvider.placeName(context, coordinates)
        return Target(CURRENT_PLACE_ID, name, coordinates.latitude, coordinates.longitude)
    }

    suspend fun refreshPlace(place: Place): Result<WeatherSnapshot> = runCatchingCancellable {
        fetchAll(listOf(Target(place.id, place.shownName, place.latitude, place.longitude))).first()
    }

    /** Pronóstico, aire y alertas de varios lugares a la vez; guarda cada uno en su archivo. */
    private suspend fun fetchAll(targets: List<Target>): List<WeatherSnapshot> = withContext(Dispatchers.IO) {
        val points = targets.map { it.latitude to it.longitude }
        val (forecasts, airs, alerts) = coroutineScope {
            val forecasts = async { OpenMeteo.forecasts(points) }
            val airs = async { airQualities(targets) }
            val alerts = async {
                val inArgentina = targets.filter { Geo.isInArgentina(it.latitude, it.longitude) }
                // Si el SMN falla, se mantienen las alertas de la última vez: si no, una alerta
                // vigente "terminaría" y volvería a sonar como nueva en la siguiente corrida.
                val cap = if (inArgentina.isEmpty()) emptyList() else capAlerts()
                targets.map { t ->
                    when {
                        !Geo.isInArgentina(t.latitude, t.longitude) -> emptyList()
                        cap != null -> cap.filter { it.covers(t.latitude, t.longitude) }.map { it.alert }
                        else -> cached(t.id)?.alerts.orEmpty().filterNot { it.isExpired() }
                    }
                }
            }
            Triple(forecasts.await(), airs.await(), alerts.await())
        }
        val now = System.currentTimeMillis()
        targets.mapIndexedNotNull { i, t ->
            val snapshot = OpenMeteo.toSnapshot(
                forecast = forecasts[i],
                placeId = t.id,
                place = t.name,
                latitude = t.latitude,
                longitude = t.longitude,
                airQuality = airs.getOrNull(i),
                fetchedAt = now,
            ).copy(alerts = alerts[i])
            // Mientras bajaba, la ciudad pudo cambiar de apodo o quitarse: se guarda con el nombre
            // de ahora, y si ya no está, no se guarda (quedaría un pronóstico huérfano).
            val shown = if (t.id == CURRENT_PLACE_ID) snapshot else {
                val place = places().firstOrNull { it.id == t.id } ?: return@mapIndexedNotNull snapshot
                snapshot.copy(place = place.shownName)
            }
            writeSnapshot(t.id, shown)
            shown
        }
    }

    // --- Lo normal para la época y la lluvia caída ---

    // Las tarjetas lo piden al mostrarse (no va en el trabajo cada 15 minutos). En dos paneles lo
    // piden las dos mitades a la vez: con el candado, la segunda usa lo que bajó la primera.
    private val pastLock = Mutex()
    private val climateDir = File(context.filesDir, "clima")
    /** Días de otros años ya leídos del disco, por lugar redondeado. */
    private val climateMemory = java.util.concurrent.ConcurrentHashMap<String, ClimateCache>()
    /** Cuándo falló el último pedido de cada cosa: sin red no se reintenta cada vez que se abre la tarjeta. */
    private val pastFailedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val weekRainMemory = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<ClimateDay>>>()

    /** Lo normal para estos días en el lugar del pronóstico y la lluvia de la última semana. */
    suspend fun pastWeather(snapshot: WeatherSnapshot): PastWeather = withContext(Dispatchers.IO) {
        val latitude = snapshot.latitude
        val longitude = snapshot.longitude
        val today = snapshot.nowAtPlace().toLocalDate()
        pastLock.withLock {
            coroutineScope {
                val days = async { runCatchingCancellable { climateDays(latitude, longitude, today) }.getOrNull() }
                val week = async { runCatchingCancellable { recentRain(latitude, longitude) }.getOrNull() }
                PastWeather(
                    normal = days.await()?.let { normalFor(it, today) },
                    weekRain = week.await()?.let { weekRain(it, today) },
                )
            }
        }
    }

    /**
     * Días de los últimos diez años alrededor de esta fecha. El promedio casi no cambia de un día
     * al otro: se guarda 30 días por lugar (redondeado a 0,1°, unos 11 km; el reanálisis tiene
     * celdas de unos 25 km), y solo los días que sirven para ese mes, no los diez años.
     */
    private suspend fun climateDays(latitude: Double, longitude: Double, today: java.time.LocalDate): List<ClimateDay>? {
        val lat = kotlin.math.round(latitude * 10) / 10
        val lon = kotlin.math.round(longitude * 10) / 10
        val key = String.format(java.util.Locale.US, "%.1f_%.1f", lat, lon)
        val file = File(climateDir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")
        val stored = climateMemory[key]
            ?: runCatching { json.decodeFromString(ClimateCache.serializer(), file.readText()) }.getOrNull()?.also { climateMemory[key] = it }
        val storedOn = stored?.let { runCatching { java.time.LocalDate.parse(it.fetchedOn) }.getOrNull() }
        if (stored != null && storedOn != null && !today.isBefore(storedOn) && today.isBefore(storedOn.plusDays(30))) return stored.days
        if (System.currentTimeMillis() - (pastFailedAt["clima:$key"] ?: 0L) < 15 * 60_000L) return stored?.days
        val fetched = try {
            // Desde la ventana de hace diez años hasta la del año pasado (más el margen de la
            // caché): el archivo llega con días de atraso, así que este año no se pide.
            OpenMeteo.archiveDays(lat, lon, today.minusYears(10).minusDays(8), today.minusYears(1).plusDays(38))
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            pastFailedAt["clima:$key"] = System.currentTimeMillis()
            // Mejor la de antes, aunque sea vieja: si ya no cubre estos días, el promedio queda sin datos.
            return stored?.days
        }
        val cache = ClimateCache(lat, lon, today.toString(), keepForCache(fetched, today))
        climateMemory[key] = cache
        runCatching {
            climateDir.mkdirs()
            val temp = File(climateDir, file.name + ".tmp")
            temp.writeText(json.encodeToString(ClimateCache.serializer(), cache))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
        return cache.days
    }

    /** Lluvia de los últimos 7 días; se guarda una hora en memoria (es un pedido chico). */
    private suspend fun recentRain(latitude: Double, longitude: Double): List<ClimateDay>? {
        val key = String.format(java.util.Locale.US, "%.2f_%.2f", latitude, longitude)
        val now = System.currentTimeMillis()
        weekRainMemory[key]?.let { (at, days) -> if (now - at < 60 * 60_000L) return days }
        if (now - (pastFailedAt["lluvia:$key"] ?: 0L) < 15 * 60_000L) return weekRainMemory[key]?.second
        return try {
            OpenMeteo.pastWeekRain(latitude, longitude).also { weekRainMemory[key] = now to it }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            pastFailedAt["lluvia:$key"] = now
            weekRainMemory[key]?.second
        }
    }

    // --- Ajustes ---

    var rainAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_RAIN_ALERTS, false)
        set(value) = prefs.edit().putBoolean(KEY_RAIN_ALERTS, value).apply()

    val rainAlertsFlow: Flow<Boolean> = prefsFlow(KEY_RAIN_ALERTS) { rainAlertsEnabled }

    var smnAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMN_ALERTS, false)
        set(value) = prefs.edit().putBoolean(KEY_SMN_ALERTS, value).apply()

    val smnAlertsFlow: Flow<Boolean> = prefsFlow(KEY_SMN_ALERTS) { smnAlertsEnabled }

    var statusTemperatureEnabled: Boolean
        get() = prefs.getBoolean(KEY_STATUS_TEMP, false)
        set(value) = prefs.edit().putBoolean(KEY_STATUS_TEMP, value).apply()

    val statusTemperatureFlow: Flow<Boolean> = prefsFlow(KEY_STATUS_TEMP) { statusTemperatureEnabled }

    /** Ids de alertas ya notificadas. */
    var notifiedAlerts: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFIED_ALERTS, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_NOTIFIED_ALERTS, value).apply()

    /** Momento hasta el que no se vuelve a avisar de lluvia, para no repetir la notificación. */
    var rainNotifiedUntil: Long
        get() = prefs.getLong(KEY_RAIN_NOTIFIED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_RAIN_NOTIFIED_UNTIL, value).apply()

    // --- Unidades ---

    private fun readUnits() = UnitSettings(
        temperature = enumOr(prefs.getString(KEY_UNIT_TEMP, null), TemperatureUnit.CELSIUS),
        wind = enumOr(prefs.getString(KEY_UNIT_WIND, null), WindUnit.KMH),
        pressure = enumOr(prefs.getString(KEY_UNIT_PRESSURE, null), PressureUnit.HPA),
        use24h = prefs.getBoolean(KEY_USE_24H, true),
    )

    var units: UnitSettings
        get() = Units.current
        set(value) {
            Units.current = value
            prefs.edit()
                .putString(KEY_UNIT_TEMP, value.temperature.name)
                .putString(KEY_UNIT_WIND, value.wind.name)
                .putString(KEY_UNIT_PRESSURE, value.pressure.name)
                .putBoolean(KEY_USE_24H, value.use24h)
                .apply()
        }

    val unitsFlow: Flow<UnitSettings> = prefsFlow(KEY_USE_24H, KEY_UNIT_TEMP, KEY_UNIT_WIND, KEY_UNIT_PRESSURE) { readUnits() }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private fun <T> prefsFlow(vararg keys: String, read: () -> T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed in keys) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        private const val KEY_PLACES = "places"
        private const val KEY_RAIN_ALERTS = "rain_alerts"
        private const val KEY_RAIN_NOTIFIED_UNTIL = "rain_notified_until"
        private const val KEY_SMN_ALERTS = "smn_alerts"
        private const val KEY_STATUS_TEMP = "status_temperature"
        private const val KEY_NOTIFIED_ALERTS = "notified_alerts"
        private const val KEY_UNIT_TEMP = "unit_temp"
        private const val KEY_UNIT_WIND = "unit_wind"
        private const val KEY_UNIT_PRESSURE = "unit_pressure"
        private const val KEY_USE_24H = "use_24h"
        private const val KEY_CITIES_AT = "ciudades_actualizadas"

        private fun snapshotKey(placeId: String) = "snapshot:$placeId"

        @Volatile
        private var instance: WeatherRepository? = null

        fun get(context: Context): WeatherRepository =
            instance ?: synchronized(this) {
                instance ?: WeatherRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** Como runCatching, pero sin tragarse la cancelación: si no, un trabajo cancelado seguiría de largo. */
private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancel: kotlinx.coroutines.CancellationException) {
    throw cancel
} catch (error: Throwable) {
    Result.failure(error)
}
