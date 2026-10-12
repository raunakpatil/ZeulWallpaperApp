package com.wallpaperapp.app

import androidx.compose.runtime.CompositionLocalProvider
import com.wallpaperapp.app.ui.theme.LocalCardStyle
import com.wallpaperapp.app.ui.theme.CardStyle
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.view.WindowCompat
import com.wallpaperapp.app.ui.intro.BootIntro
import com.wallpaperapp.app.ui.intro.IRIS_CLOSE_TO_HOLD_MS
import com.wallpaperapp.app.ui.intro.IRIS_HOLD
import com.wallpaperapp.app.ui.intro.IRIS_HOLD_MS
import com.wallpaperapp.app.ui.intro.IRIS_SHUT_MS
import com.wallpaperapp.app.ui.intro.IntroHandoff
import com.wallpaperapp.app.ui.intro.irisOut
import kotlinx.coroutines.delay
import com.wallpaperapp.app.ui.intro.IntroPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperapp.app.domain.model.AppLink
import com.wallpaperapp.app.domain.model.ShareLinks
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.lifecycle.lifecycleScope
import com.wallpaperapp.app.ads.AdsManager
import com.wallpaperapp.app.data.account.AccountSync
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.push.PushNotifications
import com.wallpaperapp.app.ui.WallpaperAppRoot
import com.wallpaperapp.app.ui.theme.WallpaperTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var adsManager: AdsManager
    @Inject lateinit var accountSync: AccountSync
    @Inject lateinit var wallpaperRepository: WallpaperRepository

    private val deepLink = MutableStateFlow<AppLink?>(null)
    private var introPlaying = false

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest's launch theme paints the boot video's orange behind the system splash.
        setTheme(R.style.Theme_WallpaperApp)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Fade the system splash (hamster icon on orange) into the intro's first frame.
            splashScreen.setOnExitAnimationListener { view ->
                view.animate().alpha(0f).setDuration(SPLASH_FADE_MS).withEndAction { view.remove() }.start()
            }
        }
        // B7: restores the session, tells PremiumManager who is signed in, syncs favourites/unlocks.
        accountSync.start()

        lifecycleScope.launch {
            // Premium users must not get an ad before RevenueCat answers; never wait more than a few seconds.
            withTimeoutOrNull(PREMIUM_WAIT_MS) { accountSync.awaitPremiumIdentity() }
            adsManager.initialize(this@MainActivity)
        }

        deepLink.value = ShareLinks.parse(intent?.dataString)
        // Count a notification tap once, not again when the activity is recreated.
        if (savedInstanceState == null) recordPushOpen(intent)

        // Boot intro (docs/design/boot-intro.md): cold start only, never for links/notification taps.
        val openedFromLinkOrPush = deepLink.value != null || intent?.getStringExtra(PushNotifications.EXTRA_PUSH_ID) != null
        introPlaying = IntroPolicy.shouldPlay(this, coldStart = savedInstanceState == null, openedFromLinkOrPush = openedFromLinkOrPush)
        if (introPlaying) WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        readCardStyle(intent)
        setContent {
            val linkId by deepLink.collectAsStateWithLifecycle()
            val cardStyle by debugCardStyle.collectAsStateWithLifecycle()
            var showIntro by remember { mutableStateOf(introPlaying) }
            var introEnded by remember { mutableStateOf(false) }
            val iris = remember { Animatable(1f) } // 1 = intro fills the screen, 0 = iris shut (app shown)
            WallpaperTheme {
              CompositionLocalProvider(LocalCardStyle provides (cardStyle ?: LocalCardStyle.current)) {
                Box(Modifier.fillMaxSize()) {
                    // The app composes underneath from the start, so Home is loaded when the intro ends.
                    Box(Modifier.fillMaxSize()) {
                        WallpaperAppRoot(
                            windowSizeClass = calculateWindowSizeClass(this@MainActivity),
                            deepLink = linkId,
                            onDeepLinkHandled = { deepLink.value = null },
                        )
                    }
                    if (showIntro) {
                        BootIntro(
                            onEnd = { introEnded = true },
                            modifier = Modifier.irisOut(iris.value),
                        )
                    }
                }
                LaunchedEffect(introEnded) {
                    if (!introEnded) return@LaunchedEffect
                    // Looney Tunes iris-out: close in on the hamster, hold a beat, then shut onto the app.
                    iris.animateTo(IRIS_HOLD, tween(IRIS_CLOSE_TO_HOLD_MS, easing = FastOutSlowInEasing))
                    delay(IRIS_HOLD_MS)
                    iris.animateTo(0f, tween(IRIS_SHUT_MS, easing = FastOutLinearInEasing))
                    // Encore: the hamster irises back in at Home's diamond button for a moment (if Home is showing).
                    if (IntroHandoff.gemCenter != null) IntroHandoff.hamsterInGem = true
                    showIntro = false
                    onIntroFinished()
                }
              }
            }
        }
    }

    /** singleTop: a link tapped while the app is open arrives here. */
    /** Debug builds only: compare card styles on a device (`-e card_style FLAT`, see CardStyle). */
    private val debugCardStyle = MutableStateFlow<CardStyle?>(null)

    private fun readCardStyle(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        intent?.getStringExtra("card_style")?.let { name -> CardStyle.entries.firstOrNull { it.name == name } }?.let { debugCardStyle.value = it }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readCardStyle(intent)
        ShareLinks.parse(intent.dataString)?.let { deepLink.value = it }
        recordPushOpen(intent)
    }

    private fun recordPushOpen(intent: Intent?) {
        intent?.getStringExtra(PushNotifications.EXTRA_PUSH_ID)?.let(wallpaperRepository::recordPushOpen)
    }

    override fun onStart() {
        super.onStart()
        // The manager decides whether one is due (remote toggle, spacing, install age).
        // Never on top of the boot intro: it gets its chance once the intro hands over.
        if (!introPlaying) adsManager.maybeShowAppOpen(this)
    }

    private fun onIntroFinished() {
        introPlaying = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK != android.content.res.Configuration.UI_MODE_NIGHT_YES
        adsManager.maybeShowAppOpen(this)
    }

    private companion object {
        const val PREMIUM_WAIT_MS = 4_000L
        const val SPLASH_FADE_MS = 250L
    }
}
