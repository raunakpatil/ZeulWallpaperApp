package com.wallpaperapp.app.ads

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

// Contract: docs/CONTRACTS.md section 5. Do not change without a CONTRACT CHANGE commit.
interface AdsManager {
    suspend fun initialize(activity: Activity)              // UMP consent + MobileAds init
    fun preloadRewarded()
    fun showRewarded(
        activity: Activity,
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit                           // ad failed → caller unlocks anyway
    )
    @Composable fun NativeAdSlot(modifier: Modifier)        // grid-tile styled native ad
    fun maybeShowAppOpen(activity: Activity)
    val adsEnabled: StateFlow<Boolean>                      // false for premium users

    // v1.2 (additive): GDPR/UK users must be able to change their ad consent later.
    // Show a "Privacy settings" row in Profile/Settings when this is true.
    val privacyOptionsRequired: StateFlow<Boolean>
    fun showPrivacyOptions(activity: Activity)
}
