package com.thelightphone.cdta

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CdtaModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun routeDisplay() {
        val route = Route(
            routeId = "1",
            routeShortName = "1",
            routeLongName = "Troy - Albany",
            routeDesc = "Bus route",
            routeColor = "FF0000",
            routeTextColor = "FFFFFF",
        )
        val display = RouteDisplay(route, stopCount = 15)
        assertEquals("1", display.shortName)
        assertEquals("Troy - Albany", display.displayName)
        assertEquals(15, display.stopCount)
        assertTrue(!display.isBusPlus)
    }

    @Test
    fun stopDisplay() {
        val stop = Stop(
            stopId = "123",
            stopName = "State & Madison",
            stopLat = 42.6528,
            stopLon = -73.7565,
            wheelchairBoarding = 1,
            routes = listOf("1", "10"),
        )
        val display = StopDisplay(stop, emptyList())
        assertEquals("State & Madison", display.displayName)
        assertTrue(display.stop.hasWheelchairAccess)
        assertEquals(2, display.stop.routeCount)
    }

    @Test
    fun predictionFormatting() {
        val pred = Prediction(
            routeId = "10",
            routeShortName = "10",
            routeLongName = "Colonie Center - Troy",
            direction = "Northbound",
            arrivalTime = "3:45 PM",
            arrivalDelay = 2,
        )
        assertEquals("10", pred.displayRoute)
        assertEquals("Northbound", pred.headsign)
        assertEquals("3:45 PM", pred.arrivalTimeFormatted)
        assertEquals("2 min late", pred.delayFormatted)
        assertTrue(pred.isDelayed)
    }

    @Test
    fun predictionOnTime() {
        val pred = Prediction(
            routeId = "10",
            routeShortName = "10",
            arrivalTime = "3:45 PM",
            arrivalDelay = 0,
        )
        assertEquals("On time", pred.delayFormatted)
        assertTrue(!pred.isDelayed)
    }

    @Test
    fun predictionEarly() {
        val pred = Prediction(
            routeId = "10",
            routeShortName = "10",
            arrivalTime = "3:45 PM",
            arrivalDelay = -3,
        )
        assertEquals("3 min early", pred.delayFormatted)
        assertTrue(!pred.isDelayed)
    }
}
