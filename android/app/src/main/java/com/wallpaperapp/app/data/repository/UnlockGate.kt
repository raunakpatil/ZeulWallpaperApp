package com.wallpaperapp.app.data.repository

import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.domain.model.Wallpaper
import javax.inject.Inject
import javax.inject.Singleton

/** Whether Download/Apply can go ahead, or the unlock sheet must be shown first. */
sealed interface UnlockDecision {
    data object Allowed : UnlockDecision

    /** [canWatchAd] is false for premium-only wallpapers: only a subscription unlocks those. */
    data class NeedsUnlock(val canWatchAd: Boolean) : UnlockDecision
}

/** The unlock decision, as a pure function so it can be tested on its own. */
object UnlockPolicy {
    fun decide(
        userIsPremium: Boolean,
        wallpaperIsPremium: Boolean,
        alreadyUnlocked: Boolean,
        alreadyDownloaded: Boolean,
    ): UnlockDecision = when {
        userIsPremium -> UnlockDecision.Allowed
        wallpaperIsPremium -> UnlockDecision.NeedsUnlock(canWatchAd = false)
        // Download history counts: re-applying something you already have never costs another ad.
        alreadyUnlocked || alreadyDownloaded -> UnlockDecision.Allowed
        else -> UnlockDecision.NeedsUnlock(canWatchAd = true)
    }
}

@Singleton
class UnlockGate @Inject constructor(
    private val premiumManager: PremiumManager,
    private val unlocks: UnlockRepository,
    private val downloads: DownloadRepository,
) {
    suspend fun check(wallpaper: Wallpaper): UnlockDecision = UnlockPolicy.decide(
        userIsPremium = premiumManager.isPremium.value,
        wallpaperIsPremium = wallpaper.isPremium,
        alreadyUnlocked = unlocks.isUnlockedNow(wallpaper.id),
        alreadyDownloaded = downloads.isDownloaded(wallpaper.id),
    )

    /** Records a rewarded-ad unlock (respects `app_config.unlock_mode`). */
    suspend fun grant(wallpaper: Wallpaper) = unlocks.unlock(wallpaper)
}
