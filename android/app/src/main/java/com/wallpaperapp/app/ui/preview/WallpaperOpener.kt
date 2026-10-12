package com.wallpaperapp.app.ui.preview

import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.di.ApplicationScope
import com.wallpaperapp.app.domain.model.Wallpaper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What every grid does before opening the preview: share its list and keep an offline copy. */
@Singleton
class WallpaperOpener @Inject constructor(
    private val session: PreviewSession,
    private val repository: WallpaperRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun open(wallpaper: Wallpaper, visibleList: List<Wallpaper>) {
        session.open(visibleList, wallpaper)
        scope.launch { repository.cache(wallpaper) }
    }
}
