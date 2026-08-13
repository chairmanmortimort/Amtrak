package com.thelightphone.amtrak

import androidx.compose.runtime.Composable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Data models for the Amtraker API (https://api.amtraker.com/v2/)
 *
 * Endpoint shapes (verified against the live API):
 *   GET /trains            -> Map<String, List<TrainData>>   (key = train number)
 *   GET /trains/{num}      -> Map<String, List<TrainData>>
 *   GET /stations          -> Map<String, Station>          (key = station code)
 *   GET /stations/{code}   -> Map<String, Station>
 *   GET /stations/{code}   -> Station.trains is List<String> of train IDs
 */

// === Trains ===

@Serializable
data class TrainData(
    val routeName: String = "",
    val trainNum: String = "",
    val trainNumRaw: String = "",
    val dataSource: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val heading: String = "",
    val velocity: Double = 0.0,
    val updatedAt: Instant? = null,
    val lastValTS: Instant? = null,
    val trainState: String = "",
    val statusMsg: String = "",
    val serviceDisruption: Boolean = false,
    val eventCode: String = "",
    val origCode: String = "",
    val destCode: String = "",
    val originTZ: String = "",
    val destTZ: String = "",
    val aliases: List<String> = emptyList(),
    val stations: List<TrainStation> = emptyList(),
) {
    val trainNumber: String get() = trainNum.ifEmpty { trainNumRaw }
    val routeDisplayName: String get() = routeName.ifEmpty { "Train $trainNumber" }
    val isDisrupted: Boolean get() = serviceDisruption || statusMsg.contains("DISRUPTION", ignoreCase = true)
    val currentStation: TrainStation? get() = stations.firstOrNull { it.code == eventCode }
}

@Serializable
data class TrainStation(
    val name: String = "",
    val code: String = "",
    val tz: String = "",
    val bus: Boolean = false,
    val schArr: Instant? = null,
    val schDep: Instant? = null,
    val arr: Instant? = null,
    val dep: Instant? = null,
    val arrCmnt: String = "",
    val depCmnt: String = "",
    val status: String = "",
    val postCmnt: String = "",
)

// === Stations ===

@Serializable
data class Station(
    val code: String = "",
    val name: String = "",
    val city: String = "",
    val state: String = "",
    val address1: String = "",
    val address2: String = "",
    val zip: String = "",
    val tz: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val hasAddress: Boolean = false,
    val trains: List<String> = emptyList(),
) {
    val displayName: String
        get() = when {
            city.isEmpty() -> name
            name.equals(city, ignoreCase = true) -> name
            name.contains(city, ignoreCase = true) -> name
            city.contains(name, ignoreCase = true) -> city
            else -> "$name, $city"
        }
    val fullAddress: String get() = listOfNotNull(address1, address2, city, state, zip).joinToString(", ")
}

// === Display Models ===

data class TrainDisplay(
    val trainId: String,
    val trainNum: Int,
    val routeName: String,
    val origin: String,
    val destination: String,
    val currentStop: String,
    val status: String,
    val delay: String,
    val isDisrupted: Boolean,
    val lat: Double,
    val lon: Double,
    val heading: String,
    val lastUpdated: Instant?,
    val routeStart: String = "",
    val routeEnd: String = "",
) {
    val trainNumber: String get() = if (trainId.contains(Regex("[^0-9]"))) trainId else trainNum.toString()
    val displayRoute: String get() = routeName.ifEmpty { "Train $trainNumber" }
}

data class StationDisplay(
    val code: String,
    val name: String,
    val city: String,
    val state: String,
    val lat: Double,
    val lon: Double,
    val nextTrains: List<StationTrain>,
    val fromTrain: StationTrain? = null,
) {
    val displayName: String
        get() = when {
            city.isEmpty() -> name
            name.equals(city, ignoreCase = true) -> name
            name.contains(city, ignoreCase = true) -> name
            city.contains(name, ignoreCase = true) -> city
            else -> "$name, $city"
        }
    val hasTrains: Boolean get() = nextTrains.isNotEmpty()
}

data class StationTrain(
    val trainId: String = "",
    val trainNum: Int,
    val routeName: String,
    val scheduledArrival: Instant?,
    val estimatedArrival: Instant?,
    val actualArrival: Instant?,
    val scheduledDeparture: Instant?,
    val estimatedDeparture: Instant?,
    val actualDeparture: Instant? = null,
    val status: String,
) {
    val trainNumber: String get() = trainNum.toString()
    val displayRoute: String get() = routeName.ifEmpty { "Train $trainNum" }
    val arrivalTime: Instant? get() = estimatedArrival ?: scheduledArrival
    val departureTime: Instant? get() = estimatedDeparture ?: scheduledDeparture
}
