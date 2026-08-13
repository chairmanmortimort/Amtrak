package com.thelightphone.amtrak

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightHapticFeedback
import com.thelightphone.sdk.ui.LightThemeController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal object AmtrakPreferences {
    val TRAINS_JSON = stringPreferencesKey("amtrak_trains_json")
    val STATIONS_JSON = stringPreferencesKey("amtrak_stations_json")
    val STATION_KEYS_JSON = stringPreferencesKey("amtrak_station_keys_json")
    val LAST_FETCH = stringPreferencesKey("amtrak_last_fetch")
    val HAPTICS_ENABLED = booleanPreferencesKey("amtrak_haptics_enabled")
    val COLOR_INVERTED = booleanPreferencesKey("amtrak_color_inverted")
}

private val JSON = Json { ignoreUnknownKeys = true }
private val MIN_LOADING_DISPLAY = 1.seconds
private val CACHE_MAX_AGE = 5 * 60 * 1000L // 5 minutes in millis

@Serializable
internal data class CachedTrain(
    val trainId: String,
    val trainData: TrainData,
)

class AmtrakViewModel(
    private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
) : LightViewModel<Unit>() {
    private val api = AmtrakApi()

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private val _trainDetailState = MutableStateFlow<TrainDetailState>(TrainDetailState.Loading)
    val trainDetailState: StateFlow<TrainDetailState> = _trainDetailState.asStateFlow()

    private val _stationDetailState = MutableStateFlow<StationDetailState>(StationDetailState.Loading)
    val stationDetailState: StateFlow<StationDetailState> = _stationDetailState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _errorModal = MutableStateFlow<String?>(null)
    val errorModal: StateFlow<String?> = _errorModal.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _lastTrains = mutableListOf<TrainDisplay>()
    private val _lastStations = mutableMapOf<String, Station>()

    private val stationKeys = mutableMapOf<String, String>()
    private val allStations = mutableMapOf<String, Station>()

    private var skipRefreshOnNextScreenShow = false

    private val apiExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        val message = throwable.message ?: "Unexpected error"
        viewModelScope.launch(Dispatchers.Main) {
            _errorModal.value = message
        }
    }

    init {
        loadSettings()
        loadFromCacheOrFetch()
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        AmtrakRepository.clearSelection()
        if (skipRefreshOnNextScreenShow) {
            skipRefreshOnNextScreenShow = false
            return
        }
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            refreshWhenStale()
        }
    }

    fun dismissError() {
        _errorModal.value = null
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val hapticsEnabled = prefs[AmtrakPreferences.HAPTICS_ENABLED] ?: true
            val colorInverted = prefs[AmtrakPreferences.COLOR_INVERTED] ?: false
            withContext(Dispatchers.Main) {
                _settingsState.value = SettingsState(
                    hapticsEnabled = hapticsEnabled,
                    colorInverted = colorInverted,
                )
            }
            // Apply color inversion immediately
            applyColorInversion(colorInverted)
        }
    }

    fun toggleHaptics() {
        val next = !_settingsState.value.hapticsEnabled
        _settingsState.value = _settingsState.value.copy(hapticsEnabled = next)
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[AmtrakPreferences.HAPTICS_ENABLED] = next
            }
        }
    }

    fun toggleColorInverted() {
        val next = !_settingsState.value.colorInverted
        _settingsState.value = _settingsState.value.copy(colorInverted = next)
        applyColorInversion(next)
        viewModelScope.launch(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[AmtrakPreferences.COLOR_INVERTED] = next
            }
        }
    }

    private fun applyColorInversion(inverted: Boolean) {
        if (inverted) {
            LightThemeController.setLightTheme()
        } else {
            LightThemeController.setDarkTheme()
        }
    }

    private fun loadFromCacheOrFetch() {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val prefs = dataStore.data.first()
            val trainsJson = prefs[AmtrakPreferences.TRAINS_JSON]
            val stationsJson = prefs[AmtrakPreferences.STATIONS_JSON]
            val stationKeysJson = prefs[AmtrakPreferences.STATION_KEYS_JSON]
            val lastFetchStr = prefs[AmtrakPreferences.LAST_FETCH]
            val lastFetch = lastFetchStr?.toLongOrNull() ?: 0L
            val now = Clock.System.now().toEpochMilliseconds()

            val cacheIsFresh = trainsJson != null && stationsJson != null && stationKeysJson != null &&
                (now - lastFetch) < CACHE_MAX_AGE

            if (cacheIsFresh) {
                try {
                    val cachedTrains = JSON.decodeFromString<List<CachedTrain>>(trainsJson)
                    val stations = JSON.decodeFromString<Map<String, Station>>(stationsJson)
                    val keys = JSON.decodeFromString<Map<String, String>>(stationKeysJson)
                    withContext(Dispatchers.Main) {
                        _lastTrains.clear()
                        _lastTrains.addAll(cachedTrains.map { it.trainData.toDisplay(it.trainId) }.sortedBy { it.trainNum })
                        _lastStations.clear()
                        _lastStations.putAll(stations)
                        stationKeys.clear()
                        stationKeys.putAll(keys)
                        allStations.clear()
                        allStations.putAll(stations)
                        _homeState.value = HomeState.Trains(_lastTrains)
                    }
                } catch (e: Exception) {
                    // Cache was corrupt — fall through to network fetch
                    refreshFromNetwork(forceLoading = true)
                }
            } else {
                refreshFromNetwork(forceLoading = true)
            }
        }
    }

    private suspend fun refreshWhenStale() {
        val prefs = dataStore.data.first()
        val lastFetchStr = prefs[AmtrakPreferences.LAST_FETCH]
        val lastFetch = lastFetchStr?.toLongOrNull() ?: 0L
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastFetch < CACHE_MAX_AGE) return
        refreshFromNetwork(forceLoading = false)
    }

    private suspend fun refreshFromNetwork(forceLoading: Boolean) {
        val loadingStartedAt = Clock.System.now()
        if (forceLoading) {
            _homeState.value = HomeState.Loading
        }
        try {
            val stationsResult = api.fetchAllStations()
            allStations.clear()
            stationsResult.getOrNull()?.let { allStations.putAll(it) }
            stationKeys.clear()
            for ((code, station) in allStations) {
                stationKeys[code] = station.name
            }
            val trainsResult = api.fetchAllTrains()
            trainsResult.fold(
                onSuccess = { trainPairs ->
                    _lastTrains.clear()
                    _lastTrains.addAll(trainPairs.map { (id, td) -> td.toDisplay(id) }.sortedBy { it.trainNum })
                    _lastStations.clear()
                    _lastStations.putAll(allStations)
                    _homeState.value = HomeState.Trains(_lastTrains)
                    // Cache to DataStore
                    val cachedTrains = trainPairs.map { (id, td) -> CachedTrain(id, td) }
                    dataStore.edit { prefs ->
                        prefs[AmtrakPreferences.TRAINS_JSON] = JSON.encodeToString(cachedTrains)
                        prefs[AmtrakPreferences.STATIONS_JSON] = JSON.encodeToString(allStations)
                        prefs[AmtrakPreferences.STATION_KEYS_JSON] = JSON.encodeToString(stationKeys)
                        prefs[AmtrakPreferences.LAST_FETCH] = Clock.System.now().toEpochMilliseconds().toString()
                    }
                },
                onFailure = { error ->
                    // Fall back to cached data on network failure
                    val cachedTrainsJson = dataStore.data.first()[AmtrakPreferences.TRAINS_JSON]
                    if (!cachedTrainsJson.isNullOrEmpty()) {
                        try {
                            val cached = JSON.decodeFromString<List<CachedTrain>>(cachedTrainsJson)
                            _lastTrains.clear()
                            _lastTrains.addAll(cached.map { it.trainData.toDisplay(it.trainId) }.sortedBy { it.trainNum })
                            _homeState.value = HomeState.Trains(_lastTrains)
                        } catch (e: Exception) {
                            _homeState.value = HomeState.Error(error.message ?: "Failed to load trains")
                        }
                    } else {
                        _homeState.value = HomeState.Error(error.message ?: "Failed to load trains")
                    }
                },
            )
            // Enforce minimum loading display (like Weather tool's awaitMinimumLoading)
            val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - loadingStartedAt)
            if (remaining.isPositive()) delay(remaining)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _homeState.value = HomeState.Error(e.message ?: "Failed to load trains")
        }
    }

    private suspend fun withMain(block: suspend () -> Unit) {
        withContext(Dispatchers.Main) { block() }
    }

    fun loadHome() {
        refreshFromNetworkSilently()
    }

    fun loadTrains() {
        refreshFromNetworkSilently()
    }

    private fun refreshFromNetworkSilently() {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            refreshFromNetwork(forceLoading = true)
        }
    }

    /** Local search across loaded trains and stations (no extra API call). */
    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _searchState.value = SearchState.Idle
            return
        }
        val ql = q.lowercase()
        val trains = _lastTrains.filter { t ->
            t.routeName.startsWith(ql, ignoreCase = true) ||
                t.origin.startsWith(ql, ignoreCase = true) ||
                t.destination.startsWith(ql, ignoreCase = true) ||
                t.trainNum.toString().startsWith(ql) ||
                t.currentStop.startsWith(ql, ignoreCase = true)
        }
        val stations = _lastStations.values.filter { s ->
            s.name.startsWith(ql, ignoreCase = true) ||
                s.code.startsWith(ql, ignoreCase = true) ||
                s.city.startsWith(ql, ignoreCase = true) ||
                s.state.startsWith(ql, ignoreCase = true)
        }
        _searchState.value = SearchState.Results(q, trains, stations)
    }

    fun clearSearch() {
        _searchState.value = SearchState.Idle
    }

    fun loadStations() {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val result = api.fetchAllStations()
            result.fold(
                onSuccess = { stations ->
                    withMain {
                        _lastStations.clear()
                        _lastStations.putAll(stations)
                        _homeState.value = HomeState.Stations(
                            stations.values
                                .filter { s -> s.trains.any { !it.startsWith("v", ignoreCase = true) } }
                                .sortedBy { it.name }
                        )
                    }
                },
                onFailure = { error ->
                    withMain {
                        _homeState.value = HomeState.Error(error.message ?: "Failed to load stations")
                    }
                },
            )
        }
    }

    fun loadTrainDetail(trainId: String) {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            _trainDetailState.value = TrainDetailState.Loading
            val result = api.fetchTrain(trainId)
            result.fold(
                onSuccess = { trains ->
                    val train = trains.firstOrNull()
                    if (train != null) {
                        val nowMs = Clock.System.now().toEpochMilliseconds()
                    val stops = train.stations.map { st ->
                        val reached = st.arr != null || st.dep != null ||
                            st.status.contains("Departed", ignoreCase = true) ||
                            st.status.contains("Arrived", ignoreCase = true) ||
                            (st.schDep != null && st.schDep.toEpochMilliseconds() < nowMs) ||
                            (st.schArr != null && st.schArr.toEpochMilliseconds() < nowMs)
                        val stopDelay = buildDelayString(
                            schArr = st.schArr,
                            arr = st.arr,
                            schDep = st.schDep,
                            dep = st.dep,
                            trainState = "",
                            comment = st.postCmnt.ifEmpty { st.arrCmnt.ifEmpty { st.depCmnt } },
                        )
                        StopDisplay(
                            name = st.name.ifEmpty { stationKeys[st.code] ?: st.code },
                            code = st.code,
                            scheduledArrival = formatInstant(st.schArr),
                            estimatedArrival = formatInstant(st.arr),
                            status = st.status,
                            comment = st.postCmnt.ifEmpty { st.arrCmnt.ifEmpty { st.depCmnt } },
                            reached = reached,
                            delay = stopDelay,
                        )
                    }
                        val eventCode = train.eventCode
                        val currentStopIndex = if (eventCode.isNotEmpty()) {
                            train.stations.indexOfFirst { it.code == eventCode }.coerceAtLeast(-1)
                        } else {
                            -1
                        }
                        _trainDetailState.value = TrainDetailState.Train(
                            train = train.toDisplay(trainId),
                            stops = stops,
                            currentStopIndex = currentStopIndex,
                        )
                    } else {
                        _trainDetailState.value = TrainDetailState.Error("Train not found")
                    }
                },
                onFailure = { error ->
                    _trainDetailState.value = TrainDetailState.Error(error.message ?: "Failed to load train")
                },
            )
        }
    }

    fun loadStationDetail(stationCode: String) {
        _stationDetailState.value = StationDetailState.Loading
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val stationResult = api.fetchStationTrains(stationCode)
            val stationInfo = allStations[stationCode]
            stationResult.fold(
                onSuccess = { station ->
                    // station.trains is a list of train-ID strings (e.g. "19-30").
                    // Fetch each train's data and build StationTrain rows.
                    // Skip Via Rail IDs ("v"-prefixed) — they're Canadian services not fetchable here.
                    val now = Clock.System.now()
                    val nextTrains = station.trains
                        .filter { !it.startsWith("v", ignoreCase = true) }
                        .mapNotNull { id ->
                            api.fetchTrain(id).getOrNull()
                                ?.firstOrNull()
                                ?.toStationTrain(id, stationCode)
                        }
                        // Show only trains arriving/departing from now, sorted by arrival time
                        .filter { train ->
                            val arrival = train.arrivalTime
                            val departure = train.departureTime
                            arrival?.let { it >= now } == true ||
                                departure?.let { it >= now } == true
                        }
                        .sortedBy { train -> train.arrivalTime?.toEpochMilliseconds() ?: Long.MAX_VALUE }
                    // If the user arrived here from a train detail, surface that
                    // originating route even if it is not in the upcoming list.
                    // Use the already-loaded TrainDisplay (no extra network call).
                    @Suppress("UNCHECKED_CAST")
                    val fromDisplay = AmtrakRepository.selectedTrainDisplay.value
                        as? TrainDisplay
                    val fromTrain = fromDisplay?.let { d ->
                        val num = d.trainNum
                        StationTrain(
                            trainId = d.trainId,
                            trainNum = num,
                            routeName = d.routeName,
                            scheduledArrival = null,
                            estimatedArrival = null,
                            actualArrival = null,
                            scheduledDeparture = null,
                            estimatedDeparture = null,
                            actualDeparture = null,
                            status = "",
                        )
                    }
                    val displayTrains = if (fromTrain != null) {
                        listOf(fromTrain) + nextTrains.filter { it.trainId != fromTrain.trainId }
                    } else {
                        nextTrains
                    }
                    val stationDisplay = StationDisplay(
                        code = stationCode,
                        name = station.name.ifEmpty { stationInfo?.name ?: stationKeys[stationCode] ?: stationCode },
                        city = station.city.ifEmpty { stationInfo?.city ?: "" },
                        state = station.state.ifEmpty { stationInfo?.state ?: "" },
                        lat = station.lat,
                        lon = station.lon,
                        nextTrains = displayTrains,
                        fromTrain = fromTrain,
                    )
                    _stationDetailState.value = StationDetailState.Station(stationDisplay)
                },
                onFailure = { error ->
                    _stationDetailState.value = StationDetailState.Error(error.message ?: "Failed to load station")
                },
            )
        }
    }

    internal fun TrainData.toDisplay(trainId: String): TrainDisplay {
        val origin = stationKeys[origCode] ?: origCode
        val destination = stationKeys[destCode] ?: destCode
        val currentStop = stationKeys[eventCode] ?: eventCode
        val currentStation = stations.firstOrNull { it.code == eventCode }
        // Compute lateness from actual/estimated vs scheduled time. Prefer the
        // current stop; if it has no times, fall back to the first stop that does.
        val delayReference = currentStation ?: stations.firstOrNull { it.schArr != null || it.arr != null }
        val computedDelay = buildDelayString(
            schArr = delayReference?.schArr,
            arr = delayReference?.arr,
            schDep = delayReference?.schDep,
            dep = delayReference?.dep,
            trainState = trainState,
            comment = currentStation?.arrCmnt ?: currentStation?.depCmnt ?: "",
        )
        // Route start = first stop's scheduled-vs-actual departure; end = last stop's arrival.
        val firstStop = stations.firstOrNull()
        val lastStop = stations.lastOrNull()
        val routeStart = formatStopTime(firstStop?.schDep, firstStop?.dep)
        val routeEnd = formatStopTime(lastStop?.schArr, lastStop?.arr)
        return TrainDisplay(
            trainId = trainId,
            trainNum = trainNum.toIntOrNull() ?: trainId.toIntOrNull() ?: 0,
            routeName = routeName,
            origin = origin,
            destination = destination,
            currentStop = currentStop,
            status = statusMsg.ifEmpty { trainState },
            delay = computedDelay,
            isDisrupted = serviceDisruption || statusMsg.contains("DISRUPTION", ignoreCase = true),
            lat = lat,
            lon = lon,
            heading = heading,
            lastUpdated = updatedAt ?: lastValTS,
            routeStart = routeStart,
            routeEnd = routeEnd,
        )
    }

    private fun TrainData.toStationTrain(trainId: String, stationCode: String): StationTrain {
        val num = trainId.split("-").firstOrNull()?.toIntOrNull() ?: trainNum.toIntOrNull() ?: 0
        // Find the stop in this train's route that matches the station we're viewing
        val stop = stations.firstOrNull { it.code == stationCode }
        // Prefer actual times, else estimated, else scheduled.
        return StationTrain(
            trainId = trainId,
            trainNum = num,
            routeName = routeName,
            scheduledArrival = stop?.schArr,
            estimatedArrival = stop?.arr,
            actualArrival = stop?.arr,
            scheduledDeparture = stop?.schDep,
            estimatedDeparture = stop?.dep,
            actualDeparture = stop?.dep,
            status = stop?.status ?: "",
        )
    }

    private fun formatInstant(instant: Instant?): String {
        if (instant == null) return ""
        val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hh = ldt.hour.toString().padStart(2, '0')
        val mm = ldt.minute.toString().padStart(2, '0')
        return "$hh:$mm"
    }

    /**
     * Render a scheduled-vs-actual time for a route endpoint (e.g. route start/end).
     * Shows "HH:MM" (actual if present, else scheduled). If an actual time exists
     * and differs from scheduled, append the delta: late (+m) / early (-m).
     */
    private fun formatStopTime(scheduled: Instant?, actual: Instant?): String {
        val base = actual ?: scheduled ?: return ""
        val text = formatInstant(base)
        if (actual != null && scheduled != null) {
            val diffMin = ((actual.toEpochMilliseconds() - scheduled.toEpochMilliseconds()) / 60000).toInt()
            if (diffMin != 0) {
                return "$text (${if (diffMin > 0) "+" else ""}$diffMin)"
            }
        }
        return text
    }

    /**
     * Build a human-readable lateness string from scheduled vs actual/estimated times.
     * Prefers a numeric diff (actual - scheduled) so a DELAYED train never reads
     * "On time" just because the feed omitted a comment.
     */
    private fun buildDelayString(
        schArr: Instant?,
        arr: Instant?,
        schDep: Instant?,
        dep: Instant?,
        trainState: String,
        comment: String,
    ): String {
        // Prefer arrival delta; fall back to departure delta.
        val (scheduled, actual) = if (schArr != null || arr != null) {
            schArr to arr
        } else {
            schDep to dep
        }
        if (scheduled != null && actual != null) {
            val diffMin = ((actual.toEpochMilliseconds() - scheduled.toEpochMilliseconds()) / 60000).toInt()
            return when {
                diffMin > 0 -> "Late $diffMin min"
                diffMin < 0 -> "Early ${(-diffMin)} min"
                else -> "On time"
            }
        }
        // No numeric times available: trust the feed's comment, then trainState.
        if (comment.isNotEmpty()) return comment
        return when (trainState.equals("DELAYED", ignoreCase = true)) {
            true -> "Delayed"
            else -> if (trainState.isNotEmpty()) trainState else "On time"
        }
    }

    fun formatArrival(train: StationTrain): String =
        formatInstant(train.arrivalTime)

    sealed class HomeState {
        object Loading : HomeState()
        data class Trains(val trains: List<TrainDisplay>) : HomeState()
        data class Stations(val stations: List<Station>) : HomeState()
        data class Error(val message: String) : HomeState()
    }

    sealed class TrainDetailState {
        object Loading : TrainDetailState()
        data class Train(
            val train: TrainDisplay,
            val stops: List<StopDisplay> = emptyList(),
            val currentStopIndex: Int = -1,
        ) : TrainDetailState()
        data class Error(val message: String) : TrainDetailState()
    }

    data class StopDisplay(
        val name: String,
        val code: String,
        val scheduledArrival: String = "",
        val estimatedArrival: String = "",
        val status: String = "",
        val comment: String = "",
        val reached: Boolean = false,
        val delay: String = "",
    )

    sealed class StationDetailState {
        object Loading : StationDetailState()
        data class Station(val station: StationDisplay) : StationDetailState()
        data class Error(val message: String) : StationDetailState()
    }

    sealed class SearchState {
        object Idle : SearchState()
        data class Results(
            val query: String,
            val trains: List<TrainDisplay>,
            val stations: List<Station>,
        ) : SearchState()
    }

    data class SettingsState(
        val hapticsEnabled: Boolean = true,
        val colorInverted: Boolean = false,
    )
}
