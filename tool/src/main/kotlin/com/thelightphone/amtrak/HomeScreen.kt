package com.thelightphone.amtrak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, AmtrakViewModel>(sealedActivity) {

    override val viewModelClass: Class<AmtrakViewModel>
        get() = AmtrakViewModel::class.java

    override fun createViewModel(): AmtrakViewModel =
        AmtrakViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.homeState.collectAsState()
        val errorModal by viewModel.errorModal.collectAsState()

        LightTheme(colors = themeColors) {
            Column(modifier = Modifier.fillMaxSize()) {
                LightTopBar(
                    center = LightTopBarCenter.Text(
                        when (state) {
                            is AmtrakViewModel.HomeState.Stations -> "Stations"
                            else -> "Trains"
                        },
                    ),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = { navigateTo(::SettingsScreen) },
                        contentDescription = "Settings",
                    ),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(top = 0.5f.gridUnitsAsDp())
                        .background(LightThemeTokens.colors.background),
                ) {
                    when (val s = state) {
                        is AmtrakViewModel.HomeState.Loading -> {
                            LightScrollView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(1f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = "Loading Amtrak data...",
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                )
                            }
                        }

                        is AmtrakViewModel.HomeState.Trains -> {
                            val trains = s.trains
                            if (trains.isEmpty()) {
                                LightScrollView(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(1f.gridUnitsAsDp()),
                                ) {
                                    LightText(
                                        text = "No trains found.",
                                        variant = LightTextVariant.Copy,
                                        lighten = true,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(trains) { train ->
                                        TrainRow(train) {
                                            AmtrakRepository.setSelectedTrain(train.trainId)
                                            navigateTo(::TrainDetailScreen)
                                        }
                                    }
                                }
                            }
                        }

                        is AmtrakViewModel.HomeState.Stations -> {
                            val stations = s.stations
                            if (stations.isEmpty()) {
                                LightScrollView(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(1f.gridUnitsAsDp()),
                                ) {
                                    LightText(
                                        text = "No stations found.",
                                        variant = LightTextVariant.Copy,
                                        lighten = true,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    items(stations) { station ->
                                        StationRow(
                                            StationDisplay(
                                                code = station.code,
                                                name = station.name,
                                                city = station.city,
                                                state = station.state,
                                                lat = station.lat,
                                                lon = station.lon,
                                                nextTrains = emptyList(),
                                            ),
                                        ) {
                                            AmtrakRepository.setSelectedStation(station.code)
                                            navigateTo(::StationDetailScreen)
                                        }
                                    }
                                }
                            }
                        }

                        is AmtrakViewModel.HomeState.Error -> {
                            LightScrollView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(1f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = "Error: ${s.message}",
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(
                            icon = LightIcons.DIRECTIONS_TRAIN,
                            onClick = { viewModel.loadHome() },
                        ),
                        LightBarButton.LightIcon(
                            icon = LightIcons.MAP,
                            onClick = { viewModel.loadStations() },
                        ),
                        LightBarButton.LightIcon(
                            icon = LightIcons.SEARCH,
                            onClick = {
                                navigateTo(::SearchScreen)
                            },
                        ),
                    ),
                )
            }
            errorModal?.let { message ->
                LightFullscreenModal(
                    message = message,
                    onClose = { viewModel.dismissError() },
                )
            }
        }
    }
}

@Composable
private fun TrainRow(train: TrainDisplay, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(
                horizontal = 1f.gridUnitsAsDp(),
                vertical = 0.75f.gridUnitsAsDp(),
            ),
    ) {
        LightText(
            text = train.displayRoute,
            variant = LightTextVariant.Copy,
        )
        LightText(
            text = " " + train.trainNumber,
            variant = LightTextVariant.Detail,
            lighten = true,
        )
        LightText(
            text = "${train.origin} → ${train.destination}",
            variant = LightTextVariant.Detail,
            lighten = true,
        )
        LightText(
            text = "Stop: ${train.currentStop} · ${train.delay}",
            variant = LightTextVariant.Detail,
            lighten = true,
        )
        if (train.isDisrupted) {
            LightText(
                text = "!! ${train.status}",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }
    }
}

@Composable
private fun StationRow(station: StationDisplay, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(
                horizontal = 1f.gridUnitsAsDp(),
                vertical = 0.75f.gridUnitsAsDp(),
            ),
    ) {
        LightText(
            text = station.displayName,
            variant = LightTextVariant.Copy,
        )
        if (station.state.isNotEmpty()) {
            LightText(
                text = station.state,
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }
    }
}
