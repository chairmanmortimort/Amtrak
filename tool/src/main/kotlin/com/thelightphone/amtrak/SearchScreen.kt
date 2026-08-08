package com.thelightphone.amtrak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

class SearchScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, AmtrakViewModel>(sealedActivity) {

    override val viewModelClass: Class<AmtrakViewModel>
        get() = AmtrakViewModel::class.java

    override fun createViewModel(): AmtrakViewModel =
        AmtrakViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.searchState.collectAsState()
        val errorModal by viewModel.errorModal.collectAsState()
        val currentQuery = (state as? AmtrakViewModel.SearchState.Results)?.query ?: ""
        val textFieldState = rememberTextFieldState(currentQuery)
        var editing by remember { mutableStateOf(false) }
        var editorKey by remember { mutableStateOf(0) }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                if (editing) {
                    LightTextInputEditor(
                        title = "Search",
                        state = textFieldState,
                        keyboardOptionsFlow = remember { MutableStateFlow(defaultKeyboardOptions()) },
                        singleLine = true,
                        editorKey = editorKey,
                        onSubmit = { result: CharSequence ->
                            editing = false
                            editorKey++
                            viewModel.search(result.toString())
                        },
                        onBack = { editing = false },
                        submitIcon = LightIcons.SEARCH,
                        showBackButton = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LightTopBar(
                            leftButton = LightBarButton.LightIcon(
                                icon = LightIcons.BACK,
                                onClick = { goBack() },
                                contentDescription = "Back",
                            ),
                            center = LightTopBarCenter.Text("Search"),
                            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                        )

                        LightTextField(
                            label = "Search",
                            value = (state as? AmtrakViewModel.SearchState.Results)?.query ?: "",
                            placeholder = "Tap to search train",
                            onClick = { editing = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 1f.gridUnitsAsDp()),
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        ) {
                            when (val s = state) {
                                is AmtrakViewModel.SearchState.Idle -> {
                                    LightScrollView(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(1f.gridUnitsAsDp()),
                                    ) {
                                        LightText(
                                            text = "Search for a train number, route, station, or city.",
                                            variant = LightTextVariant.Copy,
                                            align = TextAlign.Center,
                                        )
                                    }
                                }

                                is AmtrakViewModel.SearchState.Results -> {
                                    val trains = s.trains
                                    val stations = s.stations
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(1f.gridUnitsAsDp()),
                                    ) {
                                        if (trains.isEmpty() && stations.isEmpty()) {
                                            item {
                                                LightText(
                                                    text = "No matches for \"${s.query}\".",
                                                    variant = LightTextVariant.Copy,
                                                    align = TextAlign.Center,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 1f.gridUnitsAsDp()),
                                                )
                                            }
                                        }
                                        if (stations.isNotEmpty()) {
                                            item {
                                                LightText(
                                                    text = "STATIONS",
                                                    variant = LightTextVariant.Subheading,
                                                    lighten = true,
                                                    modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                                                )
                                            }
                                            items(stations) { station ->
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .lightClickable {
                                                            AmtrakRepository.setSelectedStation(station.code)
                                                            navigateTo(::StationDetailScreen)
                                                        }
                                                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                                                ) {
                                                    LightText(
                                                        text = station.name.ifEmpty { station.code },
                                                        variant = LightTextVariant.Copy,
                                                    )
                                                    if (station.state.isNotEmpty()) {
                                                        LightText(
                                                            text = "${station.city}, ${station.state}",
                                                            variant = LightTextVariant.Detail,
                                                            lighten = true,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (trains.isNotEmpty()) {
                                            item {
                                                LightText(
                                                    text = "TRAINS",
                                                    variant = LightTextVariant.Subheading,
                                                    lighten = true,
                                                    modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                                                )
                                            }
                                            items(trains) { train ->
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .lightClickable {
                                                            AmtrakRepository.setSelectedTrain(train.trainId)
                                                            navigateTo(::TrainDetailScreen)
                                                        }
                                                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                                                ) {
                                                    LightText(
                                                        text = train.displayRoute,
                                                        variant = LightTextVariant.Copy,
                                                    )
                                                    LightText(
                                                        text = "${train.origin} → ${train.destination}",
                                                        variant = LightTextVariant.Detail,
                                                        lighten = true,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        LightBottomBar(
                            items = listOf(
                                LightBarButton.LightIcon(
                                    icon = LightIcons.DIRECTIONS_TRAIN,
                                    onClick = { goBack() },
                                ),
                                LightBarButton.LightIcon(
                                    icon = LightIcons.MAP,
                                    onClick = { goBack() },
                                ),
                                LightBarButton.LightIcon(
                                    icon = LightIcons.SEARCH,
                                    onClick = { editing = true },
                                ),
                            ),
                        )
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
