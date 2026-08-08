package com.thelightphone.cdta

import android.util.Log
import com.thelightphone.cdta.gtfs.GtfsData
import com.thelightphone.cdta.gtfs.GtfsParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * Manages the bundled GTFS data — static schedule information including
 * routes, stops, and stop sequences per trip. No network required.
 */
/** Direction a bus is traveling along the route's stop sequence. */
enum class Direction { UP, DOWN }

/** Where a bus currently is (stop index) and which direction it's going. */
data class BusPosition(val stopIndex: Int, val direction: Direction, val displayStopIndex: Int = stopIndex)

class GtfsRepository {

    private val _data = MutableStateFlow<GtfsData?>(null)
    val data: StateFlow<GtfsData?> = _data.asStateFlow()

    companion object {
        private const val TAG = "GtfsRepository"
    }

    /**
     * Synchronously parse GTFS JSON asset bytes (assumes already decompressed by Android).
     * Call from a background thread (Dispatchers.IO) — this is a blocking operation.
     */
    fun parseAsset(assetBytes: ByteArray) {
        try {
            val json = try {
                // Try gzip decompression first
                val baos = ByteArrayOutputStream()
                GZIPInputStream(assetBytes.inputStream()).use { gzipInput ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    while (gzipInput.read(buffer).also { bytesRead = it } != -1) {
                        baos.write(buffer, 0, bytesRead)
                    }
                }
                baos.toString(Charsets.UTF_8)
            } catch (gzipError: Exception) {
                // Fallback: treat as plain JSON (Android may have already decompressed)
                assetBytes.toString(Charsets.UTF_8)
            }

            val parsed = GtfsParser.parse(json)
            _data.value = parsed
            Log.d(TAG, "GTFS data loaded: ${parsed.routes.size} routes, ${parsed.stops.size} stops")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load GTFS data", e)
            _data.value = null
        }
    }

    /**
     * Returns the ordered list of stop IDs for a given trip ID.
     * Used to determine which stop a bus is at between two stops.
     */
    fun getStopSequence(tripId: String): List<String>? = data.value?.tripStops?.get(tripId)

    /**
     * Given a tripId and the current stopId, returns the next stop ID in the sequence.
     */
    fun getNextStop(tripId: String, currentStopId: String): String? {
        val stops = getStopSequence(tripId) ?: return null
        val idx = stops.indexOf(currentStopId)
        return if (idx >= 0 && idx + 1 < stops.size) stops[idx + 1] else null
    }

    /**
     * Returns a count of stops remaining before the final destination.
     */
    fun getStopsAway(tripId: String, currentStopId: String): Int? {
        val stops = getStopSequence(tripId) ?: return null
        val idx = stops.indexOf(currentStopId)
        if (idx < 0) return null
        return stops.size - idx - 1
    }

    /**
     * Finds the active trip for a route at the given time.
     * Returns the trip ID of the trip that has started (first stop already departed)
     * but hasn't finished (last stop hasn't arrived yet).
     * Falls back to the next upcoming trip if no active trip.
     */
    fun findActiveTrip(data: com.thelightphone.cdta.gtfs.GtfsData, routeShortName: String, currentTimeSec: Int): String? {
        val tripIds = data.routeTrips[routeShortName] ?: return null
        var nextUpcomingTrip: String? = null
        var nextUpcomingStart = Int.MAX_VALUE

        for (tripId in tripIds) {
            val times = data.tripTimes[tripId] ?: continue
            if (times.isEmpty()) continue

            val firstStopTime = times[0][0]  // arrival time at first stop
            val lastStopTime = times.last()[1]  // departure time at last stop

            // Check if this trip is currently active
            if (firstStopTime <= currentTimeSec && currentTimeSec <= lastStopTime) {
                return tripId
            }

            // Track the next upcoming trip
            if (firstStopTime > currentTimeSec && firstStopTime < nextUpcomingStart) {
                nextUpcomingStart = firstStopTime
                nextUpcomingTrip = tripId
            }
        }

        // No active trip — return next upcoming (bus hasn't started yet but will)
        // Also handle edge case: if current time is past all trips today, return the first trip
        if (nextUpcomingTrip != null) {
            return nextUpcomingTrip
        }
        // Fallback: return first trip with valid times
        return tripIds.firstOrNull { data.tripTimes[it]?.isNotEmpty() == true }
    }

    /**
     * Returns the next scheduled arrival time (in seconds past midnight) for the given
     * stop on the given route at or after the current time.
     * Returns null if no more trips today.
     */
    fun getNextArrivalAtStop(data: GtfsData, stopId: String, routeShortName: String, currentTimeSec: Int): Int? {
        val tripIds = data.routeTrips[routeShortName] ?: return null
        var nextArrival: Int? = null

        for (tripId in tripIds) {
            val stopIds = data.tripStops[tripId] ?: continue
            val times = data.tripTimes[tripId] ?: continue
            val stopIdx = stopIds.indexOf(stopId)
            if (stopIdx < 0) continue
            val arrivalTime = times[stopIdx][0]
            if (arrivalTime >= currentTimeSec) {
                if (nextArrival == null || arrivalTime < nextArrival) {
                    nextArrival = arrivalTime
                }
            }
        }
        return nextArrival
    }

    /**
     * Returns the direction (headsign) for the route's trip that serves the given stop
     * at the given time.
     */
    fun getDirectionForRoute(data: GtfsData, routeShortName: String, stopId: String, currentTimeSec: Int): String {
        val tripIds = data.routeTrips[routeShortName] ?: return ""
        val activeTripId = findActiveTrip(data, routeShortName, currentTimeSec) ?: return ""
        val meta = data.tripMeta[activeTripId]
        return meta?.headsign ?: ""
    }


    /**
     * Finds ALL active trips for a route at the given time and returns each bus's
     * position (stop index + direction). Direction is UP if the bus is traveling
     * toward stop 0 of the trip's stop sequence, DOWN if toward the last stop.
     */
    fun findActiveTripsForRoute(
        data: com.thelightphone.cdta.gtfs.GtfsData,
        routeShortName: String,
        currentTimeSec: Int,
    ): List<BusPosition> {
        val tripIds = data.routeTrips[routeShortName] ?: return emptyList()
        val positions = mutableListOf<BusPosition>()

        for (tripId in tripIds) {
            val times = data.tripTimes[tripId] ?: continue
            if (times.isEmpty()) continue

            val firstStopTime = times[0][0]
            val lastStopTime = times.last()[1]

            // Only consider trips that are currently active
            if (currentTimeSec < firstStopTime || currentTimeSec > lastStopTime) continue

            val stopIndex = getScheduledStopIndex(tripId, currentTimeSec) ?: continue
            if (stopIndex < 0) continue

            // Determine direction based on whether this trip's stops are "ascending"
            // or "descending" in the route's primary stop sequence.
            // For trips that share the same stop sequence, UP = toward earlier stops,
            // DOWN = toward later stops. We determine this by checking if the trip's
            // first stop is the same as the route's first stop.
            val stopSequence = data.tripStops[tripId] ?: continue
            val meta = data.tripMeta[tripId]
            val firstTripId = data.routeTrips[routeShortName]?.firstOrNull()
            val firstTripDir = data.tripMeta[firstTripId]?.direction ?: 0
            // The stopNames list comes from the first trip. If the bus's trip has the
            // same direction_id as the first trip, it moves DOWN (toward last stop).
            // If different, it moves UP (toward stop 0).
            val direction = if ((meta?.direction ?: 0) == firstTripDir) {
                Direction.DOWN
            } else {
                Direction.UP
            }

            // Map the bus's current stop to the displayed stop list index.
            // The stopNames list comes from a representative trip — map by stop ID.
            val currentStopId = stopSequence.getOrNull(stopIndex) ?: continue
            val displayStopIndex = data.tripStops[firstTripId]?.indexOf(currentStopId) ?: stopIndex

            positions.add(BusPosition(stopIndex, direction, displayStopIndex))
        }
        return positions
    }

    /** Returns all routes sorted by route short name. */
    fun getAllRoutes(): List<com.thelightphone.cdta.gtfs.GtfsRoute> =
        data.value?.routes?.values?.sortedBy { it.shortName } ?: emptyList()

    /**
     * Returns the index of the stop the bus should be at right now,
     * based on the current time and the trip's scheduled stop times.
     * Returns -1 if the time is before the first stop, or -2 if after the last stop.
     * Returns null if no schedule data is available for this trip.
     */
    fun getScheduledStopIndex(tripId: String, currentTimeSec: Int): Int? {
        val times = data.value?.tripTimes?.get(tripId) ?: return null
        var scheduledIndex = -1
        for (i in times.indices) {
            val arrival = times[i][0]
            if (arrival <= currentTimeSec) {
                scheduledIndex = i
            }
        }
        return scheduledIndex
    }

    /**
     * Returns the stop ID the bus should be at right now,
     * based on the current time and the trip's scheduled stop times.
     */
    fun getScheduledStop(tripId: String, currentTimeSec: Int): String? {
        val idx = getScheduledStopIndex(tripId, currentTimeSec) ?: return null
        if (idx < 0) return null
        val stopIds = getStopSequence(tripId) ?: return null
        return if (idx < stopIds.size) stopIds[idx] else null
    }

    /** Returns stops that match a search query (by name). */
    fun searchStops(query: String): List<com.thelightphone.cdta.gtfs.GtfsStop> {
        val q = query.lowercase()
        return data.value?.stops?.filter {
            it.value.name.contains(q, ignoreCase = true) || it.key.lowercase().contains(q)
        }?.values?.sortedBy { it.name } ?: emptyList()
    }

    fun isLoaded(): Boolean = data.value != null

    // Test-only helper to inject schedule data for contract testing
    internal fun setTestData(tripTimes: List<List<Int>>) {
        val stopSeq = listOf("stop_a", "stop_b", "stop_c")
        val testData = com.thelightphone.cdta.gtfs.GtfsData(
            stops = emptyMap(),
            tripStops = mapOf("test_trip" to stopSeq),
            tripTimes = mapOf("test_trip" to tripTimes),
            tripMeta = emptyMap(),
            routeTrips = emptyMap(),
            routes = emptyMap(),
        )
        _data.value = testData
    }

    // Test-only helper for multiple trips
    internal fun setTestDataMulti(trips: List<Pair<String, List<List<Int>>>>) {
        val tripTimesMap = mutableMapOf<String, List<List<Int>>>()
        val tripStopsMap = mutableMapOf<String, List<String>>()
        val stopSeq = listOf("stop_a", "stop_b", "stop_c")
        for ((tripId, times) in trips) {
            tripTimesMap[tripId] = times
            tripStopsMap[tripId] = stopSeq
        }
        val testData = com.thelightphone.cdta.gtfs.GtfsData(
            stops = emptyMap(),
            tripStops = tripStopsMap,
            tripTimes = tripTimesMap,
            tripMeta = emptyMap(),
            routeTrips = mapOf("1" to trips.map { it.first }),
            routes = emptyMap(),
        )
        _data.value = testData
    }

    // Test-only helper to inject full GTFS data
    internal fun setCustomData(data: com.thelightphone.cdta.gtfs.GtfsData) {
        _data.value = data
    }
}
