package com.wallpaperapp.app.ui.favorites

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import com.wallpaperapp.app.ui.common.UiText
import com.wallpaperapp.app.domain.model.ApplyTarget
import com.wallpaperapp.app.domain.model.AutoChangerSettings
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.work.AutoChangerScheduler
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.SoftSegmented
import com.wallpaperapp.app.ui.theme.SoftScreenTitle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.wallpaperapp.app.R
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.DownloadRepository
import com.wallpaperapp.app.data.repository.FavoritesRepository
import com.wallpaperapp.app.domain.model.AppConfig
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.ui.components.WallpaperGrid
import com.wallpaperapp.app.ui.preview.WallpaperOpener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    favorites: FavoritesRepository,
    downloads: DownloadRepository,
    appConfig: AppConfigRepository,
    private val opener: WallpaperOpener,
    private val preferences: UserPreferences,
    private val scheduler: AutoChangerScheduler,
    premiumManager: PremiumManager,
) : ViewModel() {
    val config: StateFlow<AppConfig> = appConfig.config

    /** The auto-change row (B18): same settings as Profile → Auto wallpaper changer, favourites as the source. */
    val autoChange: StateFlow<FavAutoChange> =
        combine(preferences.autoChanger, premiumManager.isPremium, favorites.favoriteIds) { settings, premium, ids ->
            FavAutoChange(
                on = settings.enabled && settings.source == AutoChangerSettings.SOURCE_FAVORITES,
                premium = premium,
                favouriteCount = ids.size,
                intervalHours = settings.intervalHours,
                target = settings.target,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavAutoChange())

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    /** On = favourites become the source (whatever Profile had); the scheduler picks the change up. */
    fun setAutoChange(on: Boolean) {
        viewModelScope.launch {
            preferences.updateAutoChanger { if (on) it.copy(enabled = true, source = AutoChangerSettings.SOURCE_FAVORITES) else it.copy(enabled = false) }
        }
    }

    fun setInterval(hours: Int) = viewModelScope.launch { preferences.updateAutoChanger { it.copy(intervalHours = hours) } }

    fun setTarget(target: ApplyTarget) = viewModelScope.launch { preferences.updateAutoChanger { it.copy(target = target) } }

    fun changeNow() {
        scheduler.runNow()
        _message.value = UiText.Res(R.string.changing_wallpaper)
    }

    fun messageShown() {
        _message.value = null
    }

    /** Room-backed, so both work offline. */
    val favourites: Flow<PagingData<Wallpaper>> = favorites.pagedFavorites().cachedIn(viewModelScope)

    /** Download history, newest first: re-apply without another ad. */
    val downloads: Flow<PagingData<Wallpaper>> = downloads.downloads().map { PagingData.from(it) }.cachedIn(viewModelScope)

    fun onWallpaperOpened(wallpaper: Wallpaper, list: List<Wallpaper>) = opener.open(wallpaper, list)
}

@Composable
fun FavouritesScreen(onWallpaperClick: (Wallpaper) -> Unit, onGoPremium: () -> Unit, viewModel: FavouritesViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val autoChange by viewModel.autoChange.collectAsStateWithLifecycle()
    var showAutoChangeSheet by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val message = viewModel.message.collectAsStateWithLifecycle().value?.asString()
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val open: (Wallpaper, List<Wallpaper>) -> Unit = { wallpaper, list ->
        viewModel.onWallpaperOpened(wallpaper, list)
        onWallpaperClick(wallpaper)
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SoftScreenTitle(stringResource(R.string.nav_favourites))
            SoftSegmented(
                options = listOf(stringResource(R.string.favourites_tab), stringResource(R.string.downloads_tab)),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
            if (tab == 0) {
                // Auto-change lives with the wallpapers it rotates (B18).
                AutoChangeRow(
                    state = autoChange,
                    onToggle = viewModel::setAutoChange,
                    onOpenSettings = { showAutoChangeSheet = true },
                    onGoPremium = onGoPremium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
                )
                WallpaperGrid(
                    items = viewModel.favourites.collectAsLazyPagingItems(),
                    cdnBaseUrl = config.cdnBaseUrl,
                    onClick = open,
                    emptyTitle = stringResource(R.string.favourites_empty_title),
                    emptyBody = stringResource(R.string.favourites_empty_body),
                    emptyIcon = ZeulIcons.Heart,
                )
            } else {
                WallpaperGrid(
                    items = viewModel.downloads.collectAsLazyPagingItems(),
                    cdnBaseUrl = config.cdnBaseUrl,
                    onClick = open,
                    emptyTitle = stringResource(R.string.downloads_empty_title),
                    emptyBody = stringResource(R.string.downloads_empty_body),
                    emptyIcon = ZeulIcons.Download,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    if (showAutoChangeSheet) {
        AutoChangeSheet(
            state = autoChange,
            onInterval = { viewModel.setInterval(it) },
            onTarget = { viewModel.setTarget(it) },
            onChangeNow = { showAutoChangeSheet = false; viewModel.changeNow() },
            onDismiss = { showAutoChangeSheet = false },
        )
    }
}
