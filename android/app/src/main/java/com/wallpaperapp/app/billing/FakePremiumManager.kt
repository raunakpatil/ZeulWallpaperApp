package com.wallpaperapp.app.billing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stand-in until Dev A's RevenueCat implementation (A10). Purchases always succeed and
 * only live in memory, so the paywall and premium gating can be built and tested.
 */
@Singleton
class FakePremiumManager @Inject constructor() : PremiumManager {

    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    override suspend fun initialize(userId: String?) = Unit

    override suspend fun getPlans(): List<Plan> = listOf(
        Plan(PlanType.MONTHLY, price = "₹99", trialDays = null, pricePerMonth = "₹99"),
        Plan(PlanType.ANNUAL, price = "₹599", trialDays = 7, pricePerMonth = "₹49.92"),
    )

    override suspend fun purchase(activity: Activity, plan: PlanType): PurchaseResult {
        _isPremium.value = true
        return PurchaseResult.Success
    }

    override suspend fun restore(): Boolean = _isPremium.value

    override fun manageSubscriptionIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))
}
