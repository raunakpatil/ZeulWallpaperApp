package com.wallpaperapp.app.di

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.billing.FakePremiumManager
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.billing.UnavailablePremiumManager
import javax.inject.Provider
import org.junit.Test

class PremiumBindingTest {
    private val real = object : PremiumManager by UnavailablePremiumManager() {}
    private val fake = FakePremiumManager()
    private val unavailable = UnavailablePremiumManager()
    private fun pick(key: String, debug: Boolean) = MonetizationModule.premiumManagerFor(
        key, debug, Provider { real }, Provider { fake }, Provider { unavailable },
    )

    @Test fun `a RevenueCat key always selects the real manager`() {
        assertThat(pick("goog_abc", debug = true)).isSameInstanceAs(real)
        assertThat(pick("goog_abc", debug = false)).isSameInstanceAs(real)
    }

    @Test fun `debug without a key uses the fake for testing`() {
        assertThat(pick("", debug = true)).isSameInstanceAs(fake)
    }

    @Test fun `release without a key can never use the free fake`() {
        assertThat(pick("", debug = false)).isSameInstanceAs(unavailable)
        assertThat(pick("   ", debug = false)).isSameInstanceAs(unavailable)
    }
}
