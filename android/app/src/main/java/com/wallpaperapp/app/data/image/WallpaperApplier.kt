package com.wallpaperapp.app.data.image

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.wallpaperapp.app.domain.model.ApplyTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperApplier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** What the launcher asks for (tablets: usually square, to cover both orientations); 0 = no preference. */
    fun desiredSize(): Pair<Int, Int> {
        val manager = WallpaperManager.getInstance(context)
        return manager.desiredMinimumWidth.coerceAtLeast(0) to manager.desiredMinimumHeight.coerceAtLeast(0)
    }

    /** [visible] marks the part the user framed, so Android shows it in the current orientation. */
    suspend fun apply(bitmap: Bitmap, target: ApplyTarget, visible: Rect? = null) = withContext(Dispatchers.IO) {
        val flags = when (target) {
            ApplyTarget.HOME -> WallpaperManager.FLAG_SYSTEM
            ApplyTarget.LOCK -> WallpaperManager.FLAG_LOCK
            ApplyTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        WallpaperManager.getInstance(context).setBitmap(bitmap, visible, true, flags)
    }
}
