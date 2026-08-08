package com.thelightphone.cdta

import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.thelightphone.cdta.GtfsRepository
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

sealed class CdtaScreenMode {
    data class Routes(val routes: List<RouteDisplay>, val isLoading: Boolean = false) : CdtaScreenMode()
    data class Stops(val stops: List<Stop>, val isLoading: Boolean = false) : CdtaScreenMode()
    data class Search(val query: String, val results: List<SearchResult>) : CdtaScreenMode()
    data class SearchInput(val currentQuery: String) : CdtaScreenMode()
    data class StopDetail(val stopDisplay: StopDisplay) : CdtaScreenMode()
    data class StopRouteSchedule(val stop: Stop, val routeShortName: String, val nextArrivalFormatted: String, val direction: String) : CdtaScreenMode()
    data class RouteDetail(val route: RouteDisplay, val busMarkers: Map<Int, String> = emptyMap()) : CdtaScreenMode()
    data class TimeSelector(val customTime: TimeConfig?, val returnMode: CdtaScreenMode? = null) : CdtaScreenMode()
}

sealed class SearchResult {
    data class Route(val route: RouteDisplay) : SearchResult()
    data class Stop(val stop: com.thelightphone.cdta.Stop) : SearchResult()
}

data class CdtaUiState(
    val mode: CdtaScreenMode = CdtaScreenMode.Routes(emptyList(), true),
    val errorModal: String? = null,
    val customTime: TimeConfig? = null,
)

/** Custom time override for schedule preview. Null = use current system time. */
data class TimeConfig(val hours: Int, val minutes: Int)

/** Sorts route displays numerically by short name (1, 2, 10, 100, 114...). */
private fun List<RouteDisplay>.sortedForRoutes(): List<RouteDisplay> =
    sortedBy { it.shortName.toIntOrNull() ?: Int.MAX_VALUE }

/** Builds a RouteDisplay (with stop names + ids) from GTFS route data. */
private fun com.thelightphone.cdta.gtfs.GtfsData.buildRouteDisplay(route: com.thelightphone.cdta.gtfs.GtfsRoute): RouteDisplay {
    val stopIds = routeTrips[route.shortName]?.firstOrNull()?.let { tripStops[it] } ?: emptyList()
    val stopNames = stopIds.mapNotNull { stops[it]?.name }
    return RouteDisplay(route, stopIds.size, stopNames, stopIds)
}

private val MIN_LOADING_DISPLAY = 1.seconds

class CdtaViewModel(
    private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
    private val gtfsRepo: GtfsRepository,
    lightContext: com.thelightphone.sdk.SealedLightContext,
) : LightViewModel<Unit>() {

    private val lightContext = lightContext

    private val _uiState = MutableStateFlow(CdtaUiState())
    val uiState: StateFlow<CdtaUiState> = _uiState.asStateFlow()

    private var cachedStops: List<Stop> = emptyList()
    private var stopRoutesMap: Map<String, List<String>> = emptyMap()

    private val loadingStartedAt = Clock.System.now()

    private val apiExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        val message = throwable.message ?: "Unexpected error"
        viewModelScope.launch(Dispatchers.Main) {
            updateState { it.copy(errorModal = message) }
        }
    }

    init {
        // Trigger GTFS load on background thread, but defer slightly to let UI render
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            refreshData()
            // Enforce minimum loading display (prevents jarring flash on cached data)
            val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - loadingStartedAt)
            if (remaining.isPositive()) delay(remaining)
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        // Restore DataStore-persisted state: last-viewed stop and API key
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val prefs = dataStore.data.first()
            val lastStopId = prefs[CdtaPreferences.LAST_STOP_ID]
            if (!lastStopId.isNullOrEmpty()) {
                val stop = cachedStops.find { it.stopId == lastStopId }
                if (stop != null) {
                    viewModelScope.launch(Dispatchers.Main) {
                        selectStop(stop)
                    }
                }
            }
        }
    }

    private suspend fun updateState(transform: (CdtaUiState) -> CdtaUiState) {
        _uiState.value = transform(_uiState.value)
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(errorModal = message)
    }

    fun dismissErrorModal() {
        _uiState.value = _uiState.value.copy(errorModal = null)
    }

    /**
     * Loads static schedule data from the bundled GTFS feed.
     * No API key required — works offline. Called when the routes tab is first shown.
     */
    fun refreshData() {
        if (gtfsRepo.data.value != null) return
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            try {
                val assetBytes = lightContext.readAsset("cdta_gtfs.json")
                gtfsRepo.parseAsset(assetBytes)
                loadFromGtfs(gtfsRepo.data.value ?: return@launch)
            } catch (e: Exception) {
                showError("Failed to load schedule data: ${e.message}")
            }
        }
    }

    private suspend fun loadFromGtfs(data: com.thelightphone.cdta.gtfs.GtfsData) {
        // Build RouteDisplay list from GTFS routes
        val routeDisplays = data.routes.values.map { route ->
            data.buildRouteDisplay(route)
        }.sortedForRoutes()

        // Build stop-to-routes mapping from GTFS data:
        // For each route, iterate its trips' stops and record which routes serve each stop
        val stopRoutesMap = mutableMapOf<String, MutableSet<String>>()
        for ((shortName, tripIds) in data.routeTrips) {
            for (tripId in tripIds) {
                val stopIds = data.tripStops[tripId] ?: continue
                for (stopId in stopIds) {
                    stopRoutesMap.getOrPut(stopId) { mutableSetOf() }.add(shortName)
                }
            }
        }
        this.stopRoutesMap = stopRoutesMap.mapValues { (_, set) -> set.toList().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE } }

        // Build Stop list from GTFS stops, with routes populated from the mapping
        cachedStops = data.stops.values.map { stop ->
            Stop(
                stopId = stop.id,
                stopName = stop.name,
                stopLat = stop.lat,
                stopLon = stop.lon,
                routes = this.stopRoutesMap[stop.id] ?: emptyList(),
            )
        }.sortedBy { it.stopName }

        // Show routes screen
        updateState { it.copy(mode = CdtaScreenMode.Routes(routeDisplays), errorModal = null) }
    }

    fun showRoutes() {
        val data = gtfsRepo.data.value
        if (data != null) {
            val routeDisplays = data.routes.values.map { route ->
                data.buildRouteDisplay(route)
            }.sortedForRoutes()

            _uiState.value = _uiState.value.copy(mode = CdtaScreenMode.Routes(routeDisplays))
        } else {
            // GTFS not loaded yet — show loading and trigger async load
            _uiState.value = _uiState.value.copy(mode = CdtaScreenMode.Routes(emptyList(), isLoading = true))
            refreshData()
        }
    }

    fun showStops() {
        _uiState.value = _uiState.value.copy(
            mode = CdtaScreenMode.Stops(cachedStops, isLoading = false),
        )
    }

    /** Local search across GTFS routes and stops. */
    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _uiState.value = _uiState.value.copy(mode = CdtaScreenMode.Search("", emptyList()))
            return
        }
        val data = gtfsRepo.data.value
        if (data == null) {
            _uiState.value = _uiState.value.copy(mode = CdtaScreenMode.Search("", emptyList()))
            return
        }

        val ql = q.lowercase()

        val routeMatches = data.routes.values.filter {
            it.shortName.contains(ql, ignoreCase = true) ||
                it.longName.contains(ql, ignoreCase = true)
        }.map { route ->
            SearchResult.Route(data.buildRouteDisplay(route))
        }.sortedBy { it.route.shortName.toIntOrNull() ?: Int.MAX_VALUE }

        val stopMatches = data.stops.values.filter {
            it.name.contains(ql, ignoreCase = true) || it.id.contains(ql, ignoreCase = true)
        }.sortedBy { it.name }.map { stop ->
            SearchResult.Stop(
                Stop(
                    stopId = stop.id,
                    stopName = stop.name,
                    stopLat = stop.lat,
                    stopLon = stop.lon,
                    routes = this.stopRoutesMap[stop.id] ?: emptyList(),
                )
            )
        }

        val results = mutableListOf<SearchResult>()
        routeMatches.take(3).forEach { results.add(it) }
        stopMatches.take(5).forEach { results.add(it) }

        _uiState.value = _uiState.value.copy(
            mode = CdtaScreenMode.Search(q, results),
        )
    }

    /** Called when the user submits the search input via LightTextInputEditor. */
    fun submitSearch(query: CharSequence) {
        search(query.toString())
    }

    /** Called when the user cancels the search input. */
    fun cancelSearch() {
        _uiState.value = _uiState.value.copy(mode = CdtaScreenMode.Search("", emptyList()))
    }

    /** Returns the effective time in seconds past midnight — uses custom time if set, otherwise current system time. */
    private fun getEffectiveTimeSec(): Int {
        val custom = _uiState.value.customTime
        if (custom != null) {
            return custom.hours * 3600 + custom.minutes * 60
        }
        val now = System.currentTimeMillis()
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val m = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
        val s = java.util.Calendar.getInstance().get(java.util.Calendar.SECOND)
        return h * 3600 + m * 60 + s
    }

    fun selectRoute(route: RouteDisplay) {
        val data = gtfsRepo.data.value
        val busMarkers = mutableMapOf<Int, String>()
        if (data != null && route.stopNames.isNotEmpty()) {
            val currentTime = getEffectiveTimeSec()
            val busPositions = gtfsRepo.findActiveTripsForRoute(data, route.shortName, currentTime)
            for (pos in busPositions) {
                val arrow = when (pos.direction) {
                    Direction.UP -> "↑"
                    Direction.DOWN -> "↓"
                }
                val existing = busMarkers[pos.displayStopIndex]
                busMarkers[pos.displayStopIndex] = if (existing != null) {
                    if (existing.contains(arrow)) {
                        existing
                    } else {
                        "$existing$arrow"
                    }
                } else {
                    arrow
                }
            }
        }
        _uiState.value = _uiState.value.copy(
            mode = CdtaScreenMode.RouteDetail(route, busMarkers)
        )
    }

    /** Re-computes star positions for the current RouteDetail with current time. */
    fun refreshSchedule(route: RouteDisplay) {
        selectRoute(route)
    }

    /** Shows the next scheduled arrival of a route at a stop (StopRouteSchedule screen). */
    fun selectStopRoute(stop: Stop, routeShortName: String) {
        val data = gtfsRepo.data.value
        if (data == null) {
            _uiState.value = _uiState.value.copy(
                mode = CdtaScreenMode.StopRouteSchedule(stop, routeShortName, "No schedule data", "")
            )
            return
        }
        val currentTime = getEffectiveTimeSec()
        val nextArrival = gtfsRepo.getNextArrivalAtStop(data, stop.stopId, routeShortName, currentTime)
        val direction = gtfsRepo.getDirectionForRoute(data, routeShortName, stop.stopId, currentTime)
        val formatted = nextArrival?.let { formatTime(it) } ?: "No more today"
        _uiState.value = _uiState.value.copy(
            mode = CdtaScreenMode.StopRouteSchedule(
                stop = stop,
                routeShortName = routeShortName,
                nextArrivalFormatted = formatted,
                direction = direction,
            )
        )
    }

    /** Clears custom time and recomputes the current screen with the current system time. */
    fun refreshToCurrentTime() {
        val customTime = _uiState.value.customTime
        if (customTime != null) {
            _uiState.value = _uiState.value.copy(customTime = null)
        }
        val mode = _uiState.value.mode
        when (mode) {
            is CdtaScreenMode.RouteDetail -> refreshSchedule(mode.route)
            is CdtaScreenMode.StopRouteSchedule -> selectStopRoute(mode.stop, mode.routeShortName)
            is CdtaScreenMode.TimeSelector -> {
                val returnMode = mode.returnMode
                if (returnMode is CdtaScreenMode.RouteDetail) {
                    refreshSchedule(returnMode.route)
                } else if (returnMode is CdtaScreenMode.StopRouteSchedule) {
                    selectStopRoute(returnMode.stop, returnMode.routeShortName)
                } else {
                    showRoutes()
                }
            }
            else -> {}
        }
    }

    fun openTimeSelector() {
        val currentMode = _uiState.value.mode
        _uiState.value = _uiState.value.copy(
            mode = CdtaScreenMode.TimeSelector(_uiState.value.customTime, currentMode),
        )
    }

    fun setCustomTime(hours: Int, minutes: Int) {
        _uiState.value = _uiState.value.copy(customTime = TimeConfig(hours, minutes))
    }

    fun clearCustomTime() {
        _uiState.value = _uiState.value.copy(customTime = null)
    }

    fun selectStop(stop: Stop) {
        _uiState.value = _uiState.value.copy(
            mode = CdtaScreenMode.StopDetail(StopDisplay(stop, emptyList())),
        )
    }

    private fun formatTime(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val timeOfDay = if (h >= 12) "PM" else "AM"
        val displayHour = if (h > 12) h - 12 else if (h == 0) 12 else h
        return String.format("%d:%02d %s", displayHour, m, timeOfDay)
    }

    /** Navigates to a stop's detail page by ID (e.g. tapping a stop from a route's stop list). */
    fun selectStopById(stopId: String) {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val stop = cachedStops.find { it.stopId == stopId }
            if (stop != null) {
                updateState {
                    it.copy(mode = CdtaScreenMode.StopDetail(StopDisplay(stop, emptyList())))
                }
                dataStore.edit { prefs ->
                    prefs[CdtaPreferences.LAST_STOP_ID] = stopId
                    prefs[CdtaPreferences.LAST_STOP_NAME] = stop.displayName
                }
            }
        }
    }

    sealed class HomeView {
        object Routes : HomeView()
        object Stops : HomeView()
        object Search : HomeView()
    }

    private var homeView: HomeView = HomeView.Routes

    fun setHomeView(view: HomeView) {
        homeView = view
        when (view) {
            is HomeView.Routes -> showRoutes()
            is HomeView.Stops -> showStops()
            is HomeView.Search -> {
                val currentQuery = (_uiState.value.mode as? CdtaScreenMode.Search)?.query ?: ""
                _uiState.value = _uiState.value.copy(mode = CdtaScreenMode.SearchInput(currentQuery))
            }
        }
    }
}
