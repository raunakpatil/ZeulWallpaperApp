package com.wallpaperapp.app.ui.home

import com.wallpaperapp.app.ui.theme.ZeulRadius
import com.wallpaperapp.app.ui.intro.WaveClip
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlin.math.hypot
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.delay
import com.wallpaperapp.app.ui.theme.softClickable
import com.wallpaperapp.app.ui.intro.IntroHandoff
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Icon
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import com.wallpaperapp.app.ui.theme.rememberSoftPress
import com.wallpaperapp.app.ui.theme.pressScale
import com.wallpaperapp.app.ui.theme.ZeulMotion
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.SoftIconButton
import com.wallpaperapp.app.ui.theme.SoftScreenTitle
import com.wallpaperapp.app.ui.theme.SoftSearchBar
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.softInset
import com.wallpaperapp.app.ui.theme.softRaised
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.wallpaperapp.app.domain.model.ImageVariant
import com.wallpaperapp.app.domain.model.PopularRange
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.imageUrl
import com.wallpaperapp.app.ui.LocalWindowSizeClass
import com.wallpaperapp.app.ui.components.BlurHash
import com.wallpaperapp.app.ui.components.WallpaperGrid
import com.wallpaperapp.app.ui.components.IconTab
import com.wallpaperapp.app.ui.components.ImageCard
import com.wallpaperapp.app.ui.components.RoundSoftButton
import androidx.compose.ui.graphics.vector.ImageVector
import com.wallpaperapp.app.ui.components.fullWidth
import com.wallpaperapp.app.ui.components.rememberGridAds

@Composable
fun HomeScreen(
    onWallpaperClick: (Wallpaper) -> Unit,
    onSearch: () -> Unit,
    onPremium: () -> Unit,
    onUpload: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.wallpapers.collectAsLazyPagingItems()
    val widthClass = LocalWindowSizeClass.current.widthSizeClass
    LaunchedEffect(widthClass) { viewModel.setPreferTablet(widthClass != WindowWidthSizeClass.Compact) }

    val open: (Wallpaper, List<Wallpaper>) -> Unit = { wallpaper, list ->
        viewModel.onWallpaperOpened(wallpaper, list)
        onWallpaperClick(wallpaper)
    }

    Column(Modifier.fillMaxSize()) {
        HomeHeader(onSearch, onUpload, onPremium)
        ChipRow(state.chips, state.selected, viewModel::select)

        val gridState = rememberLazyStaggeredGridState()
        // Switching chip or range starts the new list at the top.
        LaunchedEffect(state.selected, state.popularRange) { gridState.scrollToItem(0) }

        WallpaperGrid(items = items, cdnBaseUrl = state.cdnBaseUrl, onClick = open, state = gridState, ads = rememberGridAds()) {
            if (state.showHeader) {
                state.featured?.let { featured ->
                    fullWidth("featured") {
                        FeaturedBanner(featured.title, featured.wallpaper, state.cdnBaseUrl) {
                            open(featured.wallpaper, listOf(featured.wallpaper))
                        }
                    }
                }
                state.daily?.let { daily ->
                    fullWidth("daily") {
                        FeaturedBanner(stringResource(R.string.wallpaper_of_the_day), daily, state.cdnBaseUrl) { open(daily, listOf(daily)) }
                    }
                }
            }
            if (state.selected == HomeChip.POPULAR) {
                fullWidth("range") { PopularRangeSelector(state.popularRange, viewModel::selectPopularRange) }
            }
        }
    }
}

@Composable
private fun HomeHeader(onSearch: () -> Unit, onUpload: () -> Unit, onPremium: () -> Unit) {
    SoftScreenTitle(stringResource(R.string.app_wordmark), accentFirstLetter = true) {
        RoundSoftButton(ZeulIcons.Search, stringResource(R.string.home_search_hint), onSearch)
        Spacer(Modifier.width(12.dp))
        RoundSoftButton(ZeulIcons.Upload, stringResource(R.string.upload_title), onUpload)
        Spacer(Modifier.width(12.dp))
        GemButton(onPremium)
    }
}

/**
 * The diamond (Premium) button. After the boot intro's iris-out, the hamster makes an encore here,
 * Looney Tunes style: a mini iris opens on the hamster waving hi, then closes again on the diamond.
 */
@Composable
private fun GemButton(onClick: () -> Unit) {
    val soft = LocalSoft.current
    val label = stringResource(R.string.premium)
    val press = rememberSoftPress()
    val haptics = LocalHapticFeedback.current
    val iris = remember { Animatable(0f) } // 0 = closed (diamond), 1 = open on the hamster
    var waveDone by remember { mutableStateOf(false) }
    LaunchedEffect(IntroHandoff.hamsterInGem) {
        if (!IntroHandoff.hamsterInGem) return@LaunchedEffect
        waveDone = false
        delay(GEM_IRIS_DELAY_MS)
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        iris.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f)) // iris in, with a little pop
        // The hamster waves hi (the boot video's last 2 s); close as the wave ends.
        withTimeoutOrNull(WAVE_MAX_MS) { snapshotFlow { waveDone }.first { it } }
        iris.animateTo(0f, tween(GEM_IRIS_OUT_MS, easing = FastOutLinearInEasing)) // iris out onto the diamond
        IntroHandoff.hamsterInGem = false
    }
    DisposableEffect(Unit) { onDispose { IntroHandoff.gemCenter = null } }
    Box(
        Modifier
            .size(52.dp)
            .onGloballyPositioned { coords -> IntroHandoff.gemCenter = coords.boundsInRoot().center }
            .pressScale(press, 0.06f)
            .softRaised(RoundedCornerShape(ZeulRadius.Control), depth = 5.dp, pressed = press.progress)
            .softClickable(press, label, haptic = HapticFeedbackType.ContextClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ZeulIcons.Gem, label, tint = if (soft.isDark) soft.accent else soft.wordmark, modifier = Modifier.size(24.dp))
        if (IntroHandoff.hamsterInGem) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val radius = iris.value * hypot(size.width, size.height) / 2f
                        clipPath(Path().apply { addOval(Rect(center, radius)) }) { this@drawWithContent.drawContent() }
                        drawCircle(Color.Black.copy(alpha = 0.5f), radius, center, style = Stroke(width = 2.dp.toPx()))
                    },
            ) {
                WaveClip(onEnd = { waveDone = true }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private const val GEM_IRIS_DELAY_MS = 180L
private const val GEM_IRIS_OUT_MS = 320
private const val WAVE_MAX_MS = 3_200L

/** Section tabs: icon + label pills; the selected one is filled orange. */
@Composable
private fun ChipRow(chips: List<HomeChip>, selected: HomeChip, onClick: (HomeChip) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(chips, key = { it.key }) { chip ->
            IconTab(stringResource(chip.label), chipIcon(chip), selected = chip == selected, onClick = { onClick(chip) })
        }
    }
}

private fun chipIcon(chip: HomeChip): ImageVector = when (chip) {
    HomeChip.FOR_YOU -> ZeulIcons.Sparkle
    HomeChip.CURATED -> ZeulIcons.Star
    HomeChip.POPULAR -> ZeulIcons.Flame
    HomeChip.NEW -> ZeulIcons.Reset
    HomeChip.AMOLED -> ZeulIcons.Moon
    HomeChip.TABLET -> ZeulIcons.Tablet
    HomeChip.PREMIUM -> ZeulIcons.Gem
}

@Composable
private fun PopularRangeSelector(selected: PopularRange, onClick: (PopularRange) -> Unit) {
    val options = listOf(PopularRange.WEEK to stringResource(R.string.range_week), PopularRange.MONTH to stringResource(R.string.range_month), PopularRange.ALL to stringResource(R.string.range_all))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (range, label) ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onClick(range) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label) },
            )
        }
    }
}

/** Full-width hero card: the banner's name as the corner pill, the wallpaper's title bottom-left. */
@Composable
private fun FeaturedBanner(title: String, wallpaper: Wallpaper, cdnBaseUrl: String, onClick: () -> Unit) {
    ImageCard(
        imageUrl = wallpaper.imageUrl(cdnBaseUrl, ImageVariant.PREVIEW),
        title = wallpaper.title,
        subtitle = wallpaper.categoryName,
        label = title,
        labelIcon = ZeulIcons.Sparkle,
        blurhash = wallpaper.blurhash,
        dominantColor = wallpaper.dominantColor,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(220.dp),
    )
}
