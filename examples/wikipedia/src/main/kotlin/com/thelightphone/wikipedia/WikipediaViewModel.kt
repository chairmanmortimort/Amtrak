package com.thelightphone.wikipedia
import android.util.Log

import androidx.datastore.preferences.core.edit
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
    /** Initial home screen — shows Search and Random options. */
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

    /** About / attribution screen. */
    data object About : WikipediaScreenMode()
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
}

class WikipediaViewModel(
    private val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
) : LightViewModel<Unit>() {

    private val api = WikipediaApi()

    private val _uiState = MutableStateFlow(WikipediaUiState())
    val uiState: StateFlow<WikipediaUiState> = _uiState.asStateFlow()

    /** In-memory source of truth for recently viewed articles (DataStore is just persistence). */
    private val _recentArticles = MutableStateFlow<List<String>>(emptyList())

    private val apiExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        val message = throwable.message ?: "Unexpected error"
        viewModelScope.launch(Dispatchers.Main) {
            updateErrorModal(message)
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        // Restore last search query or article title from DataStore on first show
        if (_uiState.value.mode is WikipediaScreenMode.Home && _uiState.value.errorModal == null) {
            seedRecentsOnce()
        }
    }

    // === Home screen actions ===

    /** Seed the in-memory recents from DataStore once (called on first home show). */
    private fun seedRecentsOnce() {
        if (_recentArticles.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            // Filter out blank/garbage titles that older builds may have persisted.
            val recents = (dataStore.data.first()[WikipediaPreferences.RECENT_ARTICLES]?.toList()
                ?: emptyList()).filter { it.isNotBlank() }
            _recentArticles.value = recents
            _uiState.value = _uiState.value.copy(recentArticles = recents)
        }
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
            // Enforce minimum loading display
            val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - loadStart)
            if (remaining.isPositive()) delay(remaining)
        }
    }

    // === Search ===

    fun submitSearch(query: CharSequence) {
        val q = query.toString().trim()
        if (q.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val loadStart = Clock.System.now()
            setState(WikipediaScreenMode.Search(query = q, results = emptyList(), isLoading = true))
            api.search(q).fold(
                onSuccess = { results ->
                    setState(
                        WikipediaScreenMode.Search(
                            query = q,
                            results = results,
                            isLoading = false,
                        ),
                    )
                },
                onFailure = {
                    showError(NETWORK_ERROR_MESSAGE)
                },
            )
            // Enforce minimum loading display
            val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - loadStart)
            if (remaining.isPositive()) delay(remaining)

            // Persist search query to DataStore
            dataStore.edit { prefs ->
                prefs[WikipediaPreferences.LAST_SEARCH_QUERY] = q
            }
        }
    }

    fun cancelSearch() {
        _uiState.value = _uiState.value.copy(
            mode = WikipediaScreenMode.Home,
            errorModal = null,
            recentArticles = _recentArticles.value,
        )
    }

    fun selectSearchResult(result: WikiSearchResult) {
        openArticle(result.title)
    }

    // === Article view ===

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

            // Fetch summary (description + thumbnail + lead extract + pronunciation) and extract (full text) and links
            val summary = api.fetchSummary(title).getOrNull()
            val extract = api.fetchExtract(title).getOrElse { "" }
            val links = api.fetchLinks(title).getOrElse { emptyList() }

            // Update in-memory recents immediately (source of truth for display).
            // Filter blanks so stale/garbage persisted titles never reappear.
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

            // Enforce minimum loading display
            val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - loadStart)
            if (remaining.isPositive()) delay(remaining)

            // Persist last article title + recent articles to DataStore
            dataStore.edit { prefs ->
                prefs[WikipediaPreferences.LAST_ARTICLE_TITLE] = title
                val cur = ((prefs[WikipediaPreferences.RECENT_ARTICLES]?.toMutableList()
                    ?: mutableListOf()).filter { it.isNotBlank() }).toMutableList()
                cur.remove(title)
                cur.add(0, title)
                prefs[WikipediaPreferences.RECENT_ARTICLES] = cur.filter { it.isNotBlank() }.take(5).toSet()
            }
        }
    }

    fun openLink(title: String) {
        openArticle(title)
    }

    // === About ===

    fun openAbout() {
        _uiState.value = _uiState.value.copy(mode = WikipediaScreenMode.About)
    }

    /** Long-press a word/phrase in an article to look it up (define / disambiguate). */
    fun defineTerm(term: String) {
        val t = term.toString().trim()
        if (t.isEmpty()) return
        submitSearch(t)
    }

    fun closeAbout() {
        _uiState.value = _uiState.value.copy(
            mode = WikipediaScreenMode.Home,
            recentArticles = _recentArticles.value,
        )
    }

    // === Error handling ===

    fun dismissError() {
        val currentMode = _uiState.value.mode
        _uiState.value = _uiState.value.copy(errorModal = null)
        // Return to home if we were in an error state
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
