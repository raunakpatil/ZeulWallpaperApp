package com.wallpaperapp.app.ui.common

import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.domain.model.AppConfig
import org.junit.Test

class GateAndReviewTest {

    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun `maintenance beats everything`() {
        val config = AppConfig(maintenance = AppConfig.Maintenance(true, "Back at 6"), minAppVersion = 99)
        assertThat(AppGate.decide(config, versionCode = 1, onboardingDone = true)).isEqualTo(AppGate.Maintenance("Back at 6"))
    }

    @Test
    fun `old versions must update before onboarding`() {
        assertThat(AppGate.decide(AppConfig(minAppVersion = 2), 1, onboardingDone = false)).isEqualTo(AppGate.UpdateRequired)
        assertThat(AppGate.decide(AppConfig(minAppVersion = 2), 2, onboardingDone = true)).isEqualTo(AppGate.Ready)
    }

    @Test
    fun `onboarding then ready`() {
        assertThat(AppGate.decide(AppConfig(), 1, onboardingDone = null)).isEqualTo(AppGate.Loading)
        assertThat(AppGate.decide(AppConfig(), 1, onboardingDone = false)).isEqualTo(AppGate.Onboarding)
        assertThat(AppGate.decide(AppConfig(), 1, onboardingDone = true)).isEqualTo(AppGate.Ready)
    }

    @Test
    fun `review is asked after three successes, then at most monthly`() {
        assertThat(ReviewPolicy.shouldAsk(successes = 2, lastAskedAt = null, now = 0)).isFalse()
        assertThat(ReviewPolicy.shouldAsk(successes = 3, lastAskedAt = null, now = 0)).isTrue()
        assertThat(ReviewPolicy.shouldAsk(successes = 9, lastAskedAt = 0, now = 10 * day)).isFalse()
        assertThat(ReviewPolicy.shouldAsk(successes = 9, lastAskedAt = 0, now = 31 * day)).isTrue()
    }
}
