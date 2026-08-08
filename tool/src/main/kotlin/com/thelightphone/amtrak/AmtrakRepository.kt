package com.thelightphone.amtrak

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared singleton repository (like Authenticator's TotpAccountRepository)
 * so that detail screens can access data loaded by the HomeScreen's ViewModel.
 * Replaces the old NavigationArgs global.
 */
object AmtrakRepository {
    private val _selectedTrainId = MutableStateFlow<String?>(null)
    val selectedTrainId: StateFlow<String?> = _selectedTrainId.asStateFlow()

    private val _selectedStationCode = MutableStateFlow<String?>(null)
    val selectedStationCode: StateFlow<String?> = _selectedStationCode.asStateFlow()

    private val _selectedTrainDisplay = MutableStateFlow<Any?>(null)
    val selectedTrainDisplay: StateFlow<Any?> = _selectedTrainDisplay.asStateFlow()

    fun setSelectedTrain(trainId: String) {
        _selectedTrainId.value = trainId
    }

    fun setSelectedTrainDisplay(display: Any?) {
        _selectedTrainDisplay.value = display
    }

    fun resetTrainDisplay() {
        _selectedTrainDisplay.value = null
    }

    fun setSelectedStation(stationCode: String) {
        _selectedStationCode.value = stationCode
    }

    fun clearSelection() {
        _selectedTrainId.value = null
        _selectedStationCode.value = null
        // Keep selectedTrainDisplay: it is set by the train-detail stop click so the
        // following station detail can show the originating route. It is only cleared
        // when a fresh train detail is opened (see setSelectedTrainDisplay reset).
    }
}
