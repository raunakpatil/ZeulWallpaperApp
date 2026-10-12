package com.wallpaperapp.app.ads

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wallpaperapp.app.R
import com.wallpaperapp.app.billing.PremiumManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Stand-in until Dev A's AdMob implementation (A9). Rewarded ads are "watched" instantly,
 * and the native slot is a labelled placeholder tile.
 */
@Singleton
class FakeAdsManager @Inject constructor(
    premiumManager: PremiumManager,
) : AdsManager {

    override val adsEnabled: StateFlow<Boolean> = premiumManager.isPremium
        .map { !it }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, true)

    override suspend fun initialize(activity: Activity) = Unit

    override fun preloadRewarded() = Unit

    override fun showRewarded(activity: Activity, onRewarded: () -> Unit, onUnavailable: () -> Unit) {
        onRewarded()
    }

    @Composable
    override fun NativeAdSlot(modifier: Modifier) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.ad_badge), style = MaterialTheme.typography.labelMedium)
        }
    }

    override fun maybeShowAppOpen(activity: Activity) = Unit

    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)

    override fun showPrivacyOptions(activity: Activity) = Unit
}
