package com.thelightphone.amtrak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
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

class StationDetailScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, AmtrakViewModel>(sealedActivity) {

    override val viewModelClass: Class<AmtrakViewModel>
        get() = AmtrakViewModel::class.java

    override fun createViewModel(): AmtrakViewModel =
        AmtrakViewModel(lightContext.dataStore)

    override fun willShow() {
        super.willShow()
        val stationCode = AmtrakRepository.selectedStationCode.value
        if (stationCode != null) {
            viewModel.loadStationDetail(stationCode)
        }
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.stationDetailState.collectAsState()
        val errorModal by viewModel.errorModal.collectAsState()

        LightTheme(colors = themeColors) {
            Column(modifier = Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text("Station"),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    when (val s = state) {
                        is AmtrakViewModel.StationDetailState.Loading -> {
                            LightScrollView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(1f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = "Loading station details...",
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                )
                            }
                        }

                        is AmtrakViewModel.StationDetailState.Station -> {
                            val stn = s.station
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(1f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = stn.displayName,
                                    variant = LightTextVariant.Heading,
                                    align = TextAlign.Center,
                                )
                                if (stn.state.isNotEmpty()) {
                                    LightText(
                                        text = stn.state,
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        align = TextAlign.Center,
                                        modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                                    )
                                }
                                if (stn.fromTrain != null) {
                                    LightText(
                                        text = "Route you're viewing",
                                        variant = LightTextVariant.Subheading,
                                        lighten = true,
                                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                                    )
                                }
                                if (stn.nextTrains.isEmpty()) {
                                    LightText(
                                        text = "No upcoming trains at this stop.",
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        align = TextAlign.Center,
                                        modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                                    )
                                } else {
                                    LightText(
                                        text = "Next trains",
                                        variant = LightTextVariant.Subheading,
                                        lighten = true,
                                        modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                                    )
                                    stn.nextTrains.forEach { train ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .lightClickable {
                                                    val id = train.trainId
                                                    if (id.isNotBlank()) {
                                                        AmtrakRepository.setSelectedTrain(id)
                                                        navigateTo(::TrainDetailScreen)
                                                    }
                                                }
                                                .padding(vertical = 0.5f.gridUnitsAsDp()),
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
                                            val arr = viewModel.formatArrival(train)
                                            if (arr.isNotEmpty()) {
                                                LightText(
                                                    text = "Arriving " + arr,
                                                    variant = LightTextVariant.Detail,
                                                    lighten = true,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        is AmtrakViewModel.StationDetailState.Error -> {
                            LightScrollView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(1f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = s.message,
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
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
