package com.wallpaperapp.app.ui.explore

import com.wallpaperapp.app.ui.theme.SoftTopBar
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.AppConfig
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.ui.components.WallpaperGrid
import com.wallpaperapp.app.ui.components.rememberGridAds
import com.wallpaperapp.app.ui.navigation.CollectionRoute
import com.wallpaperapp.app.ui.preview.WallpaperOpener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WallpaperRepository,
    appConfig: AppConfigRepository,
    private val opener: WallpaperOpener,
) : ViewModel() {
    val route = savedStateHandle.toRoute<CollectionRoute>()

    /** Links carry only the id, so look the title up when it wasn't passed in. */
    val title: StateFlow<String> = flow {
        emit(route.title)
        if (route.title.isBlank()) {
            runCatching { repository.collections() }.getOrNull()
                ?.firstOrNull { it.id == route.collectionId }
                ?.let { emit(it.title) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, route.title)
    val config: StateFlow<AppConfig> = appConfig.config
    val wallpapers: Flow<PagingData<Wallpaper>> = repository.collection(route.collectionId).cachedIn(viewModelScope)

    fun onWallpaperOpened(wallpaper: Wallpaper, list: List<Wallpaper>) = opener.open(wallpaper, list)
}

@Composable
fun CollectionScreen(
    onBack: () -> Unit,
    onWallpaperClick: (Wallpaper) -> Unit,
    viewModel: CollectionViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val items = viewModel.wallpapers.collectAsLazyPagingItems()
    Column(Modifier.fillMaxSize()) {
        SoftTopBar(onBack, stringResource(R.string.back), Modifier.statusBarsPadding(), title = title.ifBlank { stringResource(R.string.collection) })
        WallpaperGrid(
            items = items,
            cdnBaseUrl = config.cdnBaseUrl,
            onClick = { wallpaper, list ->
                viewModel.onWallpaperOpened(wallpaper, list)
                onWallpaperClick(wallpaper)
            },
            emptyTitle = stringResource(R.string.collection_empty_title),
            ads = rememberGridAds(),
            emptyBody = stringResource(R.string.collection_empty_body),
        )
    }
}
