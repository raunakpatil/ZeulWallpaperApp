package com.wallpaperapp.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.R
import com.wallpaperapp.app.billing.Plan
import com.wallpaperapp.app.billing.PlanType
import com.wallpaperapp.app.ui.paywall.PaywallContent
import com.wallpaperapp.app.ui.paywall.PaywallUiState
import com.wallpaperapp.app.ui.theme.WallpaperTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI test for the paywall. */
@RunWith(AndroidJUnit4::class)
class PaywallContentTest {

    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val plans = listOf(
        Plan(PlanType.MONTHLY, "₹99", trialDays = null, pricePerMonth = "₹99"),
        Plan(PlanType.ANNUAL, "₹599", trialDays = 7, pricePerMonth = "₹49.92"),
    )

    @Test
    fun annualIsHighlightedWithTrialAndTerms() {
        var purchased: PlanType? = null
        compose.setContent {
            WallpaperTheme {
                PaywallContent(
                    state = PaywallUiState(loading = false, plans = plans, selected = PlanType.ANNUAL, highlighted = PlanType.ANNUAL),
                    onClose = {}, onSelect = {}, onPurchase = { purchased = PlanType.ANNUAL }, onRestore = {}, onManage = {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.save_percent, 49)).assertIsDisplayed()
        val trialButton = context.resources.getQuantityString(R.plurals.start_trial, 7, 7)
        compose.onNodeWithText(trialButton).performScrollTo().performClick()
        assertThat(purchased).isEqualTo(PlanType.ANNUAL)
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.trial_terms_year, 7, 7, "₹599")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.restore_purchases)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosingMonthlyChangesTheButtonAndTerms() {
        var selected: PlanType? = null
        compose.setContent {
            WallpaperTheme {
                PaywallContent(
                    state = PaywallUiState(loading = false, plans = plans, selected = PlanType.MONTHLY, highlighted = PlanType.ANNUAL),
                    onClose = {}, onSelect = { selected = it }, onPurchase = {}, onRestore = {}, onManage = {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.continue_)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.renew_terms_month, "₹99")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.plan_annual)).performScrollTo().performClick()
        assertThat(selected).isEqualTo(PlanType.ANNUAL)
    }

    @Test
    fun premiumUsersSeeManageInsteadOfPlans() {
        var managed = 0
        compose.setContent {
            WallpaperTheme {
                PaywallContent(
                    state = PaywallUiState(loading = false, plans = plans, isPremium = true),
                    onClose = {}, onSelect = {}, onPurchase = {}, onRestore = {}, onManage = { managed++ },
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.paywall_already_premium)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.manage_subscription)).performClick()
        assertThat(managed).isEqualTo(1)
    }
}
