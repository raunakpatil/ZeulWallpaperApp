package com.wallpaperapp.app.ui.home

import androidx.lifecycle.ViewModel
import com.wallpaperapp.app.R
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.wallpaperapp.app.ads.AdsManager
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.AppConfig
import com.wallpaperapp.app.domain.model.PopularRange
import com.wallpaperapp.app.domain.model.Section
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.ui.preview.WallpaperOpener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Home chips. [key] matches the values in `app_config.home_sections`. */
enum class HomeChip(val key: String, @param:StringRes val label: Int) {
    FOR_YOU("for_you", R.string.for_you),
    CURATED("curated", R.string.chip_curated),
    POPULAR("popular", R.string.chip_popular),
    NEW("new", R.string.chip_new),
    AMOLED("amoled", R.string.amoled),
    TABLET("tablet", R.string.device_tablet),
    PREMIUM("premium", R.string.premium);

    companion object {
        /** Order comes from the dashboard; unknown keys are skipped, and an empty result falls back to all chips. */
        fun fromConfig(keys: List<String>): List<HomeChip> =
            keys.mapNotNull { key -> entries.firstOrNull { it.key == key } }.distinct().ifEmpty { entries }
    }
}

data class HomeUiState(
    val chips: List<HomeChip> = HomeChip.entries,
    val selected: HomeChip = HomeChip.FOR_YOU,
    val popularRange: PopularRange = PopularRange.WEEK,
    val cdnBaseUrl: String = "",
    val featured: Featured? = null,
    val daily: Wallpaper? = null,
) {
    data class Featured(val title: String, val wallpaper: Wallpaper)

    /** Banner and daily card only sit on top of the first chip, the "front page". */
    val showHeader: Boolean get() = selected == chips.firstOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    appConfigRepository: AppConfigRepository,
    private val opener: WallpaperOpener,
    sectionRequest: HomeSectionRequest,
    val adsManager: AdsManager,
) : ViewModel() {

    private val selection = MutableStateFlow<HomeChip?>(null) // null = not chosen by the user yet
    private val popularRange = MutableStateFlow(PopularRange.WEEK)
    private val preferTablet = MutableStateFlow(false)
    private val config = appConfigRepository.config

    private val chips = config.map { HomeChip.fromConfig(it.homeSections) }.distinctUntilChanged()

    private val selected: Flow<HomeChip> = combine(selection, chips, preferTablet) { chosen, chips, tablet ->
        when {
            chosen != null && chosen in chips -> chosen
            tablet && HomeChip.TABLET in chips -> HomeChip.TABLET // tablets open on the Tablet section
            else -> chips.first()
        }
    }.distinctUntilChanged()

    private val header: Flow<Pair<HomeUiState.Featured?, Wallpaper?>> = config
        .map { it.featuredBanner to it.dailyWallpaperId }
        .distinctUntilChanged()
        .mapLatest { (banner, dailyId) -> loadFeatured(banner) to dailyId?.let { repository.getWallpaper(it) } }
        .onStart { emit(null to null) } // don't hold the chips and grid back while this loads

    val uiState: StateFlow<HomeUiState> = combine(
        chips, selected, popularRange, config.map { it.cdnBaseUrl }.distinctUntilChanged(), header,
    ) { chips, selected, range, cdn, (featured, daily) ->
        HomeUiState(chips, selected, range, cdn, featured, daily)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val wallpapers: Flow<PagingData<Wallpaper>> = combine(selected, popularRange, repository.seedTags) { chip, range, seeds ->
        Triple(chip, range, if (chip == HomeChip.FOR_YOU) seeds else emptySet())
    }
        .distinctUntilChanged()
        .flatMapLatest { (chip, range, seeds) -> pagerFor(chip, range, seeds) }
        .cachedIn(viewModelScope)

    init {
        // zeul://section/{name}: open that chip (if this build knows it), once.
        viewModelScope.launch {
            sectionRequest.pending.filterNotNull().collect { key ->
                HomeChip.entries.firstOrNull { it.key == key }?.let(::select)
                sectionRequest.consumed()
            }
        }
    }

    fun select(chip: HomeChip) {
        selection.value = chip
    }

    fun selectPopularRange(range: PopularRange) {
        popularRange.value = range
    }

    /** Called by the screen with the window size; only affects the default chip. */
    fun setPreferTablet(tablet: Boolean) {
        preferTablet.value = tablet
    }

    fun onWallpaperOpened(wallpaper: Wallpaper, visibleList: List<Wallpaper>) = opener.open(wallpaper, visibleList)

    private fun pagerFor(chip: HomeChip, range: PopularRange, seeds: Set<String>): Flow<PagingData<Wallpaper>> = when (chip) {
        HomeChip.FOR_YOU -> repository.forYou(seeds)
        HomeChip.POPULAR -> repository.popular(range)
        HomeChip.CURATED -> repository.section(Section.CURATED)
        HomeChip.NEW -> repository.section(Section.NEW)
        HomeChip.AMOLED -> repository.section(Section.AMOLED)
        HomeChip.TABLET -> repository.section(Section.TABLET)
        HomeChip.PREMIUM -> repository.section(Section.PREMIUM)
    }

    // Collection banners need collections support (B6); until then only wallpaper banners show.
    private suspend fun loadFeatured(banner: AppConfig.FeaturedBanner?): HomeUiState.Featured? {
        val id = banner?.wallpaperId ?: return null
        return repository.getWallpaper(id)?.let { HomeUiState.Featured(banner.title, it) }
    }
}
