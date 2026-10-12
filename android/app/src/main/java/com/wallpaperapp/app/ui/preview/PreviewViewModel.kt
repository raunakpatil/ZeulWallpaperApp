package com.wallpaperapp.app.ui.preview

import com.wallpaperapp.app.domain.model.AppConfig
import android.app.Activity
import com.wallpaperapp.app.R
import com.wallpaperapp.app.ui.common.UiText
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.wallpaperapp.app.ads.AdsManager
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.data.image.GallerySaver
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.FavoritesRepository
import com.wallpaperapp.app.data.repository.UnlockDecision
import com.wallpaperapp.app.data.repository.UnlockGate
import com.wallpaperapp.app.data.repository.WallpaperActions
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.ui.common.ReviewPrompter
import com.wallpaperapp.app.ui.navigation.PreviewRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** What the preview draws on top of the wallpaper to show how it will look. */
enum class OverlayMode { NONE, LOCK, HOME;

    fun next(): OverlayMode = entries[(ordinal + 1) % entries.size]
}

/** Actions that sit behind the unlock sheet. */
enum class GatedAction { DOWNLOAD, APPLY }

data class UnlockSheet(
    val wallpaper: Wallpaper,
    val action: GatedAction,
    val canWatchAd: Boolean,
    /** `app_config.unlock_mode` is 24h: the sheet says the unlock lasts a day. */
    val unlockFor24h: Boolean = false,
    /** Waiting for the rewarded ad to load or finish. */
    val adLoading: Boolean = false,
)

data class PreviewUiState(
    val loading: Boolean = true,
    val wallpapers: List<Wallpaper> = emptyList(),
    val initialIndex: Int = 0,
    val cdnBaseUrl: String = "",
    val favoriteIds: Set<String> = emptySet(),
    val overlay: OverlayMode = OverlayMode.NONE,
    val chromeVisible: Boolean = true,
    val notFound: Boolean = false,
    val downloadingId: String? = null,
    val message: UiText? = null,
    val unlockSheet: UnlockSheet? = null,
    /** One-shot: the screen performs this (download with permission check / open Apply), then clears it. */
    val proceed: Pair<Wallpaper, GatedAction>? = null,
    val isPremium: Boolean = false,
    /** One-shot after a successful download: a good moment to ask for a Play review. */
    val askReview: Boolean = false,
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    session: PreviewSession,
    private val repository: WallpaperRepository,
    private val favorites: FavoritesRepository,
    private val actions: WallpaperActions,
    private val gate: UnlockGate,
    private val adsManager: AdsManager,
    premiumManager: PremiumManager,
    private val appConfig: AppConfigRepository,
    private val reviewPrompter: ReviewPrompter,
) : ViewModel() {

    private val startId = savedStateHandle.toRoute<PreviewRoute>().wallpaperId

    private val content = MutableStateFlow(PreviewUiState())
    private val viewed = mutableSetOf<String>()
    private var adActivityShown = false

    val uiState: StateFlow<PreviewUiState> = combine(
        content, favorites.favoriteIds, appConfig.config, premiumManager.isPremium,
    ) { state, favs, config, premium ->
        state.copy(favoriteIds = favs, cdnBaseUrl = config.cdnBaseUrl, isPremium = premium)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewUiState())

    init {
        val list = session.listFor(startId)
        if (list != null) {
            content.update { it.copy(loading = false, wallpapers = list, initialIndex = list.indexOfFirst { w -> w.id == startId }) }
        } else {
            viewModelScope.launch {
                val wallpaper = repository.getWallpaper(startId)
                content.update {
                    it.copy(loading = false, wallpapers = listOfNotNull(wallpaper), notFound = wallpaper == null)
                }
            }
        }
        // Subscribed from the paywall while the sheet was open: carry on with what they asked for.
        viewModelScope.launch {
            premiumManager.isPremium.filter { it }.collect {
                content.value.unlockSheet?.let { sheet ->
                    content.update { s -> s.copy(unlockSheet = null, proceed = sheet.wallpaper to sheet.action) }
                }
            }
        }
    }

    /** Counts a view once per wallpaper per visit and keeps an offline snapshot. */
    fun onPageShown(wallpaper: Wallpaper) {
        if (!viewed.add(wallpaper.id)) return
        repository.recordView(wallpaper.id)
        viewModelScope.launch { repository.cache(wallpaper) }
    }

    fun toggleFavorite(wallpaper: Wallpaper) {
        viewModelScope.launch { favorites.toggle(wallpaper) }
    }

    /** Download/Apply tapped: go ahead, or show the unlock sheet first. */
    fun request(wallpaper: Wallpaper, action: GatedAction) {
        viewModelScope.launch {
            when (val decision = gate.check(wallpaper)) {
                UnlockDecision.Allowed -> content.update { it.copy(proceed = wallpaper to action) }
                is UnlockDecision.NeedsUnlock -> content.update {
                    it.copy(unlockSheet = UnlockSheet(wallpaper, action, decision.canWatchAd, unlockFor24h = appConfig.config.value.unlockMode == AppConfig.UnlockMode.HOURS_24))
                }
            }
        }
        adsManager.preloadRewarded()
    }

    fun proceedHandled() = content.update { it.copy(proceed = null) }

    fun dismissSheet() = content.update { it.copy(unlockSheet = null) }

    /**
     * Shows the rewarded ad. Both a reward and "no ad available" unlock (never punish the user).
     * Closing the ad early fires neither callback (CONTRACTS section 5), so the sheet stays open;
     * [onScreenResumed] clears the spinner when the user comes back from the ad.
     */
    fun watchAd(activity: Activity) {
        val sheet = content.value.unlockSheet ?: return
        if (sheet.adLoading) return
        content.update { it.copy(unlockSheet = sheet.copy(adLoading = true)) }
        adActivityShown = false
        adsManager.showRewarded(
            activity = activity,
            onRewarded = { grant(sheet) },
            onUnavailable = { grant(sheet) },
        )
    }

    fun onScreenPaused() {
        if (content.value.unlockSheet?.adLoading == true) adActivityShown = true
    }

    fun onScreenResumed() {
        if (!adActivityShown) return
        adActivityShown = false
        // Back from the ad without a reward: let them try again.
        content.update { s -> s.copy(unlockSheet = s.unlockSheet?.copy(adLoading = false)) }
    }

    private fun grant(sheet: UnlockSheet) {
        viewModelScope.launch {
            gate.grant(sheet.wallpaper)
            content.update { it.copy(unlockSheet = null, proceed = sheet.wallpaper to sheet.action, message = UiText.Res(R.string.unlocked)) }
        }
    }

    /** Saves to the gallery. Call only after [request] said to proceed. */
    fun download(wallpaper: Wallpaper) {
        if (content.value.downloadingId != null) return
        content.update { it.copy(downloadingId = wallpaper.id) }
        viewModelScope.launch {
            var saved = false
            val message = try {
                actions.download(wallpaper)
                saved = true
                UiText.Res(R.string.saved_to, listOf("Pictures/${GallerySaver.FOLDER}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PreviewViewModel", "download failed", e)
                UiText.Res(R.string.download_failed)
            }
            content.update { it.copy(downloadingId = null, message = message, askReview = saved) }
        }
    }

    fun report(wallpaper: Wallpaper, reason: String) {
        viewModelScope.launch {
            val message = try {
                repository.report(wallpaper.id, reason)
                UiText.Res(R.string.report_thanks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PreviewViewModel", "report failed", e)
                UiText.Res(R.string.report_failed)
            }
            content.update { it.copy(message = message) }
        }
    }

    fun messageShown() = content.update { it.copy(message = null) }

    fun askForReview(activity: Activity) {
        content.update { it.copy(askReview = false) }
        viewModelScope.launch { reviewPrompter.onSuccess(activity) }
    }

    fun cycleOverlay() = content.update { it.copy(overlay = it.overlay.next()) }

    fun toggleChrome() = content.update { it.copy(chromeVisible = !it.chromeVisible) }
}
