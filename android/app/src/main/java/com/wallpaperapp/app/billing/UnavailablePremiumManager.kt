package com.wallpaperapp.app.billing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Bound in RELEASE builds that have no REVENUECAT_KEY, so a misconfigured build can never fall back
 * to [FakePremiumManager] (which grants premium for free). Nobody is premium; the paywall shows
 * "Retry loading plans" and purchases explain that subscriptions aren't available.
 */
class UnavailablePremiumManager @Inject constructor() : PremiumManager {
    override val isPremium: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun initialize(userId: String?) = Unit
    override suspend fun getPlans(): List<Plan> = emptyList()
    override suspend fun purchase(activity: Activity, plan: PlanType): PurchaseResult =
        PurchaseResult.Error("Subscriptions aren't available yet.")
    override suspend fun restore(): Boolean = false
    override fun manageSubscriptionIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
}
