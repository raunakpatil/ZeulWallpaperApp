package com.wallpaperapp.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.wallpaperapp.app.R
import com.wallpaperapp.app.data.remote.FakeWallpaperRemoteDataSource
import com.wallpaperapp.app.ui.preview.GatedAction
import com.wallpaperapp.app.ui.preview.UnlockSheet
import com.wallpaperapp.app.ui.theme.WallpaperTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI test for the unlock sheet. */
@RunWith(AndroidJUnit4::class)
class UnlockSheetTest {

    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)
    private val wallpaper = FakeWallpaperRemoteDataSource().wallpapers.first()

    @Test
    fun freeWallpaper_offersAdAndPremium() {
        var watched = 0
        var premium = 0
        compose.setContent {
            WallpaperTheme {
                UnlockSheet(
                    sheet = com.wallpaperapp.app.ui.preview.UnlockSheet(wallpaper, GatedAction.DOWNLOAD, canWatchAd = true),
                    onWatchAd = { watched++ },
                    onGoPremium = { premium++ },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(s(R.string.unlock_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.unlock_body_ad_download)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.watch_ad)).performClick()
        compose.onNodeWithText(s(R.string.go_premium_trial)).performClick()
        assertThat(watched).isEqualTo(1)
        assertThat(premium).isEqualTo(1)
    }

    @Test
    fun premiumWallpaper_offersOnlyTheSubscription() {
        compose.setContent {
            WallpaperTheme {
                UnlockSheet(
                    sheet = com.wallpaperapp.app.ui.preview.UnlockSheet(wallpaper, GatedAction.APPLY, canWatchAd = false),
                    onWatchAd = {}, onGoPremium = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(s(R.string.unlock_title_premium)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.unlock_body_premium_apply)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.watch_ad)).assertDoesNotExist()
        compose.onNodeWithText(s(R.string.go_premium_trial)).assertIsDisplayed()
    }

    @Test
    fun whileTheAdLoads_theButtonCannotBeTappedAgain() {
        compose.setContent {
            WallpaperTheme {
                UnlockSheet(
                    sheet = com.wallpaperapp.app.ui.preview.UnlockSheet(wallpaper, GatedAction.DOWNLOAD, canWatchAd = true, adLoading = true),
                    onWatchAd = {}, onGoPremium = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(s(R.string.loading_ad)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.loading_ad)).assertIsNotEnabled()
    }
}
