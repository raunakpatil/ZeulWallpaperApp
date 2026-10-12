package com.wallpaperapp.app.work

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.remote.WallpaperRemoteDataSource
import com.wallpaperapp.app.data.repository.FavoritesRepository
import com.wallpaperapp.app.data.repository.WallpaperActions
import com.wallpaperapp.app.domain.model.AutoChangerPicker
import com.wallpaperapp.app.domain.model.AutoChangerSettings
import com.wallpaperapp.app.domain.model.CropMath
import com.wallpaperapp.app.domain.model.EditParams
import com.wallpaperapp.app.domain.model.SearchFilters
import com.wallpaperapp.app.domain.model.Wallpaper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlin.math.max
import kotlin.coroutines.cancellation.CancellationException

/** Sets the next wallpaper for the auto changer (premium perk). Runs periodically or once ("Change now"). */
@HiltWorker
class AutoChangerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferences: UserPreferences,
    private val premiumManager: PremiumManager,
    private val favorites: FavoritesRepository,
    private val remote: WallpaperRemoteDataSource,
    private val actions: WallpaperActions,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = preferences.autoChanger.first()
        // The scheduler cancels the job when premium ends; this is the belt-and-braces check.
        if (!settings.enabled || !premiumManager.isPremium.value && !preferences.lastKnownPremium()) return Result.success()
        return try {
            val candidates = candidates(settings)
            val next = AutoChangerPicker.pick(candidates, settings.lastWallpaperId) ?: return Result.success() // nothing to pick yet
            val (width, height) = screenSize()
            val source = actions.loadForEditing(next, maxLongEdge = max(width, height))
            val crop = CropMath.crop(0f, 0f, 1f, source.width, source.height, width.toFloat(), height.toFloat())
            try {
                actions.setOnScreen(source, EditParams(), crop, width, height, settings.target)
            } finally {
                source.recycle()
            }
            preferences.updateAutoChanger { it.copy(lastWallpaperId = next.id) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "auto change failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun candidates(settings: AutoChangerSettings): List<Wallpaper> {
        settings.categorySlug?.let { slug ->
            return remote.search(null, SearchFilters(category = slug), page = 0, pageSize = 60)
        }
        return when (settings.source) {
            AutoChangerSettings.SOURCE_FOR_YOU -> {
                val seeds = preferences.seedTags.first().toList().ifEmpty { null }
                remote.getForYou(page = 0, pageSize = 60, seedTags = seeds)
            }
            else -> favorites.favorites().first()
        }
    }

    /** Full screen size in pixels, the shape the wallpaper is cropped to. */
    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val wm = applicationContext.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val size = android.graphics.Point()
            wm.defaultDisplay.getRealSize(size)
            size.x to size.y
        }
    }

    companion object {
        const val TAG = "AutoChangerWorker"
    }
}
