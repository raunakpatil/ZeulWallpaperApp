package com.wallpaperapp.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Zeul motion language: calm, slightly springy, never bouncy. Every animation in the app should use
 * these so they feel related. Compose scales all of them by the system animator setting, so
 * "Remove animations" turns them off.
 */
object ZeulMotion {
    /** Presses, toggles, small things: quick, settles without overshoot. */
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    /** Moving indicators and shared bounds: a hint of overshoot, feels alive. */
    fun <T> smooth() = spring<T>(dampingRatio = 0.82f, stiffness = 380f)
    /** Things that enter the screen. */
    fun <T> gentle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

    const val SHORT = 180
    const val MEDIUM = 300
    const val LONG = 420
    val Emphasized = FastOutSlowInEasing
}

/** Press state for soft surfaces: [progress] goes 0 → 1 while a finger is down (animated). */
@Stable
class SoftPress(val interaction: MutableInteractionSource, private val progressState: androidx.compose.runtime.State<Float>) {
    val progress: Float get() = progressState.value
}

@Composable
fun rememberSoftPress(): SoftPress {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress = animateFloatAsState(if (pressed) 1f else 0f, ZeulMotion.snappy(), label = "press")
    return remember(interaction) { SoftPress(interaction, progress) }
}

/** Sinks the element while pressed (scale), the way a soft button gives under a finger. */
fun Modifier.pressScale(press: SoftPress, amount: Float = 0.04f): Modifier = graphicsLayer {
    val s = 1f - amount * press.progress
    scaleX = s
    scaleY = s
}

/**
 * Click for soft surfaces: no ripple (the sink + flattening shadow is the feedback), optional haptic tick.
 * Use with the same [press] passed to [pressScale] / softRaised(pressed = press.progress).
 */
fun Modifier.softClickable(
    press: SoftPress,
    onClickLabel: String? = null,
    role: Role = Role.Button,
    haptic: HapticFeedbackType? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    clickable(interactionSource = press.interaction, indication = null, onClickLabel = onClickLabel, role = role) {
        haptic?.let(haptics::performHapticFeedback)
        onClick()
    }
}

/** Soft shimmer for loading placeholders: a slow light band sweeping across. */
fun Modifier.softShimmer(): Modifier = composed {
    val soft = LocalSoft.current
    val width = with(LocalDensity.current) { 420.dp.toPx() }
    val t by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = if (soft.isDark) Color(0xFF1C1C1E) else Color(0xFFE1E3E7)
    val glow = if (soft.isDark) Color(0xFF2A2A2D) else Color(0xFFF3F4F6)
    background(Brush.linearGradient(listOf(base, glow, base), start = Offset(width * t - width, 0f), end = Offset(width * t, width * 0.4f)))
}

/**
 * Rise-in for list/grid items: fades up from 18dp below, staggered by [index] (first screenful only).
 * [animate] = false shows the item immediately (already seen, or past the first screenful).
 */
fun Modifier.riseIn(index: Int, animate: Boolean): Modifier = composed {
    val anim = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(index.coerceAtMost(8) * 45L)
            anim.animateTo(1f, tween(ZeulMotion.LONG, easing = ZeulMotion.Emphasized))
        }
    }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * rise
    }
}

/** Heart-style pop when [active] turns on (not on first show): scale 1 → 1.3 → 1 with a soft spring. */
fun Modifier.popOnActivate(active: Boolean): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(active) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        if (active) {
            scale.animateTo(1.32f, spring(dampingRatio = 0.5f, stiffness = 900f))
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        } else {
            scale.animateTo(0.85f, tween(90))
            scale.animateTo(1f, ZeulMotion.snappy())
        }
    }
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
