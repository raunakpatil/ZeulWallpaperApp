package com.wallpaperapp.app.ui.preview

import com.wallpaperapp.app.ui.theme.ZeulRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.wallpaperapp.app.ui.components.OutlinePill
import com.wallpaperapp.app.ui.theme.pressScale
import com.wallpaperapp.app.ui.theme.rememberSoftPress
import com.wallpaperapp.app.ui.theme.softClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.wallpaperapp.app.ui.theme.softSheetColor
import com.wallpaperapp.app.ui.theme.softRaised
import com.wallpaperapp.app.ui.theme.popOnActivate
import com.wallpaperapp.app.ui.theme.glassPanel
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.ZeulDisplay
import com.wallpaperapp.app.ui.theme.SoftTextButton
import com.wallpaperapp.app.ui.theme.SoftSectionLabel
import com.wallpaperapp.app.ui.theme.SoftChip
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.SoftDark
import androidx.compose.runtime.CompositionLocalProvider
import com.wallpaperapp.app.ui.theme.GlassIconButton
import com.wallpaperapp.app.ui.theme.AccentButton
import com.wallpaperapp.app.ui.components.sharedWallpaper
import android.app.Activity
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import com.wallpaperapp.app.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperapp.app.domain.model.ImageVariant
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.imageUrl
import com.wallpaperapp.app.ui.components.BlurHash
import com.wallpaperapp.app.ui.settings.rememberNotificationPermissionRequest
import com.wallpaperapp.app.ui.components.parseHexColor
import coil3.compose.AsyncImage
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    onApply: (Wallpaper) -> Unit,
    onTagClick: (String) -> Unit,
    onGoPremium: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ImmersiveSystemBars(hidden = !state.chromeVisible)
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.asString()
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    // Android 8–9 need storage permission to save to Pictures; newer versions don't.
    var pendingDownload by remember { mutableStateOf<Wallpaper?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val wallpaper = pendingDownload
        pendingDownload = null
        if (granted && wallpaper != null) viewModel.download(wallpaper)
    }
    val context = LocalContext.current
    val performDownload: (Wallpaper) -> Unit = { wallpaper ->
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = wallpaper
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.download(wallpaper)
        }
    }

    val hostActivity = LocalActivity.current
    val askNotifications = rememberNotificationPermissionRequest()
    LaunchedEffect(state.askReview) {
        if (state.askReview && hostActivity != null) {
            askNotifications()
            viewModel.askForReview(hostActivity)
        }
    }

    // Download/Apply that passed the unlock gate (or were just unlocked).
    LaunchedEffect(state.proceed) {
        val (wallpaper, action) = state.proceed ?: return@LaunchedEffect
        viewModel.proceedHandled()
        when (action) {
            GatedAction.DOWNLOAD -> performDownload(wallpaper)
            GatedAction.APPLY -> onApply(wallpaper)
        }
    }
    // The rewarded ad is its own activity: pause/resume tells us the user came back from it.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onScreenPaused() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onScreenResumed() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            state.wallpapers.isEmpty() -> NotFound(onBack)
            else -> PreviewPager(
                state = state,
                viewModel = viewModel,
                onBack = onBack,
                onDownload = { viewModel.request(it, GatedAction.DOWNLOAD) },
                onApply = { viewModel.request(it, GatedAction.APPLY) },
                onTagClick = onTagClick,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }

    state.unlockSheet?.let { sheet ->
        val activity = LocalActivity.current
        UnlockSheet(
            sheet = sheet,
            onWatchAd = { activity?.let(viewModel::watchAd) },
            onGoPremium = onGoPremium,
            onDismiss = viewModel::dismissSheet,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewPager(
    state: PreviewUiState,
    viewModel: PreviewViewModel,
    onBack: () -> Unit,
    onDownload: (Wallpaper) -> Unit,
    onApply: (Wallpaper) -> Unit,
    onTagClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = state.initialIndex.coerceAtLeast(0)) { state.wallpapers.size }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var zoomed by rememberSaveable { mutableStateOf(false) }
    val current = state.wallpapers.getOrNull(pagerState.currentPage)

    LaunchedEffect(pagerState, state.wallpapers) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            state.wallpapers.getOrNull(page)?.let(viewModel::onPageShown)
        }
    }
    // Back first restores the controls, then leaves zoom, then leaves the screen.
    BackHandler(enabled = zoomed) { zoomed = false }
    BackHandler(enabled = !state.chromeVisible) { viewModel.toggleChrome() }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = !zoomed,
        key = { state.wallpapers[it].id },
    ) { page ->
        WallpaperPage(
            wallpaper = state.wallpapers[page],
            cdnBaseUrl = state.cdnBaseUrl,
            hero = page == pagerState.currentPage && !zoomed,
            zoomed = zoomed && page == pagerState.currentPage,
            onZoom = { zoomed = true },
            onTap = viewModel::toggleChrome,
        )
    }

    DeviceOverlay(state.overlay)

    AnimatedVisibility(visible = state.chromeVisible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            TopBar(
                overlay = state.overlay,
                isFavorite = current != null && current.id in state.favoriteIds,
                onBack = { if (zoomed) zoomed = false else onBack() },
                onOverlayClick = viewModel::cycleOverlay,
                onShare = { current?.let { shareWallpaper(context, it) } },
                onFavorite = { current?.let(viewModel::toggleFavorite) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            // The fake dock and search bar need the bottom of the screen while an overlay shows.
            if (current != null && state.overlay == OverlayMode.NONE) {
                BottomBar(
                    wallpaper = current,
                    cdnBaseUrl = state.cdnBaseUrl,
                    onInfo = { showInfo = true },
                    onTagClick = onTagClick,
                    downloading = state.downloadingId == current.id,
                    onDownload = { onDownload(current) },
                    onApply = { onApply(current) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    var reporting by remember { mutableStateOf<Wallpaper?>(null) }
    if (showInfo && current != null) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }, containerColor = softSheetColor()) {
            InfoSheet(
                current,
                onTagClick = { tag ->
                    showInfo = false
                    onTagClick(tag)
                },
                onReport = {
                    showInfo = false
                    reporting = current
                },
            )
        }
    }
    reporting?.let { wallpaper ->
        ReportDialog(
            onDismiss = { reporting = null },
            onSubmit = { reason ->
                reporting = null
                viewModel.report(wallpaper, reason)
            },
        )
    }
}

@Composable
private fun WallpaperPage(
    wallpaper: Wallpaper,
    cdnBaseUrl: String,
    hero: Boolean,
    zoomed: Boolean,
    onZoom: () -> Unit,
    onTap: () -> Unit,
) {
    val blur = remember(wallpaper.blurhash) { wallpaper.blurhash?.let { BlurHash.decodeImageBitmap(it) } }
    val background = remember(wallpaper.dominantColor) { parseHexColor(wallpaper.dominantColor) ?: Color.Black }
    val url = wallpaper.imageUrl(cdnBaseUrl, ImageVariant.PREVIEW)
    Box(Modifier.fillMaxSize().sharedWallpaper(wallpaper.id, enabled = hero, corner = 0.dp).background(if (zoomed) Color.Black else background)) {
        if (zoomed) {
            // Whole image, free pinch/pan. Paging is paused until the user leaves this mode.
            ZoomableAsyncImage(
                model = url,
                contentDescription = wallpaper.title,
                contentScale = ContentScale.Fit,
                onClick = { onTap() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Cropped to fill the screen, the way it will look as a wallpaper. One-finger swipes
            // go to the pager; a pinch or double tap switches to the zoomable view.
            if (blur != null) Image(blur, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // The grid thumbnail is already cached: it keeps the hero transition sharp while the
            // preview-size image loads, then the larger one crossfades in on top.
            AsyncImage(
                model = wallpaper.imageUrl(cdnBaseUrl, ImageVariant.THUMB),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = url,
                contentDescription = wallpaper.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { onZoom() }) }
                    .pointerInput(Unit) { detectPinchStart(onZoom) },
            )
        }
    }
}

/** Calls [onPinch] as soon as a second finger touches, without consuming anything. */
private suspend fun PointerInputScope.detectPinchStart(onPinch: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                onPinch()
                break
            }
        } while (event.changes.any { it.pressed })
    }
}

private val scrim = Color.Black.copy(alpha = 0.45f)

@Composable
private fun TopBar(
    overlay: OverlayMode,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onOverlayClick: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier,
) {
    val soft = LocalSoft.current
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(scrim, Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        GlassIconButton(ZeulIcons.Back, stringResource(R.string.back), onBack, Modifier.align(Alignment.CenterStart))
        // The centre pill names the mode and cycles it: plain → lock screen → home screen.
        val (modeLabel, modeAction) = when (overlay) {
            OverlayMode.NONE -> R.string.preview_mode_plain to R.string.overlay_lock
            OverlayMode.LOCK -> R.string.preview_mode_lock to R.string.overlay_home
            OverlayMode.HOME -> R.string.preview_mode_home to R.string.overlay_hide
        }
        val actionLabel = stringResource(modeAction)
        val press = rememberSoftPress()
        Row(
            Modifier
                .align(Alignment.Center)
                .height(38.dp)
                .pressScale(press, 0.05f)
                .glassPanel(ZeulRadius.Control)
                .softClickable(press, actionLabel, onClick = onOverlayClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (overlay == OverlayMode.NONE) ZeulIcons.Lock else ZeulIcons.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text(stringResource(modeLabel).uppercase(), color = Color.White, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIconButton(ZeulIcons.Share, stringResource(R.string.share), onShare)
            val heartLabel = stringResource(if (isFavorite) R.string.favourite_remove else R.string.favourite_add)
            GlassIconButton(
                ZeulIcons.Heart,
                heartLabel,
                onFavorite,
                haptic = if (isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                content = {
                    Icon(
                        if (isFavorite) ZeulIcons.HeartFilled else ZeulIcons.Heart,
                        heartLabel,
                        tint = if (isFavorite) soft.accent else Color.White,
                        modifier = Modifier.size(21.dp).popOnActivate(isFavorite),
                    )
                },
            )
        }
    }
}

/**
 * Floating info card: title + downloads, BY AUTHOR · CATEGORY, tag pills, the image's colour
 * palette + resolution, then the orange Set Wallpaper pill and a round download button.
 */
@Composable
private fun BottomBar(
    wallpaper: Wallpaper,
    cdnBaseUrl: String,
    onInfo: () -> Unit,
    onTagClick: (String) -> Unit,
    downloading: Boolean,
    onDownload: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier,
) {
    val muted = Color.White.copy(alpha = 0.62f)
    // Always the dark palette: the card sits on a photo, so light-mode shadows would halo the buttons.
    CompositionLocalProvider(LocalSoft provides SoftDark) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, scrim)))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .glassPanel(ZeulRadius.Panel)
            .background(CardFill)
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val detailsLabel = stringResource(R.string.details)
        val titlePress = rememberSoftPress()
        Row(Modifier.fillMaxWidth().softClickable(titlePress, detailsLabel, onClick = onInfo), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(wallpaper.title, color = Color.White, fontFamily = ZeulDisplay, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val byline = listOfNotNull(wallpaper.authorName?.let { stringResource(R.string.by_author, it) }, wallpaper.categoryName).joinToString("  ·  ")
                if (byline.isNotEmpty()) {
                    Text(byline.uppercase(), color = muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.4.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (wallpaper.downloadCount > 0) {
                val count = compactCount(wallpaper.downloadCount)
                val spoken = stringResource(R.string.downloads_count, count)
                Row(
                    Modifier.padding(start = 12.dp, top = 4.dp).clearAndSetSemantics { contentDescription = spoken },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ZeulIcons.Download, null, tint = muted, modifier = Modifier.size(15.dp))
                    Text(count, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (wallpaper.tags.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wallpaper.tags.take(8).forEach { tag -> OutlinePill(tag, onClick = { onTagClick(tag) }, onDark = true) }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PaletteDots(rememberPalette(wallpaper, cdnBaseUrl), Modifier.weight(1f))
            if (wallpaper.width != null && wallpaper.height != null) {
                Text("${wallpaper.width} × ${wallpaper.height}", color = muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 4.dp))
            }
            GlassIconButton(ZeulIcons.Info, detailsLabel, onInfo, size = 36.dp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentButton(stringResource(R.string.set_wallpaper), onApply, Modifier.weight(1f), height = 54.dp, leading = ZeulIcons.Sparkle)
            Spacer(Modifier.width(10.dp))
            GlassIconButton(ZeulIcons.Download, stringResource(R.string.download), onDownload, size = 54.dp, content = if (downloading) {
                { CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) }
            } else null)
        }
    }
    }
}

private val CardFill = Color(0x990D0D0E)

@Composable
private fun PaletteDots(colors: List<Color>, modifier: Modifier) {
    val label = stringResource(R.string.preview_palette)
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        colors.forEach { color ->
            Box(Modifier.size(26.dp).clip(CircleShape).background(color).border(2.dp, Color(0xFF1A1A1C), CircleShape))
        }
    }
}

/**
 * Up to five main colours of the image (androidx.palette over the cached thumbnail). Starts with
 * the server's dominant colour so the row isn't empty while it works.
 */
@Composable
private fun rememberPalette(wallpaper: Wallpaper, cdnBaseUrl: String): List<Color> {
    val context = LocalContext.current
    val initial = remember(wallpaper.id) { listOfNotNull(parseHexColor(wallpaper.dominantColor)) }
    val colors by produceState(initial, wallpaper.id) {
        val request = ImageRequest.Builder(context)
            .data(wallpaper.imageUrl(cdnBaseUrl, ImageVariant.THUMB))
            .allowHardware(false) // Palette has to read the pixels
            .size(96)
            .build()
        val image = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image ?: return@produceState
        value = withContext(Dispatchers.Default) {
            Palette.from(image.toBitmap()).maximumColorCount(12).generate()
                .swatches.sortedByDescending { it.population }.take(5).map { Color(it.rgb) }
        }.ifEmpty { initial }
    }
    return colors
}

/** 950 → "950", 1 234 → "1.2k", 3 400 000 → "3.4M". */
internal fun compactCount(n: Int): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> "%.1fk".format(Locale.US, n / 1_000.0).replace(".0k", "k")
    else -> "%.1fM".format(Locale.US, n / 1_000_000.0).replace(".0M", "M")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoSheet(wallpaper: Wallpaper, onTagClick: (String) -> Unit, onReport: () -> Unit) {
    val soft = LocalSoft.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(wallpaper.title, fontFamily = ZeulDisplay, fontSize = 26.sp, fontWeight = if (soft.isDark) FontWeight.Normal else FontWeight.Light, color = soft.wordmark)
        val details = buildList {
            if (wallpaper.width != null && wallpaper.height != null) add(stringResource(R.string.info_resolution) to "${wallpaper.width} × ${wallpaper.height}")
            wallpaper.fileSizeKb?.let { add(stringResource(R.string.info_size) to formatSize(it)) }
            wallpaper.categoryName?.let { add(stringResource(R.string.filter_category) to it) }
            // A brand-new wallpaper's zero counts read as "nobody likes this"; only show real numbers.
            if (wallpaper.downloadCount > 0) add(stringResource(R.string.downloads_tab) to "%,d".format(wallpaper.downloadCount))
            if (wallpaper.favCount > 0) add(stringResource(R.string.favourites_tab) to "%,d".format(wallpaper.favCount))
            wallpaper.authorName?.let { add(stringResource(R.string.info_author) to it) }
            wallpaper.license?.let { add(stringResource(R.string.info_licence) to it) }
        }
        Column(
            Modifier.fillMaxWidth().softRaised(RoundedCornerShape(ZeulRadius.Card), depth = 4.dp).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            details.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = soft.textMuted, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = soft.text, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (wallpaper.tags.isNotEmpty()) {
            SoftSectionLabel(stringResource(R.string.info_tags))
            // Tapping a tag searches for it.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                wallpaper.tags.forEach { tag -> SoftChip("#$tag", selected = false, onClick = { onTagClick(tag) }) }
            }
        }
        // The terms point here for copyright complaints (A12), so keep the stringResource(R.string.report) label.
        SoftTextButton(stringResource(R.string.report), onReport, color = soft.textMuted)
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.not_available), color = Color.White, style = MaterialTheme.typography.titleMedium)
        SoftTextButton(stringResource(R.string.go_back), onBack)
    }
}

/** Light status/nav icons over the image; hides the system bars while the controls are hidden. */
@Composable
private fun ImmersiveSystemBars(hidden: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(hidden) {
        val controller = WindowCompat.getInsetsController(window, view)
        val wasLight = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = wasLight
            controller.isAppearanceLightNavigationBars = wasLight
        }
    }
}

internal fun formatSize(kb: Int): String = if (kb >= 1024) "%.1f MB".format(kb / 1024.0) else "$kb KB"
