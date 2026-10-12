package com.wallpaperapp.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.WallpaperCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class ExploreUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val categories: List<Category> = emptyList(),
    val collections: List<WallpaperCollection> = emptyList(),
    /** Cover wallpaper per category slug; filled in after the categories show. */
    val covers: Map<String, Wallpaper> = emptyMap(),
    val cdnBaseUrl: String = "",
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    appConfig: AppConfigRepository,
) : ViewModel() {

    private val content = MutableStateFlow(ExploreUiState())

    val uiState: StateFlow<ExploreUiState> = combine(content, appConfig.config) { state, config ->
        state.copy(cdnBaseUrl = config.cdnBaseUrl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    init {
        load()
    }

    fun load() {
        content.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            try {
                val categories = async { repository.categories() }
                // Collections are optional: Explore still works if there are none or the call fails.
                val collections = async { runCatching { repository.collections() }.getOrDefault(emptyList()) }
                content.update { it.copy(loading = false, categories = categories.await(), collections = collections.await()) }
                loadCovers(content.value.categories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                content.update { it.copy(loading = false, error = true) }
            }
        }
    }

    private fun loadCovers(categories: List<Category>) {
        categories.filter { it.slug !in content.value.covers }.forEach { category ->
            viewModelScope.launch {
                val cover = runCatching { repository.categoryCover(category.slug) }.getOrNull() ?: return@launch
                content.update { it.copy(covers = it.covers + (category.slug to cover)) }
            }
        }
    }
}
