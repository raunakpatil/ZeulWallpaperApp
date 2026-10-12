package com.wallpaperapp.app.ui.onboarding

import com.wallpaperapp.app.ui.theme.softRaised
import com.wallpaperapp.app.ui.theme.ZeulMotion
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.ZeulDisplay
import com.wallpaperapp.app.ui.theme.SoftTopBar
import com.wallpaperapp.app.ui.theme.SoftTextButton
import com.wallpaperapp.app.ui.theme.SoftChip
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.AccentButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.annotation.StringRes
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImage
import com.wallpaperapp.app.domain.model.ImageVariant
import com.wallpaperapp.app.domain.model.imageUrl
import com.wallpaperapp.app.ui.components.OutlinePill

private data class IntroPage(val icon: ImageVector, @param:StringRes val title: Int, @param:StringRes val body: Int)

// Page 0 is the full-bleed photo landing (intro1's text); these follow it.
private val INTRO = listOf(
    IntroPage(ZeulIcons.Lock, R.string.intro2_title, R.string.intro2_body),
    IntroPage(ZeulIcons.HeartFilled, R.string.intro3_title, R.string.intro3_body),
)

/**
 * First-launch onboarding: a full-bleed photo landing, 2 intro pages with the
 * hamster, then the style picker. Always skippable.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    // The picker page only exists when there are styles to pick (an empty catalogue has none).
    val showPicker = state.loading || state.styles.isNotEmpty()
    val pageCount = 1 + INTRO.size + if (showPicker) 1 else 0
    val pager = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    val onLanding = pager.currentPage == 0
    val onLastPage = pager.currentPage == pageCount - 1
    val soft = LocalSoft.current
    val next: () -> Unit = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }

    Box(Modifier.fillMaxSize().background(soft.base)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when {
                page == 0 -> LandingPage(state)
                page <= INTRO.size -> Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 56.dp, bottom = 90.dp)) {
                    IntroPageContent(INTRO[page - 1])
                }
                else -> Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 56.dp, bottom = 90.dp)) {
                    StylePicker(state, viewModel::toggle)
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            SoftTextButton(stringResource(R.string.skip), viewModel::skip, color = if (onLanding) Color.White.copy(alpha = 0.8f) else soft.textMuted)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onLanding) {
                AccentButton(stringResource(R.string.landing_cta), next, Modifier.fillMaxWidth(), height = 58.dp)
                return@Row
            }
            PageDots(pageCount, pager.currentPage, Modifier.weight(1f))
            when {
                !onLastPage -> AccentButton(stringResource(R.string.next), next)
                showPicker -> AccentButton(
                    text = if (state.canContinue) stringResource(R.string.continue_) else (StylePickerState.MIN_PICKS - state.selected.size).let { pluralStringResource(R.plurals.pick_n_more, it, it) },
                    onClick = viewModel::finish,
                    enabled = state.canContinue,
                )
                else -> AccentButton(stringResource(R.string.get_started), viewModel::finish)
            }
        }
    }
}

/**
 * Full-bleed photo landing: popular wallpapers crossfading behind an outlined pill, a big uppercase
 * headline and small corner captions. Falls back to the plain dark page with no network.
 */
@Composable
private fun LandingPage(state: StylePickerState) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.heroes.size) {
        while (state.heroes.size > 1) {
            delay(LANDING_PHOTO_MS)
            index = (index + 1) % state.heroes.size
        }
    }
    val hero = state.heroes.getOrNull(index)
    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0E))) {
        Crossfade(hero, animationSpec = tween(900), label = "landingPhoto") { wallpaper ->
            if (wallpaper != null && state.cdnBaseUrl.isNotEmpty()) {
                AsyncImage(wallpaper.imageUrl(state.cdnBaseUrl, ImageVariant.PREVIEW), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.3f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.92f))))
        val caption = MaterialTheme.typography.labelSmall
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 96.dp, top = 22.dp)) {
            Text(stringResource(R.string.landing_caption_left).uppercase(), style = caption, letterSpacing = 1.6.sp, color = Color.White.copy(alpha = 0.75f))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 104.dp)) {
            OutlinePill(stringResource(R.string.landing_pill).uppercase(), onClick = null, onDark = true)
            Text(
                stringResource(R.string.intro1_title).uppercase(),
                fontFamily = ZeulDisplay,
                fontSize = 44.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 18.dp).semantics { heading() },
            )
            Text(stringResource(R.string.intro1_body), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text(stringResource(R.string.app_wordmark), style = caption, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.weight(1f))
                hero?.authorName?.let { Text(stringResource(R.string.photo_by, it).uppercase(), style = caption, letterSpacing = 1.2.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1) }
            }
        }
    }
}

private const val LANDING_PHOTO_MS = 4_000L

/** Page indicator: the current page is a stretched accent pill, the rest small soft dots. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val soft = LocalSoft.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 26.dp else 8.dp, ZeulMotion.smooth(), label = "dotWidth")
            val color by animateColorAsState(if (i == current) soft.accent else soft.textMuted.copy(alpha = 0.35f), ZeulMotion.snappy(), label = "dotColor")
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

/** Profile → stringResource(R.string.your_styles): the same picker on its own, to change picks later. */
@Composable
fun StylesScreen(onBack: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onBack() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        SoftTopBar(onBack, stringResource(R.string.back))
        Box(Modifier.weight(1f)) { StylePicker(state, viewModel::toggle) }
        AccentButton(
            stringResource(R.string.save),
            viewModel::finish,
            enabled = state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
        )
    }
}

@Composable
private fun IntroPageContent(page: IntroPage) {
    val soft = LocalSoft.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The hamster in a raised soft circle, with a small accent badge for what the page is about.
        Box(Modifier.size(210.dp)) {
            Box(Modifier.size(190.dp).align(Alignment.Center).softRaised(CircleShape, depth = 10.dp), contentAlignment = Alignment.Center) {
                Image(
                    painterResource(R.drawable.boot_splash_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(182.dp).clip(CircleShape).graphicsLayer { scaleX = 1.35f; scaleY = 1.35f },
                )
            }
            Box(
                Modifier.size(56.dp).align(Alignment.BottomEnd).softRaised(CircleShape, depth = 5.dp, fill = soft.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(page.icon, null, tint = soft.onAccent, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(36.dp))
        Text(
            stringResource(page.title),
            fontFamily = ZeulDisplay,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = if (soft.isDark) FontWeight.Normal else FontWeight.Light,
            color = soft.wordmark,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, color = soft.textMuted, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StylePicker(state: StylePickerState, onToggle: (String) -> Unit) {
    val soft = LocalSoft.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(
            stringResource(R.string.pick_styles_title),
            fontFamily = ZeulDisplay,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = if (soft.isDark) FontWeight.Normal else FontWeight.Light,
            color = soft.wordmark,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.pick_styles_body, StylePickerState.MIN_PICKS),
            style = MaterialTheme.typography.bodyMedium,
            color = soft.textMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
        )
        if (state.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = soft.accent)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                state.styles.forEach { style ->
                    SoftChip(style.replaceFirstChar(Char::uppercase), selected = style in state.selected, onClick = { onToggle(style) })
                }
            }
        }
    }
}
