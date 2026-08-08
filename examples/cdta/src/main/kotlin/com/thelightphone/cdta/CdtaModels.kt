package com.thelightphone.cdta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for CDTA schedule data, parsed from the bundled GTFS feed
 * (https://www.cdta.org/gtfs) by GtfsParser. CDTA serves the Capital District
 * (Albany, Schenectady, Troy, Rensselaer) NY area — fixed-route buses and
 * BusPlus (limited-stop BRT). No network/API calls; everything is offline.
 */

// === Data Models (parsed from GTFS) ===

@Serializable
data class Route(
    @SerialName("route_id") val routeId: String = "",
    @SerialName("route_type") val routeType: String = "",
    @SerialName("route_name") val routeName: String = "",
    @SerialName("route_short_name") val routeShortName: String = "",
    @SerialName("route_long_name") val routeLongName: String = "",
    @SerialName("route_desc") val routeDesc: String = "",
    @SerialName("route_color") val routeColor: String = "",
    @SerialName("route_text_color") val routeTextColor: String = "",
) {
    val displayName: String
        get() = routeLongName.ifEmpty { routeShortName.ifEmpty { routeName.ifEmpty { routeId } } }

    val shortName: String
        get() = routeShortName.ifEmpty { routeId }

    val isBusPlus: Boolean
        get() = routeLongName.contains("PLUS", ignoreCase = true) || routeDesc.contains("BusPlus", ignoreCase = true)
}

@Serializable
data class Stop(
    @SerialName("stop_id") val stopId: String = "",
    @SerialName("stop_name") val stopName: String = "",
    @SerialName("stop_lat") val stopLat: Double = 0.0,
    @SerialName("stop_lon") val stopLon: Double = 0.0,
    @SerialName("stop_desc") val stopDesc: String = "",
    @SerialName("zone_id") val zoneId: String = "",
    @SerialName("location_type") val locationType: String = "",
    @SerialName("wheelchair_boarding") val wheelchairBoarding: Int = 0,
    @SerialName("routes") val routes: List<String> = emptyList(),
) {
    val displayName: String
        get() = stopName.ifEmpty { stopId }

    val hasWheelchairAccess: Boolean
        get() = wheelchairBoarding == 1

    val routeCount: Int
        get() = routes.size
}

// === Real-time Predictions ===

@Serializable
data class StopPredictionResponse(
    @SerialName("stop_id") val stopId: String = "",
    @SerialName("stop_name") val stopName: String = "",
    @SerialName("predictions") val predictions: List<Prediction> = emptyList(),
)

@Serializable
data class Prediction(
    @SerialName("route_id") val routeId: String = "",
    @SerialName("route_short_name") val routeShortName: String = "",
    @SerialName("route_long_name") val routeLongName: String = "",
    @SerialName("direction_id") val directionId: String = "",
    @SerialName("direction") val direction: String = "",
    @SerialName("trip_id") val tripId: String = "",
    @SerialName("trip_headsign") val tripHeadsign: String = "",
    @SerialName("vehicle_id") val vehicleId: String = "",
    @SerialName("arrival_time") val arrivalTime: String = "",
    @SerialName("arrival_delay") val arrivalDelay: Int = 0,
    @SerialName("departure_time") val departureTime: String = "",
    @SerialName("departure_delay") val departureDelay: Int = 0,
    @SerialName("prediction_time") val predictionTime: String = "",
    @SerialName("wheelchair_accessible") val wheelchairAccessible: Int = 0,
    @SerialName("next_stop_id") val nextStopId: String = "",
    @SerialName("next_stop_name") val nextStopName: String = "",
    @SerialName("stop_sequence") val stopSequence: Int = 0,
) {
    val displayRoute: String
        get() = routeShortName.ifEmpty { routeId }

    val headsign: String
        get() = tripHeadsign.ifEmpty { direction }

    /** Formatted time string for arrival (e.g. "3:45 PM") */
    val arrivalTimeFormatted: String
        get() {
            if (arrivalTime.isEmpty()) return "--:--"
            return arrivalTime
        }

    /** Formatted delay string (e.g. "2 min late") */
    val delayFormatted: String
        get() {
            return when {
                arrivalDelay > 0 -> "${arrivalDelay} min late"
                arrivalDelay < 0 -> "${-arrivalDelay} min early"
                else -> "On time"
            }
        }

    val isDelayed: Boolean
        get() = arrivalDelay > 0

    val hasWheelchairAccess: Boolean
        get() = wheelchairAccessible == 1

    /**
     * Returns a description of the next stop if available.
     * e.g. "1 stop to Washington Park" or "2 stops to Downtown"
     * Uses GTFS stop sequence data if nextStopName is not provided by the API.
     */
    val nextStopFormatted: String
        get() = if (nextStopName.isNotBlank()) {
            val count = if (stopSequence > 0) stopSequence else 1
            val stopWord = if (count == 1) "stop" else "stops"
            "$count $stopWord to $nextStopName"
        } else ""
}

// === Display/Helper Models ===

/**
 * Unified display model for routes that works with both CDTA API and GTFS data.
 */
data class RouteDisplay(
    val shortName: String,
    val displayName: String,
    val isBusPlus: Boolean = false,
    val stopCount: Int = 0,
    val routeId: String = "",
    val stopNames: List<String> = emptyList(),
    val stopIds: List<String> = emptyList(),
) {
    constructor(route: Route, stopCount: Int = 0, stopNames: List<String> = emptyList(), stopIds: List<String> = emptyList()) : this(
        shortName = route.shortName,
        displayName = route.displayName,
        isBusPlus = route.isBusPlus,
        stopCount = stopCount,
        routeId = route.routeId,
        stopNames = stopNames,
        stopIds = stopIds,
    )

    constructor(route: com.thelightphone.cdta.gtfs.GtfsRoute, stopCount: Int = 0, stopNames: List<String> = emptyList(), stopIds: List<String> = emptyList()) : this(
        shortName = route.shortName,
        displayName = route.longName.ifEmpty { route.shortName },
        isBusPlus = route.longName.contains("PLUS", ignoreCase = true) || route.desc.contains("BusPlus", ignoreCase = true),
        stopCount = stopCount,
        routeId = route.shortName,
        stopNames = stopNames,
        stopIds = stopIds,
    )
}

data class StopDisplay(
    val stop: Stop,
    val predictions: List<Prediction> = emptyList(),
) {
    val displayName: String get() = stop.displayName
    val hasPredictions: Boolean get() = predictions.isNotEmpty()
}

data class NearbyStop(
    val stopId: String,
    val stopName: String,
    val distanceText: String,
    val routes: List<String>,
)
