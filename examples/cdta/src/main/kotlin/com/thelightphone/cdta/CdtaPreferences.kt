package com.thelightphone.cdta

import androidx.datastore.preferences.core.stringPreferencesKey

internal object CdtaPreferences {
    val API_KEY = stringPreferencesKey("cdta_api_key")
    val ROUTES_JSON = stringPreferencesKey("cdta_routes_json")
    val STOPS_JSON = stringPreferencesKey("cdta_stops_json")
    val LAST_STOP_ID = stringPreferencesKey("cdta_last_stop_id")
    val LAST_STOP_NAME = stringPreferencesKey("cdta_last_stop_name")
}
