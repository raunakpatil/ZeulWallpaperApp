package com.wallpaperapp.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.data.repository.AppConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Real AdMob implementation of [AdsManager] (task A9).
 *
 * - UMP consent form (GDPR/UK) before any ad request; ads are only requested when `canRequestAds()`.
 * - Rewarded: preloaded; if not ready when requested, waits up to [REWARDED_WAIT_MS], else `onUnavailable`.
 * - Native: a small pool shared by all grid slots; ads are returned to the pool when a slot scrolls
 *   away (fewer requests), and dropped after an hour as AdMob requires.
 * - App-open: off unless `app_config.ads.app_open_enabled`; spaced by `app_open_min_hours`; never in
 *   the first hours after install. See [AppOpenPolicy].
 * - Premium users: `adsEnabled` = false, nothing is loaded or shown.
 */
@Singleton
class AdMobAdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    premiumManager: PremiumManager,
    private val appConfig: AppConfigRepository,
) : AdsManager {

    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = context.getSharedPreferences("ads", Context.MODE_PRIVATE)
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)

    override val adsEnabled: StateFlow<Boolean> = premiumManager.isPremium
        .map { !it }
        .stateIn(main, SharingStarted.Eagerly, true)

    private val sdkReady = MutableStateFlow(false)
    private var initStarted = false
    private var fullScreenShowing = false

    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    init {
        if (prefs.getLong(KEY_LAST_APP_OPEN, 0L) == 0L) {
            prefs.edit().putLong(KEY_LAST_APP_OPEN, System.currentTimeMillis()).apply()
        }
        main.launch {
            adsEnabled.collect { enabled -> if (!enabled) releaseAll() else if (sdkReady.value) preloadAll() }
        }
    }

    private fun canRequestAds() = consent.canRequestAds() && adsEnabled.value

    // ---------------------------------------------------------------- init + consent

    override suspend fun initialize(activity: Activity) {
        if (initStarted) return
        initStarted = true
        gatherConsent(activity)
        _privacyOptionsRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (!consent.canRequestAds()) {
            Log.i(TAG, "Consent does not allow ad requests; ads stay off")
            return
        }
        startSdk()
    }

    private suspend fun gatherConsent(activity: Activity) = suspendCancellableCoroutine { cont ->
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    formError?.let { Log.w(TAG, "Consent form: ${it.message}") }
                    if (cont.isActive) cont.resume(Unit)
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.message}")
                if (cont.isActive) cont.resume(Unit)
            },
        )
    }

    private suspend fun startSdk() {
        if (sdkReady.value) return
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont -> MobileAds.initialize(context) { if (cont.isActive) cont.resume(Unit) } }
        }
        sdkReady.value = true
        Log.i(TAG, "AdMob ready (test ads: ${AdUnits.usingTestAds})")
        preloadAll()
    }

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { Log.w(TAG, "Privacy options form: ${it.message}") }
            main.launch { if (consent.canRequestAds()) startSdk() else releaseAll() }
        }
    }

    private fun preloadAll() {
        preloadRewarded()
        loadNativeAd()
        loadAppOpen()
    }

    private fun releaseAll() {
        rewardedAd = null
        appOpenAd = null
        nativeRetry?.cancel()
        nativePool.value.forEach { loadedAt.remove(it.ad); it.ad.destroy() }
        nativePool.value = emptyList()
    }

    // ---------------------------------------------------------------- rewarded

    private var rewardedAd: RewardedAd? = null
    private var rewardedLoading = false
    private val rewardedAvailable = MutableStateFlow(false)

    override fun preloadRewarded() {
        if (!sdkReady.value || !canRequestAds() || rewardedAd != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(context, AdUnits.rewarded, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                rewardedLoading = false
                rewardedAvailable.value = true
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "Rewarded load failed: ${error.code} ${error.message}")
                rewardedLoading = false
                rewardedAvailable.value = false
            }
        })
    }

    override fun showRewarded(activity: Activity, onRewarded: () -> Unit, onUnavailable: () -> Unit) {
        if (!adsEnabled.value) { onRewarded(); return }          // premium: nothing to watch
        if (!sdkReady.value || !consent.canRequestAds()) { onUnavailable(); return }

        main.launch {
            if (rewardedAd == null) {
                preloadRewarded()
                withTimeoutOrNull(REWARDED_WAIT_MS) { rewardedAvailable.first { it } }
            }
            val ad = rewardedAd
            if (ad == null || fullScreenShowing) { onUnavailable(); return@launch }
            rewardedAd = null
            rewardedAvailable.value = false

            var earned = false
            var finished = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() { fullScreenShowing = true }
                override fun onAdDismissedFullScreenContent() {
                    fullScreenShowing = false
                    if (!finished && earned) { finished = true; onRewarded() }
                    // Closed before earning: no callback, the unlock sheet simply stays open.
                    preloadRewarded()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    fullScreenShowing = false
                    Log.w(TAG, "Rewarded show failed: ${error.message}")
                    if (!finished) { finished = true; onUnavailable() }
                    preloadRewarded()
                }
            }
            ad.show(activity) { earned = true }
        }
    }

    // ---------------------------------------------------------------- native

    private data class PooledNative(val ad: NativeAd, val loadedAtMs: Long)

    private val nativePool = MutableStateFlow<List<PooledNative>>(emptyList())
    private val loadedAt = HashMap<NativeAd, Long>()
    private var nativeLoading = false
    private var nativeFailures = 0
    // True after repeated failures with nothing in the pool (no fill, or an emulator that can't render
    // native test ads): slots then collapse instead of showing empty "Sponsored" boxes.
    private val nativeUnavailable = MutableStateFlow(false)
    private var nativeRetry: Job? = null
    // Some creatives reject media/AdChoices options with "Invalid template ID"; after such an
    // internal error we retry with the SDK defaults.
    private var nativePlainOptions = false

    private fun nativeOptions(): NativeAdOptions =
        if (nativePlainOptions) NativeAdOptions.Builder().build()
        else NativeAdOptions.Builder()
            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY) // the tile crops to fit
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .build()

    /** Loads ONE native ad, then keeps going until the pool holds NATIVE_BATCH. Main thread only. */
    private fun loadNativeAd() {
        if (!sdkReady.value || !canRequestAds() || nativeLoading || nativeRetry?.isActive == true) return
        if (nativePool.value.size >= NATIVE_BATCH) return
        nativeLoading = true
        AdLoader.Builder(context, AdUnits.native)
            .forNativeAd { ad ->
                nativeLoading = false
                nativeFailures = 0
                nativeUnavailable.value = false
                if (!adsEnabled.value) { ad.destroy(); return@forNativeAd }
                val now = System.currentTimeMillis()
                loadedAt[ad] = now
                nativePool.value = nativePool.value + PooledNative(ad, now)
                loadNativeAd()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeLoading = false
                    nativeFailures++
                    Log.w(TAG, "Native load failed (${nativeFailures}x, plainOptions=$nativePlainOptions): ${error.code} ${error.message}")
                    if (error.code == AdRequest.ERROR_CODE_INTERNAL_ERROR && !nativePlainOptions) nativePlainOptions = true
                    if (nativeFailures >= NATIVE_GIVE_UP_AFTER && nativePool.value.isEmpty()) nativeUnavailable.value = true
                    // 15 s, 30 s, 60 s … capped at 5 min, so a no-fill streak never spams requests.
                    val wait = minOf(NATIVE_RETRY_BASE_MS shl (nativeFailures - 1).coerceAtMost(5), NATIVE_RETRY_MAX_MS)
                    nativeRetry = main.launch { delay(wait); nativeRetry = null; loadNativeAd() }
                }
            })
            .withNativeAdOptions(nativeOptions())
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun isFresh(ad: NativeAd) =
        (loadedAt[ad] ?: 0L) + NATIVE_MAX_AGE_MS > System.currentTimeMillis()

    /** Takes an ad from the pool, waiting until one is available. Main thread only. */
    private suspend fun takeNative(): NativeAd {
        while (true) {
            val (fresh, stale) = nativePool.value.partition { isFresh(it.ad) }
            stale.forEach { loadedAt.remove(it.ad); it.ad.destroy() }
            nativePool.value = fresh
            loadNativeAd()
            val list = withTimeoutOrNull(NATIVE_WAIT_MS) { nativePool.first { it.isNotEmpty() } } ?: continue
            val picked = list.first()
            nativePool.value = nativePool.value - picked
            loadNativeAd() // top the pool back up
            return picked.ad
        }
    }

    private fun giveBack(ad: NativeAd) {
        if (adsEnabled.value && isFresh(ad)) {
            nativePool.value = nativePool.value + PooledNative(ad, loadedAt[ad] ?: 0L)
        } else {
            loadedAt.remove(ad)
            ad.destroy()
        }
    }

    @Composable
    override fun NativeAdSlot(modifier: Modifier) {
        val enabled by adsEnabled.collectAsStateWithLifecycle()
        val ready by sdkReady.collectAsStateWithLifecycle()
        val unavailable by nativeUnavailable.collectAsStateWithLifecycle()
        if (!enabled) return

        var ad by remember { mutableStateOf<NativeAd?>(null) }
        // Keeps waiting while the slot is on screen, so a slot that appeared before ads loaded fills in later.
        LaunchedEffect(ready) { if (ready && ad == null) ad = takeNative() }
        DisposableEffect(Unit) { onDispose { ad?.let(::giveBack) } }

        val current = ad
        when {
            current != null -> NativeAdTile(current, modifier)
            !unavailable -> NativeAdPlaceholder(modifier)
            else -> Unit // collapsed; fills in if an ad arrives later
        }
    }

    // ---------------------------------------------------------------- app open

    private var appOpenAd: AppOpenAd? = null
    private var appOpenLoadedAtMs: Long? = null
    private var appOpenLoading = false

    private fun loadAppOpen() {
        val cfg = appConfig.config.value.ads
        if (!cfg.appOpenEnabled || !sdkReady.value || !canRequestAds() || appOpenLoading) return
        if (appOpenAd != null && !AppOpenPolicy.isStale(System.currentTimeMillis(), appOpenLoadedAtMs)) return
        appOpenLoading = true
        AppOpenAd.load(context, AdUnits.appOpen, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpenAd = ad
                appOpenLoadedAtMs = System.currentTimeMillis()
                appOpenLoading = false
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w(TAG, "App-open load failed: ${error.code} ${error.message}")
                appOpenLoading = false
            }
        })
    }

    override fun maybeShowAppOpen(activity: Activity) {
        val cfg = appConfig.config.value.ads
        val show = AppOpenPolicy.shouldShow(
            AppOpenPolicy.State(
                nowMs = System.currentTimeMillis(),
                enabled = cfg.appOpenEnabled,
                minHoursBetween = cfg.appOpenMinHours,
                adsEnabled = adsEnabled.value,
                canRequestAds = sdkReady.value && consent.canRequestAds(),
                lastShownOrInstallMs = prefs.getLong(KEY_LAST_APP_OPEN, System.currentTimeMillis()),
                adLoadedAtMs = appOpenAd?.let { appOpenLoadedAtMs },
                isShowingFullScreenAd = fullScreenShowing,
            ),
        )
        val ad = appOpenAd
        if (!show || ad == null) { loadAppOpen(); return }

        appOpenAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                fullScreenShowing = true
                prefs.edit().putLong(KEY_LAST_APP_OPEN, System.currentTimeMillis()).apply()
            }
            override fun onAdDismissedFullScreenContent() { fullScreenShowing = false; loadAppOpen() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { fullScreenShowing = false; loadAppOpen() }
        }
        ad.show(activity)
    }

    private companion object {
        const val TAG = "AdMobAdsManager"
        const val KEY_LAST_APP_OPEN = "last_app_open_or_install_ms"
        const val REWARDED_WAIT_MS = 6_000L
        const val NATIVE_WAIT_MS = 8_000L
        const val NATIVE_BATCH = 3
        const val NATIVE_RETRY_BASE_MS = 15_000L
        const val NATIVE_RETRY_MAX_MS = 300_000L
        const val NATIVE_GIVE_UP_AFTER = 2
        val NATIVE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(1)
    }
}
