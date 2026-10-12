package com.wallpaperapp.app.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.Period
import com.wallpaperapp.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real subscriptions through RevenueCat + Google Play Billing (A10).
 *
 * RevenueCat setup (see CONTRACTS section 5 "PremiumManager behaviour"):
 *  - entitlement `premium`, current offering with the standard `$rc_monthly` and `$rc_annual` packages
 *  - free trials are configured as offers on the Play Console base plans; RevenueCat reports them per package
 *  - app user id = Supabase user id when signed in, RevenueCat anonymous id otherwise
 * The server mirrors the entitlement into profiles.is_premium through the revenuecat-webhook function (A11).
 */
@Singleton
class RevenueCatPremiumManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : PremiumManager {

    private val _isPremium = MutableStateFlow(false)
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val lock = Mutex()
    private var configured = false
    private var managementUrl: Uri? = null

    override suspend fun initialize(userId: String?) {
        lock.withLock { identify(userId) }
    }

    private suspend fun identify(userId: String?) {
        try {
            if (!configured) {
                Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
                Purchases.configure(
                    PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_KEY).appUserID(userId).build(),
                )
                Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener(::update)
                configured = true
            } else {
                val purchases = Purchases.sharedInstance
                when {
                    userId != null && purchases.appUserID != userId -> update(purchases.awaitLogIn(userId).customerInfo)
                    userId == null && !purchases.isAnonymous -> update(purchases.awaitLogOut())
                }
            }
            update(Purchases.sharedInstance.awaitCustomerInfo())
        } catch (e: PurchasesException) {
            Log.w(TAG, "RevenueCat init/identify failed: ${e.error.message}")
        }
    }

    private fun update(info: CustomerInfo) {
        _isPremium.value = info.entitlements[ENTITLEMENT]?.isActive == true
        managementUrl = info.managementURL
    }

    override suspend fun getPlans(): List<Plan> {
        if (!configured) return emptyList()
        val offering = try {
            Purchases.sharedInstance.awaitOfferings().current
        } catch (e: PurchasesException) {
            Log.w(TAG, "Offerings failed: ${e.error.message}")
            null
        } ?: return emptyList()
        return listOfNotNull(
            offering.monthly?.let { it.toPlan(PlanType.MONTHLY) },
            offering.annual?.let { it.toPlan(PlanType.ANNUAL) },
        )
    }

    private fun Package.toPlan(type: PlanType): Plan {
        val p = product
        return Plan(
            type = type,
            price = p.price.formatted,
            trialDays = p.subscriptionOptions?.freeTrial?.freePhase?.billingPeriod?.toDays(),
            pricePerMonth = p.pricePerMonth()?.formatted ?: p.price.formatted,
        )
    }

    override suspend fun purchase(activity: Activity, plan: PlanType): PurchaseResult {
        if (!configured) return PurchaseResult.Error("Subscriptions aren't ready yet. Try again in a moment.")
        return try {
            val offering = Purchases.sharedInstance.awaitOfferings().current
                ?: return PurchaseResult.Error("No subscription plans are available right now.")
            val pkg = (if (plan == PlanType.MONTHLY) offering.monthly else offering.annual)
                ?: return PurchaseResult.Error("That plan isn't available right now.")
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            update(result.customerInfo)
            if (_isPremium.value) PurchaseResult.Success
            else PurchaseResult.Error("Payment received, but premium isn't active yet. Use Restore purchases in a minute.")
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseResult.Cancelled else PurchaseResult.Error(e.error.message)
        } catch (e: PurchasesException) {
            PurchaseResult.Error(e.error.message)
        }
    }

    override suspend fun restore(): Boolean {
        if (!configured) return false
        return try {
            update(Purchases.sharedInstance.awaitRestore())
            _isPremium.value
        } catch (e: PurchasesException) {
            Log.w(TAG, "Restore failed: ${e.error.message}")
            false
        }
    }

    override fun manageSubscriptionIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        managementUrl ?: Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}"),
    )

    private companion object {
        const val TAG = "RevenueCatPremium"
        const val ENTITLEMENT = "premium"
    }
}

/** Trial length in days (weeks/months approximated as 7/30 days, which is how Play shows them). */
internal fun Period.toDays(): Int = when (unit) {
    Period.Unit.DAY -> value
    Period.Unit.WEEK -> value * 7
    Period.Unit.MONTH -> value * 30
    Period.Unit.YEAR -> value * 365
    else -> value
}
