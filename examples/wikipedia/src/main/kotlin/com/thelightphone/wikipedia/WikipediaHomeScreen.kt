package com.thelightphone.wikipedia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
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
class WikipediaHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, WikipediaViewModel>(sealedActivity) {

    override val viewModelClass: Class<WikipediaViewModel>
        get() = WikipediaViewModel::class.java

    override fun createViewModel(): WikipediaViewModel =
        WikipediaViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    is WikipediaScreenMode.Home -> {
                        HomeContent(
                            onSearchClick = viewModel::openSearch,
                            onRandomClick = viewModel::openRandom,
                            onSettingsClick = viewModel::openAbout,
                        )
                    }

                    is WikipediaScreenMode.SearchInput -> {
                        SearchInputContent(
                            currentQuery = mode.currentQuery,
                            editorKey = mode.editorKey,
                            onSearchSubmitted = { query -> viewModel.submitSearch(query) },
                            onBack = viewModel::cancelSearch,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is WikipediaScreenMode.Search -> {
                        SearchResultsContent(
                            query = mode.query,
                            results = mode.results,
                            isLoading = mode.isLoading,
                            onSelect = viewModel::selectSearchResult,
                            onBack = viewModel::cancelSearch,
                        )
                    }

                    is WikipediaScreenMode.Loading -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LightTopBar(
                                center = LightTopBarCenter.Text("Wikipedia"),
                                modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                LightText(
                                    text = mode.message,
                                    variant = LightTextVariant.Copy,
                                    align = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                                )
                            }
                        }
                    }

                    is WikipediaScreenMode.Article -> {
                        ArticleContent(
                            title = mode.title,
                            description = mode.description,
                            extract = mode.extract,
                            thumbnailUrl = mode.thumbnailUrl,
                            links = mode.links,
                            isLoading = mode.isLoading,
                            onBack = viewModel::cancelSearch,
                            onOpenSettings = viewModel::openAbout,
                            onOpenLink = viewModel::openLink,
                        )
                    }

                    is WikipediaScreenMode.About -> {
                        AboutContent(onBack = viewModel::closeAbout)
                    }
                }

                state.errorModal?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = viewModel::dismissError,
                    )
                }
            }
        }
    }
}

/**
 * Home screen — presents Search and Random options.
 */
@Composable
private fun HomeContent(
    onSearchClick: () -> Unit,
    onRandomClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(3f.gridUnitsAsDp()),
    ) {
        LightTopBar(
            center = LightTopBarCenter.Text("Wikipedia"),
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SETTINGS,
                onClick = onSettingsClick,
                contentDescription = "About",
            ),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightText(
            text = "Search articles or discover random facts.",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp())
                .padding(top = 2f.gridUnitsAsDp())
                .verticalScroll(rememberScrollState()),
        ) {
            HomeMenuItem(
                text = "Search",
                icon = LightIcons.SEARCH,
                onClick = onSearchClick,
            )
            HomeMenuItem(
                text = "Random Article",
                icon = LightIcons.LOOP,
                onClick = onRandomClick,
            )
        }
    }
}

@Composable
private fun HomeMenuItem(
    text: String,
    icon: com.thelightphone.sdk.ui.LightIconConfiguration,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 1.5f.gridUnitsAsDp()),
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Subheading,
        )
    }
}

/**
 * Search input screen using the SDK's LightTextInputEditor — integrates with LP3
 * hardware keyboard and supports D-pad navigation properly.
 * Uses editorKey to force TextFieldState reset on re-entry (same pattern as Amtrak SearchScreen).
 */
@Composable
fun SearchInputContent(
    currentQuery: String,
    editorKey: Any,
    onSearchSubmitted: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textFieldState = rememberTextFieldState(currentQuery)

    LightTextInputEditor(
        title = "Search Wikipedia",
        state = textFieldState,
        onSubmit = { query -> onSearchSubmitted(query.toString()) },
        onBack = onBack,
        modifier = modifier.fillMaxSize(),
        submitLabel = "SEARCH",
        submitIcon = LightIcons.SEARCH,
        editorKey = editorKey,
    )
}
