package com.thelightphone.cdta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GtfsRepositoryContractTest {

    @Test
    fun testScheduledStopIndex_before_first_stop_returns_0() {
        val repo = GtfsRepository()
        // Simulate trip with stops at 100, 200, 300 seconds
        val times = listOf(listOf(100, 100), listOf(200, 200), listOf(300, 300))
        repo.setTestData(times)

        // Time 50 is before the first stop (100) — bus hasn't started yet
        assertEquals(-1, repo.getScheduledStopIndex("test_trip", 50))
    }

    @Test
    fun testScheduledStopIndex_at_first_stop() {
        val repo = GtfsRepository()
        val times = listOf(listOf(100, 100), listOf(200, 200), listOf(300, 300))
        repo.setTestData(times)

        // Time 100 is exactly at the first stop
        assertEquals(0, repo.getScheduledStopIndex("test_trip", 100))
    }

    @Test
    fun testScheduledStopIndex_between_stops() {
        val repo = GtfsRepository()
        val times = listOf(listOf(100, 100), listOf(200, 200), listOf(300, 300))
        repo.setTestData(times)

        // Time 150 is between stop 0 (100) and stop 1 (200) — bus should be at stop 0
        assertEquals(0, repo.getScheduledStopIndex("test_trip", 150))
    }

    @Test
    fun testScheduledStopIndex_at_last_stop() {
        val repo = GtfsRepository()
        val times = listOf(listOf(100, 100), listOf(200, 200), listOf(300, 300))
        repo.setTestData(times)

        // Time 300 is at the last stop
        assertEquals(2, repo.getScheduledStopIndex("test_trip", 300))
    }

    @Test
    fun testScheduledStopIndex_after_last_stop() {
        val repo = GtfsRepository()
        val times = listOf(listOf(100, 100), listOf(200, 200), listOf(300, 300))
        repo.setTestData(times)

        // Time 400 is after the last stop — bus has finished
        assertEquals(2, repo.getScheduledStopIndex("test_trip", 400))
    }

    @Test
    fun testScheduledStopIndex_no_schedule_data_returns_null() {
        val repo = GtfsRepository()
        // No test data set
        assertNull(repo.getScheduledStopIndex("nonexistent_trip", 150))
    }

    @Test
    fun testRouteDetailMode_hasBusMarkers() {
        // Verify the CdtaScreenMode.RouteDetail carries bus markers
        val mode = CdtaScreenMode.RouteDetail(
            route = RouteDisplay("1", "Central Avenue", isBusPlus = false),
            busMarkers = mapOf(3 to "⭐↓")
        )
        assertEquals("⭐↓", mode.busMarkers[3])
    }

    @Test
    fun testRouteDetailMode_busMarkers_defaults_to_empty() {
        val mode = CdtaScreenMode.RouteDetail(
            route = RouteDisplay("1", "Central Avenue", isBusPlus = false),
        )
        assertTrue(mode.busMarkers.isEmpty())
    }

    @Test
    fun testRouteDisplay_has_stopNames() {
        val display = RouteDisplay(
            shortName = "114",
            displayName = "Madison Ave - Western Ave",
            isBusPlus = false,
            stopCount = 57,
            routeId = "114",
            stopNames = listOf("Stop A", "Stop B", "Stop C")
        )
        assertEquals(3, display.stopNames.size)
        assertEquals("Stop B", display.stopNames[1])
    }

    @Test
    fun testFindActiveTrip_during_active_trip() {
        val repo = GtfsRepository()
        // Trip 1: 100-300, Trip 2: 500-700
        val times1 = listOf(listOf(100, 100), listOf(200, 200), listOf(300, 300))
        val times2 = listOf(listOf(500, 500), listOf(600, 600), listOf(700, 700))
        repo.setTestDataMulti(listOf(Pair("trip1", times1), Pair("trip2", times2)))

        val data = repo.data.value ?: return
        // At time 150, trip1 is active
        assertEquals("trip1", repo.findActiveTrip(data, "1", 150))
        // At time 550, trip2 is active
        assertEquals("trip2", repo.findActiveTrip(data, "1", 550))
    }

    @Test
    fun testFindActiveTrip_before_any_trip() {
        val repo = GtfsRepository()
        val times = listOf(listOf(1000, 1000), listOf(2000, 2000), listOf(3000, 3000))
        repo.setTestDataMulti(listOf(Pair("trip1", times)))

        val data = repo.data.value ?: return
        // At time 50, no trip has started — should return the first upcoming trip
        assertEquals("trip1", repo.findActiveTrip(data, "1", 50))
    }

    @Test
    fun testFindActiveTripsForRoute_finds_active_buses() {
        val repo = GtfsRepository()
        // Trip 1: 100-300 (active at time 150), Trip 2: 500-700 (not active at 150)
        val times1 = listOf(listOf(100, 100), listOf(200, 200), listOf(300, 300))
        val times2 = listOf(listOf(500, 500), listOf(600, 600), listOf(700, 700))
        repo.setTestDataMulti(listOf(Pair("trip1", times1), Pair("trip2", times2)))

        val data = repo.data.value ?: return
        // At time 150, only trip1 is active and should be at stop 0
        val positions = repo.findActiveTripsForRoute(data, "1", 150)
        assertEquals(1, positions.size)
        assertEquals(0, positions[0].stopIndex)
        // tripMeta is empty → direction defaults to 0 for both trip and firstTrip → same → DOWN
        assertEquals(Direction.DOWN, positions[0].direction)
    }

    @Test
    fun testFindActiveTripsForRoute_no_active_buses() {
        val repo = GtfsRepository()
        val times = listOf(listOf(1000, 1000), listOf(2000, 2000), listOf(3000, 3000))
        repo.setTestDataMulti(listOf(Pair("trip1", times)))

        val data = repo.data.value ?: return
        // At time 50, no trip has started
        val positions = repo.findActiveTripsForRoute(data, "1", 50)
        assertTrue(positions.isEmpty())
    }

    @Test
    fun testFindActiveTripsForRoute_direction_mapping() {
        val repo = GtfsRepository()
        val times = listOf(listOf(100, 100), listOf(200, 200), listOf(300, 300))
        val stopSeq = listOf("stop_a", "stop_b", "stop_c")
        // Create data with explicit direction metadata
        val testData = com.thelightphone.cdta.gtfs.GtfsData(
            stops = emptyMap(),
            tripStops = mapOf("trip_dir0" to stopSeq, "trip_dir1" to stopSeq),
            tripTimes = mapOf("trip_dir0" to times, "trip_dir1" to times),
            tripMeta = mapOf(
                "trip_dir0" to com.thelightphone.cdta.gtfs.TripMeta(direction = 0),
                "trip_dir1" to com.thelightphone.cdta.gtfs.TripMeta(direction = 1),
            ),
            routeTrips = mapOf("1" to listOf("trip_dir0", "trip_dir1")),
            routes = emptyMap(),
        )
        repo.setCustomData(testData)

        val positions = repo.findActiveTripsForRoute(testData, "1", 150)
        assertEquals(2, positions.size)
        // First trip has direction_id 0 → it's the same as firstTripDir → DOWN
        // Second trip has direction_id 1 → different from firstTripDir → UP
        val down = positions.find { it.direction == Direction.DOWN }
        val up = positions.find { it.direction == Direction.UP }
        assertNotNull(down)
        assertNotNull(up)
    }

    @Test
    fun testFindActiveTripsForRoute_multiple_active_buses() {
        val repo = GtfsRepository()
        // Two overlapping trips active at time 150
        val times1 = listOf(listOf(100, 100), listOf(200, 200), listOf(300, 300))
        val times2 = listOf(listOf(120, 120), listOf(220, 220), listOf(320, 320))
        repo.setTestDataMulti(listOf(Pair("trip1", times1), Pair("trip2", times2)))

        val data = repo.data.value ?: return
        val positions = repo.findActiveTripsForRoute(data, "1", 150)
        assertEquals(2, positions.size)
    }
}
