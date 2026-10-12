package com.wallpaperapp.app.ui.paywall

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.billing.Plan
import com.wallpaperapp.app.billing.PlanType
import org.junit.Test
import com.wallpaperapp.app.R
import com.wallpaperapp.app.ui.common.UiText

class PaywallTextTest {

    private val monthly = Plan(PlanType.MONTHLY, "₹99", trialDays = null, pricePerMonth = "₹99")
    private val annual = Plan(PlanType.ANNUAL, "₹599", trialDays = 7, pricePerMonth = "₹49.92")

    @Test
    fun `annual saving is computed against twelve months`() {
        assertThat(annualSavings(listOf(monthly, annual))).isEqualTo(49) // 599 vs 1188
    }

    @Test
    fun `no saving badge when prices can't be compared`() {
        assertThat(annualSavings(listOf(annual))).isNull()
        assertThat(annualSavings(listOf(monthly.copy(price = "free"), annual))).isNull()
    }

    @Test
    fun `prices parse with currency symbols and separators`() {
        assertThat(parsePrice("₹1,299.00")).isEqualTo(1299.0)
        assertThat(parsePrice("$4.99")).isEqualTo(4.99)
    }

    @Test
    fun `trial terms carry the price, trial length and period`() {
        assertThat(trialTerms(annual)).isEqualTo(UiText.Plural(R.plurals.trial_terms_year, 7, listOf(7, "₹599")))
        assertThat(trialTerms(monthly)).isEqualTo(UiText.Res(R.string.renew_terms_month, listOf("₹99")))
    }
}
