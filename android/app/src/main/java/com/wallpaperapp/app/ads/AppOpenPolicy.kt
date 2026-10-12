package com.wallpaperapp.app.ads

import java.util.concurrent.TimeUnit

/** Pure decision logic for app-open ads, kept separate so it can be unit tested. */
object AppOpenPolicy {
    /** Google: an app-open ad is only valid for 4 hours after it loaded. */
    val MAX_AD_AGE_MS: Long = TimeUnit.HOURS.toMillis(4)

    data class State(
        val nowMs: Long,
        /** Remote config (app_config.ads). Off by default. */
        val enabled: Boolean,
        val minHoursBetween: Int,
        val adsEnabled: Boolean,
        val canRequestAds: Boolean,
        /** Last time an app-open ad was shown, or when the app was first installed (so a new user's first sessions are ad-free). */
        val lastShownOrInstallMs: Long,
        /** When the cached ad loaded, or null if none is loaded. */
        val adLoadedAtMs: Long?,
        val isShowingFullScreenAd: Boolean,
    )

    fun shouldShow(s: State): Boolean {
        if (!s.enabled || !s.adsEnabled || !s.canRequestAds || s.isShowingFullScreenAd) return false
        val loadedAt = s.adLoadedAtMs ?: return false
        if (s.nowMs - loadedAt > MAX_AD_AGE_MS) return false
        val gapMs = TimeUnit.HOURS.toMillis(s.minHoursBetween.coerceAtLeast(1).toLong())
        return s.nowMs - s.lastShownOrInstallMs >= gapMs
    }

    fun isStale(nowMs: Long, adLoadedAtMs: Long?): Boolean =
        adLoadedAtMs == null || nowMs - adLoadedAtMs > MAX_AD_AGE_MS
}
