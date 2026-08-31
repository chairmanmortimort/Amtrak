package com.thelightphone.wikipedia

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.thelightphone.sdk.ui.defaultKeyboardOptions
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcon
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement

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
        val settings by viewModel.settings.collectAsState()

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
                            onSettingsClick = viewModel::openSettings,
                            recentArticles = state.recentArticles,
                            showArticleOfDay = settings?.showArticleOfDay ?: true,
                            onOpenRecent = viewModel::openArticle,
                            onArticleOfDay = viewModel::openArticleOfDay,
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
                            leadExtract = mode.leadExtract,
                            isLoading = mode.isLoading,
                            onBack = viewModel::cancelSearch,
                            onOpenSettings = viewModel::openAbout,
                            onOpenLink = viewModel::openLink,
                            onRandom = viewModel::openRandom,
                            showRandomArticle = settings?.showRandomArticle ?: true,
                            onLongPressWord = viewModel::defineTerm,
                        )
                    }

                    is WikipediaScreenMode.About -> {
                        AboutContent(onBack = viewModel::closeAbout)
                    }

                    is WikipediaScreenMode.Settings -> {
                        SettingsContent(
                            showRecent = mode.showRecentArticles,
                            showArticleOfDay = mode.showArticleOfDay,
                            showRandom = mode.showRandomArticle,
                            onToggleRecent = viewModel::toggleShowRecentArticles,
                            onToggleArticleOfDay = viewModel::toggleShowArticleOfDay,
                            onToggleRandom = viewModel::toggleShowRandomArticle,
                            onBack = viewModel::closeSettings,
                        )
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
 * Home screen — presents Search, Random, Article of the Day, and recently viewed articles.
 */
@Composable
private fun HomeContent(
    onSearchClick: () -> Unit,
    onRandomClick: () -> Unit,
    onSettingsClick: () -> Unit,
    recentArticles: List<String> = emptyList(),
    showArticleOfDay: Boolean = true,
    onOpenRecent: (String) -> Unit = {},
    onArticleOfDay: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(3f.gridUnitsAsDp()),
    ) {
        LightTopBar(
            rightButton = LightBarButton.LightIcon(
                icon = LightIcons.SETTINGS,
                onClick = onSettingsClick,
                contentDescription = "Settings",
            ),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        // Large wordmark
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2f.gridUnitsAsDp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LightText(
                text = "Wikipedia",
                variant = LightTextVariant.Heading,
                align = TextAlign.Center,
            )
            LightText(
                text = "Search articles or discover random facts.",
                variant = LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
            )
        }

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
            if (showArticleOfDay) {
                HomeMenuItem(
                    text = "Article of the Day",
                    icon = LightIcons.CIRCLE,
                    onClick = onArticleOfDay,
                )
            }
            if (recentArticles.isNotEmpty()) {
                LightText(
                    text = "Recent",
                    variant = LightTextVariant.Detail, // smaller, per user feedback
                    modifier = Modifier.padding(top = 2f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
                )
                recentArticles.forEach { title ->
                    LightText(
                        text = title,
                        variant = LightTextVariant.Detail, // smaller
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClick = { onOpenRecent(title) })
                            .padding(vertical = 1f.gridUnitsAsDp()), // tighter
                    )
                }
            }
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
 * Settings screen — toggles for Recent Articles, Article of the Day, and Random Article.
 * Uses the same pattern as Weather app's SettingsContent.
 */
@Composable
private fun SettingsContent(
    showRecent: Boolean,
    showArticleOfDay: Boolean,
    showRandom: Boolean,
    onToggleRecent: () -> Unit,
    onToggleArticleOfDay: () -> Unit,
    onToggleRandom: () -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("Settings"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            ToggleSettingRow(
                label = "Recent Articles",
                enabled = showRecent,
                onClick = onToggleRecent,
            )
            ToggleSettingRow(
                label = "Article of the Day",
                enabled = showArticleOfDay,
                onClick = onToggleArticleOfDay,
            )
            ToggleSettingRow(
                label = "Random Article",
                enabled = showRandom,
                onClick = onToggleRandom,
            )
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.BACK,
                    onClick = onBack,
                    contentDescription = "Back",
                ),
            ),
        )
    }
}

@Composable
private fun ToggleSettingRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 1f.gridUnitsAsDp()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        ) {
            LightText(
                text = label,
                variant = LightTextVariant.Copy,
            )
            LightIcon(
                icon = if (enabled) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
                size = 6f,
            )
        }
    }
}

@Composable
private fun AttributionFooter(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4f.gridUnitsAsDp())
            .lightClickable(onClick = onClick)
            .padding(horizontal = 1f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = "Content licensed under CC BY-SA 4.0.",
            variant = LightTextVariant.Fine,
            lighten = true,
        )
    }
}

/**
 * Search input screen using the SDK's LightTextInputEditor.
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
        keyboardOptionsFlow = remember { MutableStateFlow(defaultKeyboardOptions()) },
    )
}