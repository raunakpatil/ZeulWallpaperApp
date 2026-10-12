package com.wallpaperapp.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.PopularRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class StylePickerState(
    val loading: Boolean = true,
    /** Tag slugs to choose from, most common first. Empty = nothing to pick yet. */
    val styles: List<String> = emptyList(),
    val selected: Set<String> = emptySet(),
    val done: Boolean = false,
    /** Popular wallpapers for the landing page's full-bleed photo; empty = plain dark page. */
    val heroes: List<Wallpaper> = emptyList(),
    val cdnBaseUrl: String = "",
) {
    val canContinue: Boolean get() = selected.size >= MIN_PICKS

    companion object {
        const val MIN_PICKS = 3
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    private val preferences: UserPreferences,
    appConfig: AppConfigRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(StylePickerState())
    val state: StateFlow<StylePickerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val heroes = try {
                repository.popularSample(PopularRange.WEEK, size = 5)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(heroes = heroes) }
        }
        viewModelScope.launch {
            appConfig.config.collect { config -> _state.update { it.copy(cdnBaseUrl = config.cdnBaseUrl) } }
        }
        viewModelScope.launch {
            val current = repository.seedTags.first()
            // The most-used tags across popular wallpapers: real content, not one-off upload tags.
            val styles = try {
                repository.trendingTags(limit = 24, range = PopularRange.ALL, sample = 100)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            val clean = styles.filter { STYLE_TAG.matches(it) } // skip broken tags like "back…" from uploads
            _state.update { it.copy(loading = false, styles = (current.toList() + clean).distinct(), selected = current) }
        }
    }

    private companion object {
        /** A plain word or hyphenated phrase; anything else isn't worth offering as a style. */
        val STYLE_TAG = Regex("^[a-z][a-z0-9-]{1,23}$")
    }

    fun toggle(style: String) = _state.update {
        it.copy(selected = if (style in it.selected) it.selected - style else it.selected + style)
    }

    /** Saves the picks (For You uses them as `seed_tags`) and never shows onboarding again. */
    fun finish() {
        viewModelScope.launch {
            repository.setSeedTags(_state.value.selected)
            preferences.setOnboardingDone()
            _state.update { it.copy(done = true) }
        }
    }

    /** Skipping keeps any earlier picks and still counts as done. */
    fun skip() {
        viewModelScope.launch {
            preferences.setOnboardingDone()
            _state.update { it.copy(done = true) }
        }
    }
}
