package com.wallpaperapp.app.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.data.repository.FavoritesRepository
import com.wallpaperapp.app.domain.model.Wallpaper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Heart state + toggle for the grid cards (same repository call as the preview's heart). */
@HiltViewModel
class TileActionsViewModel @Inject constructor(
    private val favorites: FavoritesRepository,
) : ViewModel() {
    val favoriteIds: StateFlow<Set<String>> =
        favorites.favoriteIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch { favorites.toggle(wallpaper) }
    }
}
