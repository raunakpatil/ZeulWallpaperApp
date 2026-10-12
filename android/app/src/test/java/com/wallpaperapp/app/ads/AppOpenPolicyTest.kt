package com.wallpaperapp.app.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

class AppOpenPolicyTest {

    private val hour = TimeUnit.HOURS.toMillis(1)
    private val now = 1_000_000_000_000L

    private fun state(
        enabled: Boolean = true,
        minHours: Int = 4,
        adsEnabled: Boolean = true,
        canRequest: Boolean = true,
        lastShown: Long = now - 5 * hour,
        loadedAt: Long? = now - hour,
        showing: Boolean = false,
    ) = AppOpenPolicy.State(now, enabled, minHours, adsEnabled, canRequest, lastShown, loadedAt, showing)

    @Test fun `shows when everything allows it`() {
        assertThat(AppOpenPolicy.shouldShow(state())).isTrue()
    }

    @Test fun `disabled by remote config`() {
        assertThat(AppOpenPolicy.shouldShow(state(enabled = false))).isFalse()
    }

    @Test fun `never for premium users`() {
        assertThat(AppOpenPolicy.shouldShow(state(adsEnabled = false))).isFalse()
    }

    @Test fun `never without consent`() {
        assertThat(AppOpenPolicy.shouldShow(state(canRequest = false))).isFalse()
    }

    @Test fun `respects minimum gap since last ad or install`() {
        assertThat(AppOpenPolicy.shouldShow(state(lastShown = now - 3 * hour))).isFalse()
        assertThat(AppOpenPolicy.shouldShow(state(lastShown = now - 4 * hour))).isTrue()
    }

    @Test fun `gap of zero hours is treated as one hour`() {
        assertThat(AppOpenPolicy.shouldShow(state(minHours = 0, lastShown = now - hour / 2))).isFalse()
    }

    @Test fun `needs a loaded ad that is at most four hours old`() {
        assertThat(AppOpenPolicy.shouldShow(state(loadedAt = null))).isFalse()
        assertThat(AppOpenPolicy.shouldShow(state(loadedAt = now - 5 * hour))).isFalse()
        assertThat(AppOpenPolicy.isStale(now, now - 5 * hour)).isTrue()
        assertThat(AppOpenPolicy.isStale(now, now - hour)).isFalse()
    }

    @Test fun `not while another full-screen ad is showing`() {
        assertThat(AppOpenPolicy.shouldShow(state(showing = true))).isFalse()
    }
}
