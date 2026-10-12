package com.wallpaperapp.app.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.domain.model.SearchFilters
import com.wallpaperapp.app.domain.model.SearchSuggestion
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.ui.navigation.SearchRoute
import com.wallpaperapp.app.ui.preview.WallpaperOpener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class SearchUiState(
    val text: String = "",
    val submitted: String? = null,
    val filters: SearchFilters = SearchFilters(),
    val suggestions: List<SearchSuggestion> = emptyList(),
    val recent: List<String> = emptyList(),
    val trending: List<String> = emptyList(),
    val categories: List<Category> = emptyList(),
    val cdnBaseUrl: String = "",
) {
    /** Results show once there's a query or any filter; otherwise the recent/trending start page. */
    val showResults: Boolean get() = !submitted.isNullOrBlank() || filters != SearchFilters()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WallpaperRepository,
    private val preferences: UserPreferences,
    private val opener: WallpaperOpener,
    appConfig: AppConfigRepository,
) : ViewModel() {

    // Read by name rather than toRoute(): same keys as SearchRoute's properties, but it also works
    // in JVM unit tests, where toRoute's Bundle decoding isn't available.
    private val route = SearchRoute(
        query = savedStateHandle.get<String>("query"),
        category = savedStateHandle.get<String>("category"),
        color = savedStateHandle.get<String>("color"),
    )

    private val text = MutableStateFlow(route.query.orEmpty())
    private val submitted = MutableStateFlow(route.query)
    private val filters = MutableStateFlow(SearchFilters(color = route.color, category = route.category))
    private val trending = MutableStateFlow<List<String>>(emptyList())
    private val categories = MutableStateFlow<List<Category>>(emptyList())

    private val suggestions: Flow<List<SearchSuggestion>> = combine(text, submitted) { t, s -> t to s }
        .debounce(200)
        .distinctUntilChanged()
        .mapLatest { (t, s) ->
            // Nothing to suggest for very short input, or when the text is what's already searched.
            if (t.trim().length < 2 || t == s) emptyList() else safely(emptyList()) { repository.suggest(t.trim()) }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        combine(text, submitted, filters, ::Triple),
        suggestions,
        preferences.recentSearches,
        combine(trending, categories, ::Pair),
        appConfig.config,
    ) { (t, s, f), sugg, recent, (trend, cats), config ->
        SearchUiState(t, s, f, sugg, recent, trend, cats, config.cdnBaseUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(text.value, submitted.value, filters.value))

    val results: Flow<PagingData<Wallpaper>> = combine(submitted, filters) { q, f -> q to f }
        .distinctUntilChanged()
        .flatMapLatest { (q, f) ->
            if (q.isNullOrBlank() && f == SearchFilters()) flowOf(PagingData.empty()) else repository.search(q, f)
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch { trending.value = safely(emptyList()) { repository.trendingTags() } }
        viewModelScope.launch { categories.value = safely(emptyList()) { repository.categories() } }
    }

    fun onTextChange(value: String) {
        text.value = value
    }

    fun submit(query: String = text.value) {
        val q = query.trim()
        text.value = q
        submitted.value = q.ifEmpty { null }
        if (q.isNotEmpty()) viewModelScope.launch { preferences.addRecentSearch(q) }
    }

    fun onSuggestion(suggestion: SearchSuggestion) {
        val category = categories.value.firstOrNull { it.name.equals(suggestion.label, ignoreCase = true) }
        if (suggestion.kind == SearchSuggestion.Kind.CATEGORY && category != null) {
            // Browsing a category is a filter, not a word search.
            text.value = ""
            submitted.value = null
            filters.update { it.copy(category = category.slug) }
        } else {
            submit(suggestion.label)
        }
    }

    fun setColor(color: String?) = filters.update { it.copy(color = color) }
    fun setDevice(device: String?) = filters.update { it.copy(device = device) }
    fun setAmoled(amoled: Boolean?) = filters.update { it.copy(amoled = amoled) }
    fun setCategory(slug: String?) = filters.update { it.copy(category = slug) }
    fun clearFilters() = filters.update { SearchFilters() }

    fun clearRecent() {
        viewModelScope.launch { preferences.clearRecentSearches() }
    }

    fun onWallpaperOpened(wallpaper: Wallpaper, list: List<Wallpaper>) = opener.open(wallpaper, list)

    private suspend fun <T> safely(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallback
    }
}
