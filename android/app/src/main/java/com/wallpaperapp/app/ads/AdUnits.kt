package com.wallpaperapp.app.ads

import com.wallpaperapp.app.BuildConfig

/**
 * AdMob ad unit ids.
 * Debug builds ALWAYS use Google's public test units, so real ads can never be clicked during
 * development (that gets AdMob accounts banned). Release builds use the ids from local.properties
 * (ADMOB_REWARDED_ID / ADMOB_NATIVE_ID / ADMOB_APP_OPEN_ID), falling back to test units if they're unset.
 */
object AdUnits {
    // https://developers.google.com/admob/android/test-ads#sample_ad_units
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"

    val rewarded: String get() = pick(BuildConfig.ADMOB_REWARDED_ID, TEST_REWARDED)
    val native: String get() = pick(BuildConfig.ADMOB_NATIVE_ID, TEST_NATIVE)
    val appOpen: String get() = pick(BuildConfig.ADMOB_APP_OPEN_ID, TEST_APP_OPEN)

    val usingTestAds: Boolean get() = BuildConfig.DEBUG || BuildConfig.ADMOB_REWARDED_ID.isBlank()

    private fun pick(configured: String, test: String) =
        if (BuildConfig.DEBUG || configured.isBlank()) test else configured
}
