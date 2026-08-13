package com.thelightphone.amtrak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

class TrainDetailScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, AmtrakViewModel>(sealedActivity) {

    override val viewModelClass: Class<AmtrakViewModel>
        get() = AmtrakViewModel::class.java

    override fun createViewModel(): AmtrakViewModel =
        AmtrakViewModel(lightContext.dataStore)

    override fun willShow() {
        super.willShow()
        AmtrakRepository.resetTrainDisplay()
        val trainId = AmtrakRepository.selectedTrainId.value
        if (trainId != null) {
            viewModel.loadTrainDetail(trainId)
        }
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.trainDetailState.collectAsState()
        val errorModal by viewModel.errorModal.collectAsState()

        LightTheme(colors = themeColors) {
            Column(modifier = Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text("Train"),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    when (val s = state) {
                        is AmtrakViewModel.TrainDetailState.Loading -> {
                            LightScrollView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(1f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = "Loading train details...",
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                )
                            }
                        }

                        is AmtrakViewModel.TrainDetailState.Train -> {
                            val train = s.train
                            val stops = s.stops
                            val currentStopIndex = s.currentStopIndex
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(1f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = train.displayRoute,
                                    variant = LightTextVariant.Heading,
                                    align = TextAlign.Center,
                                )
                                LightText(
                                    text = train.trainNumber,
                                    variant = LightTextVariant.Detail,
                                    lighten = true,
                                    align = TextAlign.Center,
                                )
                                LightText(
                                    text = "${train.origin} → ${train.destination}",
                                    variant = LightTextVariant.Detail,
                                    align = TextAlign.Center,
                                )
                                val routeSpan = buildString {
                                    if (train.routeStart.isNotEmpty()) append(train.routeStart)
                                    if (train.routeEnd.isNotEmpty()) {
                                        if (isNotEmpty()) append(" → ")
                                        append(train.routeEnd)
                                    }
                                }
                                if (routeSpan.isNotEmpty()) {
                                    LightText(
                                        text = routeSpan,
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        align = TextAlign.Center,
                                    )
                                }
                                if (train.isDisrupted) {
                                    LightText(
                                        text = "!! ${train.status}",
                                        variant = LightTextVariant.Copy,
                                        lighten = true,
                                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                                    )
                                }
                                LightText(
                                    text = train.delay,
                                    variant = LightTextVariant.Detail,
                                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                                )
                                if (stops.isNotEmpty()) {
                                    LightText(
                                        text = "STOPS",
                                        variant = LightTextVariant.Subheading,
                                        lighten = true,
                                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                                    )
                                    stops.forEachIndexed { index, stop ->
                                        StopRow(
                                            stop = stop,
                                            index = index,
                                            currentStopIndex = currentStopIndex,
                                            drawConnector = index < currentStopIndex,
                                            onClick = {
                                                val code = stop.code
                                                if (code.isNotBlank()) {
                                                    AmtrakRepository.setSelectedTrainDisplay(train)
                                                    AmtrakRepository.setSelectedStation(code)
                                                    navigateTo(::StationDetailScreen)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        is AmtrakViewModel.TrainDetailState.Error -> {
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

@Composable
private fun StopRow(
    stop: AmtrakViewModel.StopDisplay,
    index: Int = 0,
    currentStopIndex: Int = -1,
    drawConnector: Boolean,
    onClick: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    val dotColor = if (currentStopIndex < 0 || index <= currentStopIndex) colors.content else colors.content.copy(alpha = 0.4f)
    val connectorColor = if (drawConnector) colors.content else colors.content.copy(alpha = 0.4f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable { onClick() }
            .padding(vertical = 0.5f.gridUnitsAsDp()),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(
                text = stop.name,
                variant = LightTextVariant.Copy,
            )
            val time = stop.estimatedArrival.ifEmpty { stop.scheduledArrival }
            val sub = buildString {
                if (time.isNotEmpty()) append(time)
                if (stop.delay.isNotEmpty() && stop.delay != "On time") {
                    if (isNotEmpty()) append(" · ")
                    append(stop.delay)
                } else if (stop.status.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(stop.status)
                }
                if (stop.comment.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append(stop.comment)
                }
            }
            if (sub.isNotEmpty()) {
                LightText(
                    text = sub,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }
        }
        Spacer(modifier = Modifier.width(0.5f.gridUnitsAsDp()))
        // Right-side progress rail: dot + optional connector line below.
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(0.6f.gridUnitsAsDp())
                    .background(dotColor),
            )
            if (drawConnector) {
                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
                Box(
                    modifier = Modifier
                        .width(0.15f.gridUnitsAsDp())
                        .height(1.5f.gridUnitsAsDp())
                        .background(connectorColor),
                )
            }
        }
    }
}


}
