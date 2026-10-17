package com.thelightphone.amtrak

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    // amtraker.com live API returns trainTimely as an (often empty) STRING,
    // e.g. "trainTimely":"" — never a JSON boolean. Declared as String so the
    // trains map decodes instead of throwing and dropping every train.
    val trainTimely: String = "",
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
) {
    /** Whole-minute arrival delay vs scheduled (negative = early). Null when no actual arrival yet. */
    val arrivalDelayMinutes: Int? get() =
        arr?.let { a -> schArr?.let { s -> ((a.epochSeconds - s.epochSeconds) / 60).toInt() } }

    /** Whole-minute departure delay vs scheduled (negative = early). Null when no actual departure yet. */
    val departureDelayMinutes: Int? get() =
        dep?.let { d -> schDep?.let { s -> ((d.epochSeconds - s.epochSeconds) / 60).toInt() } }
}

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

/** Position of a train relative to a stop, for the route-progress view. */
enum class StopProgress {
    Past,   // the train has already visited this stop.
    Current, // the train is here now.
    Future,  // the train has not yet arrived.
}

data class StationTrain(
    val trainNum: Int,
    val routeName: String,
    val trainId: String,
    val scheduledArrival: Instant? = null,
    val estimatedArrival: Instant? = null,
    val actualArrival: Instant? = null,
    val scheduledDeparture: Instant? = null,
    val estimatedDeparture: Instant? = null,
    val actualDeparture: Instant? = null,
    val delay: String = "",
    val status: String,
) {
    val trainNumber: String get() = trainNum.toString()
    val displayRoute: String get() = routeName.ifEmpty { "Train $trainNum" }
    val arrivalTime: Instant? get() = actualArrival ?: scheduledArrival
    val departureTime: Instant? get() = actualDeparture ?: scheduledDeparture

    /** Whole-minute arrival delay at this stop vs scheduled (negative = early). Null when not yet arrived. */
    val arrivalDelayMinutes: Int? get() =
        actualArrival?.let { a -> scheduledArrival?.let { s -> ((a.epochSeconds - s.epochSeconds) / 60).toInt() } }
}

internal fun formatDelay(minutes: Int?): String {
    if (minutes == null) return ""
    return when {
        minutes > 1 -> "$minutes min late"
        minutes < -1 -> "${-minutes} min early"
        else -> "On time"
    }
}
