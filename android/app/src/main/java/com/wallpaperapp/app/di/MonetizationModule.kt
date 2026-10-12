package com.wallpaperapp.app.di

import com.wallpaperapp.app.BuildConfig
import com.wallpaperapp.app.ads.AdMobAdsManager
import com.wallpaperapp.app.ads.AdsManager
import com.wallpaperapp.app.billing.FakePremiumManager
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.billing.RevenueCatPremiumManager
import com.wallpaperapp.app.billing.UnavailablePremiumManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MonetizationModule {

    @Binds
    abstract fun bindAdsManager(impl: AdMobAdsManager): AdsManager

    companion object {
        /**
         * Real RevenueCat whenever REVENUECAT_KEY is set. Without a key: the free-granting fake in
         * debug builds only (for testing the paywall), and a "not available" manager in release,
         * so a release build can never hand out premium for free.
         */
        @Provides
        @Singleton
        fun providePremiumManager(
            revenueCat: Provider<RevenueCatPremiumManager>,
            fake: Provider<FakePremiumManager>,
            unavailable: Provider<UnavailablePremiumManager>,
        ): PremiumManager = premiumManagerFor(BuildConfig.REVENUECAT_KEY, BuildConfig.DEBUG, revenueCat, fake, unavailable)

        internal fun premiumManagerFor(
            key: String,
            debug: Boolean,
            revenueCat: Provider<out PremiumManager>,
            fake: Provider<out PremiumManager>,
            unavailable: Provider<out PremiumManager>,
        ): PremiumManager = when {
            key.isNotBlank() -> revenueCat.get()
            debug -> fake.get()
            else -> unavailable.get()
        }
    }
}
