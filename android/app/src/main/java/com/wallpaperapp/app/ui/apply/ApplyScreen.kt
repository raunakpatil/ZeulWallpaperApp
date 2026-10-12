package com.wallpaperapp.app.ui.apply

import com.wallpaperapp.app.ui.theme.ZeulRadius
import com.wallpaperapp.app.ui.theme.softClickable
import com.wallpaperapp.app.ui.theme.rememberSoftPress
import com.wallpaperapp.app.ui.theme.pressScale
import com.wallpaperapp.app.ui.theme.glassPanel
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.GlassIconButton
import com.wallpaperapp.app.ui.theme.AccentButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.height
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperapp.app.domain.model.ApplyTarget
import com.wallpaperapp.app.domain.model.CropMath
import com.wallpaperapp.app.ui.settings.rememberNotificationPermissionRequest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Apply editor. The whole screen is the viewport: pinch and drag to choose what fills it,
 * adjust blur and dim, then set it on the home screen, lock screen or both.
 */
@Composable
fun ApplyScreen(onBack: () -> Unit, viewModel: ApplyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val activity = LocalActivity.current
    val askNotifications = rememberNotificationPermissionRequest()
    LaunchedEffect(state.askReview) {
        if (state.askReview && activity != null) {
            askNotifications()
            viewModel.askForReview(activity)
        }
    }
    val messageText = state.message?.asString()
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val viewportW = with(density) { maxWidth.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        LaunchedEffect(Unit) { viewModel.load(maxLongEdge = (max(viewportW, viewportH) * 1.5f).roundToInt()) }

        val source = state.source
        when {
            state.loadError -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.apply_load_error), color = Color.White)
                TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) }
            }
            source == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            else -> {
                var zoom by rememberSaveable { mutableFloatStateOf(1f) }
                var panX by rememberSaveable { mutableFloatStateOf(0f) }
                var panY by rememberSaveable { mutableFloatStateOf(0f) }
                val shown = remember(state.blurred, source) { (state.blurred ?: source).asImageBitmap() }

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(source) {
                            detectTransformGestures { _, pan, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                val (x, y) = CropMath.clampPan(panX + pan.x, panY + pan.y, zoom, source.width, source.height, viewportW, viewportH)
                                panX = x
                                panY = y
                            }
                        },
                ) {
                    val scale = CropMath.baseScale(source.width, source.height, size.width, size.height) * zoom
                    val drawnW = source.width * scale
                    val drawnH = source.height * scale
                    drawImage(
                        image = shown,
                        dstOffset = IntOffset(((size.width - drawnW) / 2 + panX).roundToInt(), ((size.height - drawnH) / 2 + panY).roundToInt()),
                        dstSize = IntSize(drawnW.roundToInt(), drawnH.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                    // Dim preview: same black overlay the final render draws.
                    if (state.edits.dim > 0f) drawRect(Color.Black.copy(alpha = state.edits.dim))
                }

                Controls(
                    blur = state.edits.blur,
                    dim = state.edits.dim,
                    applying = state.applying,
                    onBlur = viewModel::setBlur,
                    onDim = viewModel::setDim,
                    onApply = { target ->
                        val crop = CropMath.crop(panX, panY, zoom, source.width, source.height, viewportW, viewportH)
                        viewModel.apply(target, crop, viewportW.roundToInt(), viewportH.roundToInt())
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        TopBar(onBack = onBack, onReset = viewModel::reset, modifier = Modifier.align(Alignment.TopCenter))
        SnackbarHost(snackbar, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onReset: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(ZeulIcons.Back, stringResource(R.string.back), onBack)
        Text(
            stringResource(R.string.apply_hint),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.glassPanel(ZeulRadius.Small).padding(horizontal = 14.dp, vertical = 8.dp),
        )
        GlassIconButton(ZeulIcons.Reset, stringResource(R.string.reset), onReset)
    }
}

/** Floating frosted panel: blur and dim sliders, then Home / Lock (glass) and Both (orange). */
@Composable
private fun Controls(
    blur: Float,
    dim: Float,
    applying: ApplyTarget?,
    onBlur: (Float) -> Unit,
    onDim: (Float) -> Unit,
    onApply: (ApplyTarget) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .glassPanel(ZeulRadius.Panel)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        LabeledSlider(stringResource(R.string.blur), blur, onBlur)
        LabeledSlider(stringResource(R.string.dim), dim, onDim, range = 0f..0.8f)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val enabled = applying == null
            GlassPill(stringResource(R.string.target_home), applying == ApplyTarget.HOME, enabled, Modifier.weight(1f)) { onApply(ApplyTarget.HOME) }
            GlassPill(stringResource(R.string.target_lock), applying == ApplyTarget.LOCK, enabled, Modifier.weight(1f)) { onApply(ApplyTarget.LOCK) }
            AccentButton(
                stringResource(R.string.target_both),
                { onApply(ApplyTarget.BOTH) },
                Modifier.weight(1f),
                enabled = enabled,
                loading = applying == ApplyTarget.BOTH,
                height = 48.dp,
            )
        }
    }
}

/** Glass pill button for the apply targets (busy = spinner). */
@Composable
private fun GlassPill(text: String, busy: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val press = rememberSoftPress()
    Box(
        modifier
            .height(48.dp)
            .pressScale(press, 0.04f)
            .glassPanel(ZeulRadius.Control)
            .then(if (enabled) Modifier.softClickable(press, text, haptic = HapticFeedbackType.ContextClick, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    val soft = LocalSoft.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = soft.accent, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
    }
}
