package com.wallpaperapp.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.ads.AdsManager
import com.wallpaperapp.app.data.repository.AppConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Where native ads go in a grid: one after every [every] wallpapers (`app_config.ads.native_every`).
 * Positions are grid slots; wallpaper indexes are positions in the paged list.
 */
data class AdLayout(val every: Int) {
    private val block get() = every + 1

    fun slotCount(wallpapers: Int): Int = if (every <= 0) wallpapers else wallpapers + wallpapers / every

    fun isAd(position: Int): Boolean = every > 0 && position % block == every

    /** Which ad (0, 1, 2…) sits at [position]; only valid when [isAd]. */
    fun adIndex(position: Int): Int = position / block

    fun wallpaperIndex(position: Int): Int = if (every <= 0) position else position - position / block

    companion object {
        val None = AdLayout(0)
    }
}

/** Ads for grids: nothing for premium users or when the dashboard turns them off. */
class GridAds(val layout: AdLayout, val slot: @Composable (Modifier) -> Unit)

@HiltViewModel
class GridAdsViewModel @Inject constructor(
    val adsManager: AdsManager,
    appConfig: AppConfigRepository,
) : ViewModel() {
    val layout: StateFlow<AdLayout> = combine(adsManager.adsEnabled, appConfig.config) { enabled, config ->
        if (enabled && config.ads.nativeEvery > 0) AdLayout(config.ads.nativeEvery) else AdLayout.None
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdLayout.None)
}

@Composable
fun rememberGridAds(viewModel: GridAdsViewModel = hiltViewModel()): GridAds {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    return GridAds(layout) { modifier -> viewModel.adsManager.NativeAdSlot(modifier) }
}
