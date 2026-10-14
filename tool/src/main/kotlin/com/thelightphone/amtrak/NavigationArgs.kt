package com.thelightphone.amtrak

/**
 * Lightweight holder for selection made on one screen and read by the next,
 * since the SDK's navigateTo does not support passing arguments into a child screen.
 * Values are cleared after consumption to avoid stale reads.
 */
object NavigationArgs {
    var trainId: String = ""
    var stationCode: String = ""

    fun clear() {
        trainId = ""
        stationCode = ""
    }
}
