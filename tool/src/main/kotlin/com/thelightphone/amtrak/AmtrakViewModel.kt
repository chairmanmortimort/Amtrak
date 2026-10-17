package com.thelightphone.amtrak

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class AmtrakViewModel : LightViewModel<Unit>() {
    private val api = AmtrakApi()

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private val _trainDetailState = MutableStateFlow<TrainDetailState>(TrainDetailState.Loading)
    val trainDetailState: StateFlow<TrainDetailState> = _trainDetailState.asStateFlow()

    private val _stationDetailState = MutableStateFlow<StationDetailState>(StationDetailState.Loading)
    val stationDetailState: StateFlow<StationDetailState> = _stationDetailState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _lastTrains = mutableListOf<TrainDisplay>()
    private val _lastStations = mutableMapOf<String, Station>()

    private val stationKeys = mutableMapOf<String, String>()
    private val allStations = mutableMapOf<String, Station>()

    init {
        loadHome()
    }

    fun loadHome() {
        viewModelScope.launch {
            _homeState.value = HomeState.Loading
            val stationsResult = api.fetchAllStations()
            allStations.clear()
            stationsResult.getOrNull()?.let { allStations.putAll(it) }
            // Derive code->name keys from the full station list (v2 dropped the /stations/keys wrapper).
            stationKeys.clear()
            for ((code, station) in allStations) {
                stationKeys[code] = station.name
            }
            val trainsResult = api.fetchAllTrains()
            trainsResult.fold(
                onSuccess = { trains ->
                    _lastTrains.clear()
                    _lastTrains.addAll(trains.map { (id, td) -> td.toDisplay(id) }.sortedBy { it.trainNum })
                    _lastStations.clear()
                    _lastStations.putAll(allStations)
                    _homeState.value = HomeState.Trains(_lastTrains)
                },
                onFailure = { error ->
                    _homeState.value = HomeState.Error(error.message ?: "Failed to load trains")
                },
            )
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
            t.routeName.contains(ql, ignoreCase = true) ||
                t.origin.contains(ql, ignoreCase = true) ||
                t.destination.contains(ql, ignoreCase = true) ||
                t.trainNum.toString().contains(ql) ||
                t.currentStop.contains(ql, ignoreCase = true)
        }
        val stations = _lastStations.values.filter { s ->
            s.name.contains(ql, ignoreCase = true) ||
                s.code.contains(ql, ignoreCase = true) ||
                s.city.contains(ql, ignoreCase = true) ||
                s.state.contains(ql, ignoreCase = true)
        }
        _searchState.value = SearchState.Results(q, trains, stations)
    }

    fun clearSearch() {
        _searchState.value = SearchState.Idle
    }

    fun loadTrains() {
        loadHome()
    }

    fun loadStations() {
        viewModelScope.launch {
            val result = api.fetchAllStations()
            result.fold(
                onSuccess = { stations ->
                    _lastStations.clear()
                    _lastStations.putAll(stations)
                    _homeState.value = HomeState.Stations(
                        stations.values
                            .filter { s -> s.trains.any { !it.startsWith("v", ignoreCase = true) } }
                            .sortedBy { it.name }
                    )
                },
                onFailure = { error ->
                    _homeState.value = HomeState.Error(error.message ?: "Failed to load stations")
                },
            )
        }
    }

    fun loadTrainDetail(trainId: String) {
        viewModelScope.launch {
            _trainDetailState.value = TrainDetailState.Loading
            val result = api.fetchTrain(trainId)
            result.fold(
                onSuccess = { trains ->
                    val train = trains.firstOrNull()
                    if (train != null) {
                        // eventCode is the train's current station. Stops before it are
                        // ahead (the train has already been), the stop matching eventCode
                        // is where the train is now, and stops after it are still future.
                        val currentIndex = train.stations.indexOfFirst { it.code == train.eventCode }
                        val stops = train.stations.mapIndexed { index, st ->
                            val progress = when {
                                currentIndex >= 0 && index < currentIndex -> StopProgress.Past
                                currentIndex >= 0 && index == currentIndex -> StopProgress.Current
                                else -> StopProgress.Future
                            }
                            StopDisplay(
                                name = st.name.ifEmpty { stationKeys[st.code] ?: st.code },
                                code = st.code,
                                scheduledArrival = formatInstant(st.schArr),
                                estimatedArrival = formatInstant(st.arr),
                                delay = formatDelay(st.arrivalDelayMinutes),
                                status = st.status,
                                comment = st.postCmnt.ifEmpty { st.arrCmnt.ifEmpty { st.depCmnt } },
                                progress = progress,
                            )
                        }
                        _trainDetailState.value = TrainDetailState.Train(train.toDisplay(trainId), stops)
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
        viewModelScope.launch {
            val stationResult = api.fetchStationTrains(stationCode)
            val stationInfo = allStations[stationCode]
            stationResult.fold(
                onSuccess = { station ->
                    // station.trains is a list of train-ID strings (e.g. "19-30").
                    // Fetch each train's data and build StationTrain rows.
                    // Skip Via Rail IDs ("v"-prefixed) — they're Canadian services not fetchable here.
                    val nextTrains = station.trains
                        .filter { !it.startsWith("v", ignoreCase = true) }
                        .mapNotNull { id ->
                            api.fetchTrain(id).getOrNull()
                                ?.firstOrNull()
                                ?.toStationTrain(id, stationCode)
                        }
                    val stationDisplay = StationDisplay(
                        code = stationCode,
                        name = station.name.ifEmpty { stationInfo?.name ?: stationKeys[stationCode] ?: stationCode },
                        city = station.city.ifEmpty { stationInfo?.city ?: "" },
                        state = station.state.ifEmpty { stationInfo?.state ?: "" },
                        lat = station.lat,
                        lon = station.lon,
                        nextTrains = nextTrains,
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
        val delay = formatDelay(
            currentStation?.arrivalDelayMinutes ?: currentStation?.departureDelayMinutes,
        ) ?: "On time"
        return TrainDisplay(
            trainId = trainId,
            trainNum = trainNum.toIntOrNull() ?: trainId.toIntOrNull() ?: 0,
            routeName = routeName,
            origin = origin,
            destination = destination,
            currentStop = currentStop,
            status = statusMsg.ifEmpty { trainState },
            delay = delay,
            isDisrupted = serviceDisruption || statusMsg.contains("DISRUPTION", ignoreCase = true),
            lat = lat,
            lon = lon,
            heading = heading,
            lastUpdated = updatedAt ?: lastValTS,
        )
    }

    private fun TrainData.toStationTrain(trainId: String, stationCode: String): StationTrain {
        val num = trainId.split("-").firstOrNull()?.toIntOrNull() ?: trainNum.toIntOrNull() ?: 0
        // Resolve the stop AT the viewed station (not the train origin) so times and
        // delays apply to the station the user is viewing. Fall back to the first stop.
        val stop = stations.firstOrNull { it.code == stationCode } ?: stations.firstOrNull()
        return StationTrain(
            trainNum = num,
            routeName = routeName,
            trainId = trainId,
            scheduledArrival = stop?.schArr,
            actualArrival = stop?.arr,
            scheduledDeparture = stop?.schDep,
            actualDeparture = stop?.dep,
            delay = formatDelay(stop?.arrivalDelayMinutes),
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
        ) : TrainDetailState()
        data class Error(val message: String) : TrainDetailState()
    }

    data class StopDisplay(
        val name: String,
        val code: String,
        val scheduledArrival: String = "",
        val estimatedArrival: String = "",
        val delay: String = "",
        val status: String = "",
        val comment: String = "",
        val progress: StopProgress = StopProgress.Future,
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
}
