package com.wallpaperapp.app.ui.components

import com.wallpaperapp.app.ui.theme.ZeulRadius
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Set by AppNavHost: lets any screen take part in shared-element transitions without threading scopes. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Wraps a navigation destination so its content can find its AnimatedVisibilityScope. */
@Composable
fun AnimatedContentScope.WithNavScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavScope provides this, content = content)
}

/**
 * Hero transition for a wallpaper: the grid card and the preview page share bounds by id, so the
 * image grows from its card into the full screen (and shrinks back on return). [enabled] = false
 * leaves the element out (e.g. preview pages that aren't the current one).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedWallpaper(id: String, enabled: Boolean = true, corner: Dp = ZeulRadius.Card): Modifier {
    if (!enabled) return this
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    return with(shared) {
        this@sharedWallpaper.sharedBounds(
            sharedContentState = rememberSharedContentState("wallpaper-$id"),
            animatedVisibilityScope = nav,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.88f, stiffness = 320f) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
        )
    }
}
