package com.thelightphone.cdta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, CdtaViewModel>(sealedActivity) {

    override val viewModelClass: Class<CdtaViewModel>
        get() = CdtaViewModel::class.java

    override fun createViewModel(): CdtaViewModel =
        CdtaViewModel(lightContext.dataStore, GtfsRepository(), lightContext)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        LightTheme(colors = themeColors) {
            Column(modifier = Modifier.fillMaxSize()) {
                // LightTextInputEditor has its own top bar
                if (state.mode !is CdtaScreenMode.SearchInput) {
                    LightTopBar(
                        center = LightTopBarCenter.Text("CDTA"),
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(LightThemeTokens.colors.background),
                ) {
                    when (val mode = state.mode) {
                        is CdtaScreenMode.Routes -> {
                            RouteListContent(
                                routes = mode.routes,
                                isLoading = mode.isLoading,
                                onRouteClick = { route -> viewModel.selectRoute(route) },
                            )
                        }
                        is CdtaScreenMode.Stops -> {
                            StopListContent(
                                stops = mode.stops,
                                isLoading = mode.isLoading,
                                onStopClick = { stop -> viewModel.selectStop(stop) },
                            )
                        }
                        is CdtaScreenMode.Search -> {
                            SearchResultsContent(
                                query = mode.query,
                                results = mode.results,
                                onRouteClick = { route -> viewModel.selectRoute(route) },
                                onStopClick = { stop -> viewModel.selectStop(stop) },
                                onBack = { viewModel.cancelSearch() },
                            )
                        }
                        is CdtaScreenMode.SearchInput -> {
                            SearchInputContent(
                                currentQuery = mode.currentQuery,
                                onSearchSubmitted = { query -> viewModel.submitSearch(query) },
                                onBack = { viewModel.cancelSearch() },
                            )
                        }
                        is CdtaScreenMode.StopDetail -> {
                            StopDetailContent(
                                stopDisplay = mode.stopDisplay,
                                onRouteClick = { routeShortName ->
                                    viewModel.selectStopRoute(mode.stopDisplay.stop, routeShortName)
                                },
                                onBack = { viewModel.setHomeView(CdtaViewModel.HomeView.Stops) },
                            )
                        }
                        is CdtaScreenMode.StopRouteSchedule -> {
                            StopRouteScheduleContent(
                                stop = mode.stop,
                                routeShortName = mode.routeShortName,
                                nextArrivalFormatted = mode.nextArrivalFormatted,
                                direction = mode.direction,
                                onTimeClick = { viewModel.refreshToCurrentTime() },
                                onBack = { viewModel.showStops() },
                            )
                        }
                        is CdtaScreenMode.RouteDetail -> {
                            RouteDetailContent(
                                routeDisplay = mode.route,
                                busMarkers = mode.busMarkers,
                                onTimeClick = { viewModel.refreshToCurrentTime() },
                                onBack = { viewModel.showRoutes() },
                                onStopClick = { stopId -> viewModel.selectStopById(stopId) },
                            )
                        }
                        is CdtaScreenMode.TimeSelector -> {
                            TimeSelectorContent(
                                customTime = mode.customTime,
                                onSetTime = { timeConfig ->
                                    if (timeConfig != null) {
                                        viewModel.setCustomTime(timeConfig.hours, timeConfig.minutes)
                                    } else {
                                        viewModel.clearCustomTime()
                                    }
                                    // Recompute the screen we came from with the new time
                                    val returnMode = mode.returnMode
                                    if (returnMode is CdtaScreenMode.RouteDetail) {
                                        viewModel.refreshSchedule(returnMode.route)
                                    } else if (returnMode is CdtaScreenMode.StopRouteSchedule) {
                                        viewModel.selectStopRoute(returnMode.stop, returnMode.routeShortName)
                                    } else {
                                        // Default to routes
                                        viewModel.showRoutes()
                                    }
                                },
                                onDismiss = {
                                    val returnMode = mode.returnMode
                                    if (returnMode is CdtaScreenMode.RouteDetail) {
                                        viewModel.refreshSchedule(returnMode.route)
                                    } else if (returnMode is CdtaScreenMode.StopRouteSchedule) {
                                        viewModel.selectStopRoute(returnMode.stop, returnMode.routeShortName)
                                    } else {
                                        viewModel.showRoutes()
                                    }
                                },
                            )
                        }
                    }
                }

                // Hide bottom bar when keyboard input is active
                if (state.mode !is CdtaScreenMode.SearchInput) {
                    LightBottomBar(
                        items = listOf(
                            LightBarButton.LightIcon(
                                icon = LightIcons.DIRECTIONS_BUS,
                                onClick = { viewModel.setHomeView(CdtaViewModel.HomeView.Routes) },
                                contentDescription = "Routes",
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.MAP,
                                onClick = { viewModel.setHomeView(CdtaViewModel.HomeView.Stops) },
                                contentDescription = "Stops",
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.SEARCH,
                                onClick = { viewModel.setHomeView(CdtaViewModel.HomeView.Search) },
                                contentDescription = "Search",
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.ALARM,
                                onClick = { viewModel.openTimeSelector() },
                                contentDescription = "Time",
                            ),
                        ),
                    )
                }
            }
        }

        state.errorModal?.let { message ->
            LightFullscreenModal(
                message = message,
                onClose = { viewModel.dismissErrorModal() },
            )
        }
    }
}

@Composable
private fun RouteListContent(
    routes: List<RouteDisplay>,
    isLoading: Boolean,
    onRouteClick: (RouteDisplay) -> Unit,
) {
    if (routes.isEmpty() && isLoading) {
        LightScrollView(
            modifier = Modifier
                .fillMaxSize()
                .padding(1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = "Loading CDTA routes...",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
    } else if (routes.isEmpty()) {
        LightScrollView(
            modifier = Modifier
                .fillMaxSize()
                .padding(1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = "No routes found. Try restarting the app.",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(routes) { route ->
                RouteRow(route, onClick = { onRouteClick(route) })
            }
        }
    }
}

@Composable
private fun RouteRow(route: RouteDisplay, onClick: () -> Unit) {
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
            text = route.shortName,
            variant = LightTextVariant.Copy,
        )
        LightText(
            text = route.displayName,
            variant = LightTextVariant.Detail,
            lighten = true,
        )
        if (route.isBusPlus) {
            LightText(
                text = "BusPlus",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }
    }
}

@Composable
private fun StopListContent(
    stops: List<Stop>,
    isLoading: Boolean,
    onStopClick: (Stop) -> Unit,
) {
    if (stops.isEmpty() && isLoading) {
        LightScrollView(
            modifier = Modifier
                .fillMaxSize()
                .padding(1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = "Loading CDTA stops...",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
    } else if (stops.isEmpty()) {
        LightScrollView(
            modifier = Modifier
                .fillMaxSize()
                .padding(1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = "No stops found.",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(stops) { stop ->
                StopRow(stop, onClick = { onStopClick(stop) })
            }
        }
    }
}

@Composable
private fun StopRow(stop: Stop, onClick: () -> Unit) {
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
            text = stop.displayName,
            variant = LightTextVariant.Copy,
        )
        if (stop.hasWheelchairAccess) {
            LightText(
                text = "Wheelchair accessible",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }
        LightText(
            text = "${stop.routeCount} route(s)",
            variant = LightTextVariant.Detail,
            lighten = true,
        )
    }
}

@Composable
private fun SearchResultsContent(
    query: String,
    results: List<SearchResult>,
    onRouteClick: (RouteDisplay) -> Unit,
    onStopClick: (Stop) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("Search"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (results.isEmpty()) {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(1f.gridUnitsAsDp()),
            ) {
                LightText(
                    text = if (query.isEmpty()) {
                        "Search for a route or stop by name or number."
                    } else {
                        "No results for \"$query\"."
                    },
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                items(results) { result: SearchResult ->
                    when (result) {
                        is SearchResult.Route -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .lightClickable { onRouteClick(result.route) }
                                    .padding(
                                        horizontal = 1f.gridUnitsAsDp(),
                                        vertical = 0.75f.gridUnitsAsDp(),
                                    ),
                            ) {
                                LightText(
                                    text = result.route.shortName,
                                    variant = LightTextVariant.Copy,
                                )
                                LightText(
                                    text = result.route.displayName,
                                    variant = LightTextVariant.Detail,
                                    lighten = true,
                                )
                            }
                        }
                        is SearchResult.Stop -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .lightClickable { onStopClick(result.stop) }
                                    .padding(
                                        horizontal = 1f.gridUnitsAsDp(),
                                        vertical = 0.75f.gridUnitsAsDp(),
                                    ),
                            ) {
                                LightText(
                                    text = result.stop.displayName,
                                    variant = LightTextVariant.Copy,
                                )
                                LightText(
                                    text = result.stop.stopId,
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
}

@Composable
private fun StopDetailContent(
    stopDisplay: StopDisplay,
    onRouteClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("Arrivals"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        val predictions = stopDisplay.predictions
        val routes = stopDisplay.stop.routes

        if (predictions.isEmpty() && routes.isEmpty()) {
            LightScrollView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1f.gridUnitsAsDp()),
            ) {
                LightText(
                    text = "No arrival predictions available for this stop.\nNo routes found in GTFS data.",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                )
            }
        } else if (predictions.isEmpty()) {
            // No API key — show GTFS routes as tappable items
            LightScrollView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1f.gridUnitsAsDp()),
            ) {
                LightText(
                    text = stopDisplay.displayName,
                    variant = LightTextVariant.Copy,
                )
                LightText(
                    text = "${routes.size} route(s) serve this stop:",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
                routes.forEach { routeShortName ->
                    LightText(
                        text = "Route $routeShortName",
                        variant = LightTextVariant.Copy,
                        modifier = Modifier
                            .padding(vertical = 0.5f.gridUnitsAsDp())
                            .lightClickable { onRouteClick(routeShortName) },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(1f.gridUnitsAsDp()),
            ) {
                items(predictions) { pred ->
                    Column(
                        modifier = Modifier.padding(vertical = 0.75f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = pred.displayRoute,
                            variant = LightTextVariant.Copy,
                        )
                        LightText(
                            text = pred.arrivalTimeFormatted,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                        )
                        if (pred.nextStopFormatted.isNotBlank()) {
                            LightText(
                                text = pred.nextStopFormatted,
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

@Composable
private fun RouteDetailContent(
    routeDisplay: RouteDisplay,
    busMarkers: Map<Int, String>,
    onBack: () -> Unit,
    onTimeClick: () -> Unit,
    onStopClick: (stopId: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text(routeDisplay.shortName),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SETTINGS,
                onClick = onTimeClick,
                contentDescription = "Refresh schedule",
            ),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = routeDisplay.displayName,
                variant = LightTextVariant.Copy,
            )
            LightText(
                text = "${routeDisplay.stopCount} stops",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
            if (routeDisplay.isBusPlus) {
                LightText(
                    text = "BusPlus - Limited stop service",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }
            if (routeDisplay.stopNames.isNotEmpty()) {
                routeDisplay.stopNames.forEachIndexed { index, stopName ->
                    val marker = busMarkers[index]
                    val markerText = marker ?: ""
                    LightText(
                        text = "$markerText${index + 1}. $stopName",
                        variant = LightTextVariant.Copy,
                        modifier = Modifier
                            .lightClickable(onClick = { onStopClick(routeDisplay.stopIds[index]) })
                            .padding(top = 1f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}

@Composable
private fun StopRouteScheduleContent(
    stop: Stop,
    routeShortName: String,
    nextArrivalFormatted: String,
    direction: String,
    onTimeClick: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("Route $routeShortName"),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SETTINGS,
                onClick = onTimeClick,
                contentDescription = "Time selector",
            ),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = stop.displayName,
                variant = LightTextVariant.Copy,
            )
            LightText(
                text = "Next scheduled arrival:",
                variant = LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
            )
            LightText(
                text = nextArrivalFormatted,
                variant = LightTextVariant.Copy,
            )
            if (direction.isNotBlank()) {
                LightText(
                    text = "Direction: $direction",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }
            if (nextArrivalFormatted == "No more today") {
                LightText(
                    text = "No more scheduled trips for today.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            }
        }
    }
}

@Composable
private fun SearchInputContent(
    currentQuery: String,
    onSearchSubmitted: (CharSequence) -> Unit,
    onBack: () -> Unit,
) {
    val textFieldState = rememberTextFieldState(currentQuery)
    LightTextInputEditor(
        title = "Search Routes & Stops",
        state = textFieldState,
        onSubmit = onSearchSubmitted,
        onBack = onBack,
        submitIcon = LightIcons.SEARCH,
        showBackButton = true,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun TimeSelectorContent(
    customTime: TimeConfig?,
    onSetTime: (TimeConfig?) -> Unit,
    onDismiss: () -> Unit,
) {
    // State for the 4-digit time picker: H H : M M
    var h1 by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(customTime?.hours?.div(10) ?: 0) }
    var h2 by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(customTime?.hours?.rem(10) ?: 0) }
    var m1 by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(customTime?.minutes?.div(10) ?: 0) }
    var m2 by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(customTime?.minutes?.rem(10) ?: 0) }

    fun cycleH1() { h1 = (h1 + 1) % 3 }
    fun cycleH2() { h2 = if (h1 == 2 && h2 >= 3) 0 else (h2 + 1) % 10 }
    fun cycleM1() { m1 = (m1 + 1) % 6 }
    fun cycleM2() { m2 = (m2 + 1) % 10 }

    val effectiveHour = (h1 * 10 + h2).coerceIn(0, 23)
    val effectiveMinute = (m1 * 10 + m2).coerceIn(0, 59)

    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = { onDismiss() },
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("Time"),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.ACCEPT,
                onClick = { onSetTime(TimeConfig(effectiveHour, effectiveMinute)) },
                contentDescription = "Set time",
            ),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = "View schedule as of:",
                variant = LightTextVariant.Copy,
            )
            LightText(
                text = String.format("%02d:%02d", effectiveHour, effectiveMinute),
                variant = LightTextVariant.Heading,
                modifier = Modifier.padding(vertical = 1f.gridUnitsAsDp()),
            )

            // 4-digit time picker: H H : M M
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2f.gridUnitsAsDp()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val digitStyle = LightTextVariant.Heading
                val digitMod = Modifier
                    .lightClickable(onClick = { cycleH1() })
                    .padding(horizontal = 0.5f.gridUnitsAsDp())
                LightText(text = h1.toString(), variant = digitStyle, modifier = digitMod)
                LightText(
                    text = h2.toString(),
                    variant = digitStyle,
                    modifier = Modifier
                        .lightClickable(onClick = { cycleH2() })
                        .padding(horizontal = 0.5f.gridUnitsAsDp()),
                )
                LightText(
                    text = ":",
                    variant = digitStyle,
                    modifier = Modifier.padding(horizontal = 0.5f.gridUnitsAsDp()),
                )
                LightText(
                    text = m1.toString(),
                    variant = digitStyle,
                    modifier = Modifier
                        .lightClickable(onClick = { cycleM1() })
                        .padding(horizontal = 0.5f.gridUnitsAsDp()),
                )
                LightText(
                    text = m2.toString(),
                    variant = digitStyle,
                    modifier = Modifier
                        .lightClickable(onClick = { cycleM2() })
                        .padding(horizontal = 0.5f.gridUnitsAsDp()),
                )
            }

            LightText(
                text = "Tap a digit to change it.",
                variant = LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.Center,
            )
        }
    }
}
