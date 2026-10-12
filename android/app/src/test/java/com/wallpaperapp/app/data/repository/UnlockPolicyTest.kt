package com.wallpaperapp.app.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnlockPolicyTest {

    private fun decide(
        userPremium: Boolean = false,
        wallpaperPremium: Boolean = false,
        unlocked: Boolean = false,
        downloaded: Boolean = false,
    ) = UnlockPolicy.decide(userPremium, wallpaperPremium, unlocked, downloaded)

    @Test
    fun `premium users never see the sheet`() {
        assertThat(decide(userPremium = true)).isEqualTo(UnlockDecision.Allowed)
        assertThat(decide(userPremium = true, wallpaperPremium = true)).isEqualTo(UnlockDecision.Allowed)
    }

    @Test
    fun `free wallpaper needs an ad the first time`() {
        assertThat(decide()).isEqualTo(UnlockDecision.NeedsUnlock(canWatchAd = true))
    }

    @Test
    fun `unlocked or already downloaded goes straight through`() {
        assertThat(decide(unlocked = true)).isEqualTo(UnlockDecision.Allowed)
        assertThat(decide(downloaded = true)).isEqualTo(UnlockDecision.Allowed)
    }

    @Test
    fun `premium-only wallpaper offers only the subscription`() {
        assertThat(decide(wallpaperPremium = true)).isEqualTo(UnlockDecision.NeedsUnlock(canWatchAd = false))
        // An old ad unlock doesn't open a premium wallpaper.
        assertThat(decide(wallpaperPremium = true, unlocked = true)).isEqualTo(UnlockDecision.NeedsUnlock(canWatchAd = false))
    }
}
