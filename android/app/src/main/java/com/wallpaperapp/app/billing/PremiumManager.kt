package com.wallpaperapp.app.billing

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

// Contract: docs/CONTRACTS.md section 5. Do not change without a CONTRACT CHANGE commit.
enum class PlanType { MONTHLY, ANNUAL }
data class Plan(val type: PlanType, val price: String, val trialDays: Int?, val pricePerMonth: String)
sealed interface PurchaseResult { object Success : PurchaseResult; object Cancelled : PurchaseResult; data class Error(val msg: String) : PurchaseResult }

interface PremiumManager {
    val isPremium: StateFlow<Boolean>
    suspend fun initialize(userId: String?)                 // call again on login/logout
    suspend fun getPlans(): List<Plan>
    suspend fun purchase(activity: Activity, plan: PlanType): PurchaseResult
    suspend fun restore(): Boolean
    fun manageSubscriptionIntent(): Intent
}
