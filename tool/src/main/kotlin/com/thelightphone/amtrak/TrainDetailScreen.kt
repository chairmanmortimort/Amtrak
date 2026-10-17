package com.thelightphone.amtrak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
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

    override fun createViewModel(): AmtrakViewModel = AmtrakViewModel()

    override fun willShow() {
        super.willShow()
        viewModel.loadTrainDetail(NavigationArgs.trainId)
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.trainDetailState.collectAsState()

        LightTheme(colors = themeColors) {
            Column(modifier = Modifier.fillMaxSize()) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text("Train"),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.SETTINGS,
                        onClick = { navigateTo(screenFactory = { activity -> AboutScreen(activity) }) },
                        contentDescription = "About",
                    ),
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
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(1f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = "Train ${train.trainNumber}",
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                )
                                LightText(
                                    text = "${train.origin} → ${train.destination}",
                                    variant = LightTextVariant.Detail,
                                    align = TextAlign.Center,
                                )
                                if (train.isDisrupted) {
                                    LightText(
                                        text = "⚠ ${train.status}",
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
                                        text = "ROUTE",
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                                    )
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        // Route progress: stop name/times on the LEFT,
                                        // connecting line + square markers on the RIGHT.
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.CenterStart),
                                        ) {
                                            stops.forEach { stop ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .lightClickable {
                                                            val code = stop.code
                                                            if (code.isNotBlank()) {
                                                                NavigationArgs.stationCode = code
                                                                navigateTo(::StationDetailScreen)
                                                            }
                                                        }
                                                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(end = 1f.gridUnitsAsDp()),
                                                ) {
                                                        LightText(
                                                            text = stop.name,
                                                            variant = LightTextVariant.Copy,
                                                        )
                                                        val time = stop.estimatedArrival.ifEmpty { stop.scheduledArrival }
                                                        val sub = buildString {
                                                            if (time.isNotEmpty()) append(time)
                                                            if (stop.delay.isNotEmpty()) {
                                                                if (isNotEmpty()) append(" · ")
                                                                append(stop.delay)
                                                            }
                                                            if (stop.status.isNotEmpty()) {
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
                                                                text = "   $sub",
                                                                variant = LightTextVariant.Detail,
                                                                lighten = true,
                                                                modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                                                            )
                                                        }
                                                    }
                                                    // Marker, on the right edge of the row.
                                                    Box(
                                                        modifier = Modifier
                                                            .width(1.5f.gridUnitsAsDp())
                                                            .align(Alignment.CenterVertically),
                                                    ) {
                                                        val marker = when (stop.progress) {
                                                            StopProgress.Past -> "◼"
                                                            StopProgress.Current -> "◼"
                                                            StopProgress.Future -> "□"
                                                        }
                                                        val markerColor = if (stop.progress == StopProgress.Current)
                                                            LightThemeTokens.colors.content
                                                        else
                                                            LightThemeTokens.colors.contentSecondary
                                                        LightText(
                                                            text = marker,
                                                            variant = LightTextVariant.Copy,
                                                            monospace = true,
                                                            color = markerColor,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        // Vertical line connecting every stop's marker.
                                        Box(
                                            modifier = Modifier
                                                .width(1.5f.gridUnitsAsDp())
                                                .fillMaxHeight()
                                                .background(LightThemeTokens.colors.contentSecondary)
                                                .align(Alignment.CenterEnd),
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
        }
    }
}
