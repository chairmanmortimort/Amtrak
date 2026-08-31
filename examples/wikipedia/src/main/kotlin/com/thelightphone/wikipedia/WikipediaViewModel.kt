package com.thelightphone.wikipedia

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

sealed class WikipediaScreenMode {
    data object Home : WikipediaScreenMode()
    data class SearchInput(
        val currentQuery: String = "",
        val editorKey: Any = System.nanoTime(),
    ) : WikipediaScreenMode()
    data class Search(
        val query: String = "",
        val results: List<WikiSearchResult> = emptyList(),
        val isLoading: Boolean = false,
    ) : WikipediaScreenMode()
    data class Loading(val message: String) : WikipediaScreenMode()
    data class Article(
        val title: String,
        val description: String? = null,
        val extract: String = "",
        val thumbnailUrl: String? = null,
        val links: List<String> = emptyList(),
        val leadExtract: String? = null,
        val isLoading: Boolean = false,
    ) : WikipediaScreenMode()
    data object About : WikipediaScreenMode()
    data class Settings(
        val showRecentArticles: Boolean,
        val showArticleOfDay: Boolean,
        val showRandomArticle: Boolean,
    ) : WikipediaScreenMode()
}

data class WikipediaUiState(
    val mode: WikipediaScreenMode = WikipediaScreenMode.Home,
    val errorModal: String? = null,
    val recentArticles: List<String> = emptyList(),
)

private const val NETWORK_ERROR_MESSAGE =
    "Wikipedia requires a network connection. Please insert a data sim or connect to wi-fi."

private val MIN_LOADING_DISPLAY = 1.seconds

internal object WikipediaPreferences {
    val LAST_SEARCH_QUERY = stringPreferencesKey("wiki_last_search_query")
    val LAST_ARTICLE_TITLE = stringPreferencesKey("wiki_last_article_title")
    val RECENT_ARTICLES = stringSetPreferencesKey("wiki_recent_articles")
    val SHOW_RECENT_ARTICLES = booleanPreferencesKey("wiki_show_recent_articles")
    val SHOW_ARTICLE_OF_DAY = booleanPreferencesKey("wiki_show_article_of_day")
    val SHOW_RANDOM_ARTICLE = booleanPreferencesKey("wiki_show_random_article")
}

class WikipediaViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {

    private val api = WikipediaApi()

    private val _uiState = MutableStateFlow(WikipediaUiState())
    val uiState: StateFlow<WikipediaUiState> = _uiState.asStateFlow()

    private val _recentArticles = MutableStateFlow<List<String>>(emptyList())
    private val _settings = MutableStateFlow<WikipediaScreenMode.Settings?>(null)
    val settings: StateFlow<WikipediaScreenMode.Settings?> = _settings.asStateFlow()

    private val apiExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        val message = throwable.message ?: "Unexpected error"
        viewModelScope.launch(Dispatchers.Main) {
            updateErrorModal(message)
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (_uiState.value.mode is WikipediaScreenMode.Home && _uiState.value.errorModal == null) {
            seedRecentsOnce()
        }
        if (_settings.value == null) {
            loadSettings()
        }
    }

    private fun seedRecentsOnce() {
        if (_recentArticles.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val recents = (dataStore.data.first()[WikipediaPreferences.RECENT_ARTICLES]?.toList()
                ?: emptyList<String>()).filter { it.isNotBlank() }
            _recentArticles.value = recents
            _uiState.value = _uiState.value.copy(recentArticles = recents)
        }
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val showRecent = prefs[WikipediaPreferences.SHOW_RECENT_ARTICLES] ?: true
            val showArticleOfDay = prefs[WikipediaPreferences.SHOW_ARTICLE_OF_DAY] ?: true
            val showRandom = prefs[WikipediaPreferences.SHOW_RANDOM_ARTICLE] ?: true
            _settings.value = WikipediaScreenMode.Settings(showRecent, showArticleOfDay, showRandom)
            _uiState.value = _uiState.value.copy(recentArticles = if (showRecent) _recentArticles.value else emptyList<String>())
        }
    }

    private suspend fun saveSettings(settings: WikipediaScreenMode.Settings) {
        dataStore.edit { prefs ->
            prefs[WikipediaPreferences.SHOW_RECENT_ARTICLES] = settings.showRecentArticles
            prefs[WikipediaPreferences.SHOW_ARTICLE_OF_DAY] = settings.showArticleOfDay
            prefs[WikipediaPreferences.SHOW_RANDOM_ARTICLE] = settings.showRandomArticle
        }
        _settings.value = settings
        _uiState.value = _uiState.value.copy(
            recentArticles = if (settings.showRecentArticles) _recentArticles.value else emptyList<String>()
        )
    }

    fun openSearch() {
        _uiState.value = _uiState.value.copy(
            mode = WikipediaScreenMode.SearchInput(
                currentQuery = "",
                editorKey = System.nanoTime(),
            ),
            errorModal = null,
        )
    }

    fun openRandom() {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val loadStart = Clock.System.now()
            setState(WikipediaScreenMode.Loading("Finding a random article…"))
            api.fetchRandomTitle().fold(
                onSuccess = { title ->
                    if (title.isNotBlank()) {
                        openArticle(title)
                    } else {
                        showError("Could not find a random article.")
                    }
                },
                onFailure = {
                    showError(NETWORK_ERROR_MESSAGE)
                },
            )
            val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - loadStart)
            if (remaining.isPositive()) delay(remaining)
        }
    }

    fun openArticleOfDay() {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val loadStart = Clock.System.now()
            setState(WikipediaScreenMode.Loading("Finding article of the day…"))
            // Use current month/day
            val month = 8 // TODO: derive from now
            val day = 30
            api.fetchOnThisDay(month, day).fold(
                onSuccess = { response ->
                    val title = response.selected.firstOrNull()?.pages?.firstOrNull()?.title
                        ?: response.events.firstOrNull()?.pages?.firstOrNull()?.title
                    if (title.isNullOrBlank()) {
                        showError("Could not find article of the day.")
                    } else {
                        openArticle(title)
                    }
                },
                onFailure = {
                    showError(NETWORK_ERROR_MESSAGE)
                },
            )
            val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - loadStart)
            if (remaining.isPositive()) delay(remaining)
        }
    }

    fun submitSearch(query: CharSequence) {
        val q = query.toString().trim()
        if (q.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val loadStart = Clock.System.now()
            setState(WikipediaScreenMode.Search(query = q, results = emptyList<WikiSearchResult>(), isLoading = true))
            api.search(q).fold(
                onSuccess = { results ->
                    setState(WikipediaScreenMode.Search(query = q, results = results, isLoading = false))
                },
                onFailure = {
                    showError(NETWORK_ERROR_MESSAGE)
                },
            )
            val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - loadStart)
            if (remaining.isPositive()) delay(remaining)
            dataStore.edit { prefs ->
                prefs[WikipediaPreferences.LAST_SEARCH_QUERY] = q
            }
        }
    }

    fun cancelSearch() {
        val settings = _settings.value
        _uiState.value = _uiState.value.copy(
            mode = WikipediaScreenMode.Home,
            errorModal = null,
            recentArticles = if (settings?.showRecentArticles == true) _recentArticles.value else emptyList(),
        )
    }

    fun selectSearchResult(result: WikiSearchResult) {
        openArticle(result.title)
    }

    fun openArticle(title: String) {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val loadStart = Clock.System.now()
            setState(
                WikipediaScreenMode.Article(
                    title = title,
                    isLoading = true,
                    extract = "",
                    links = emptyList(),
                ),
            )
            val summary = api.fetchSummary(title).getOrNull()
            val extract = api.fetchExtract(title).getOrElse { "" }
            val links = api.fetchLinks(title).getOrElse { emptyList<String>() }

            val updatedRecents = buildList {
                add(title)
                addAll(_recentArticles.value.filter { it != title && it.isNotBlank() })
            }.filter { it.isNotBlank() }.take(5)
            _recentArticles.value = updatedRecents

            setState(
                WikipediaScreenMode.Article(
                    title = title,
                    description = summary?.description,
                    extract = extract,
                    thumbnailUrl = summary?.thumbnailUrl,
                    links = links,
                    leadExtract = summary?.extract?.takeIf { it.isNotBlank() },
                    isLoading = false,
                ),
            )
            _uiState.value = _uiState.value.copy(recentArticles = updatedRecents)

            val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - loadStart)
            if (remaining.isPositive()) delay(remaining)

            dataStore.edit { prefs ->
                prefs[WikipediaPreferences.LAST_ARTICLE_TITLE] = title
                val cur = ((prefs[WikipediaPreferences.RECENT_ARTICLES]?.toMutableList()
                    ?: mutableListOf<String>()).filter { it.isNotBlank() }).toMutableList()
                cur.remove(title)
                cur.add(0, title)
                prefs[WikipediaPreferences.RECENT_ARTICLES] = cur.filter { it.isNotBlank() }.take(5).toSet<String>()
            }
        }
    }

    fun openLink(title: String) {
        openArticle(title)
    }

    fun openAbout() {
        _uiState.value = _uiState.value.copy(mode = WikipediaScreenMode.About)
    }

    fun openSettings() {
        val current = _uiState.value.mode
        val settings = _settings.value ?: WikipediaScreenMode.Settings(true, true, true)
        _uiState.value = _uiState.value.copy(mode = settings)
    }

    fun closeSettings() {
        val settings = _settings.value ?: WikipediaScreenMode.Settings(true, true, true)
        _uiState.value = _uiState.value.copy(
            mode = WikipediaScreenMode.Home,
            recentArticles = if (settings.showRecentArticles) _recentArticles.value else emptyList<String>(),
            errorModal = null,
        )
    }

    fun toggleShowRecentArticles() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _settings.value ?: WikipediaScreenMode.Settings(true, true, true)
            val next = current.copy(showRecentArticles = !current.showRecentArticles)
            saveSettings(next)
        }
    }

    fun toggleShowArticleOfDay() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _settings.value ?: WikipediaScreenMode.Settings(true, true, true)
            val next = current.copy(showArticleOfDay = !current.showArticleOfDay)
            saveSettings(next)
        }
    }

    fun toggleShowRandomArticle() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _settings.value ?: WikipediaScreenMode.Settings(true, true, true)
            val next = current.copy(showRandomArticle = !current.showRandomArticle)
            saveSettings(next)
        }
    }

    fun defineTerm(term: String) {
        val t = term.toString().trim()
        if (t.isEmpty()) return
        submitSearch(t)
    }

    fun closeAbout() {
        val settings = _settings.value ?: WikipediaScreenMode.Settings(true, true, true)
        _uiState.value = _uiState.value.copy(
            mode = WikipediaScreenMode.Home,
            recentArticles = if (settings.showRecentArticles) _recentArticles.value else emptyList<String>(),
        )
    }

    fun dismissError() {
        val currentMode = _uiState.value.mode
        _uiState.value = _uiState.value.copy(errorModal = null)
        if (currentMode is WikipediaScreenMode.Loading) {
            _uiState.value = _uiState.value.copy(mode = WikipediaScreenMode.Home)
        }
    }

    private suspend fun updateErrorModal(message: String?) {
        _uiState.value = _uiState.value.copy(errorModal = message)
    }

    private suspend fun setState(mode: WikipediaScreenMode) {
        _uiState.value = _uiState.value.copy(mode = mode, errorModal = null)
    }

    private suspend fun showError(message: String) {
        _uiState.value = _uiState.value.copy(errorModal = message)
    }
}