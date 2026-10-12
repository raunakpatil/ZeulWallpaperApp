package com.wallpaperapp.app.ui.components

import com.wallpaperapp.app.ui.theme.LocalCardStyle
import com.wallpaperapp.app.ui.theme.CardStyle
import com.wallpaperapp.app.ui.theme.ZeulRadius
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.wallpaperapp.app.ui.theme.GlassEdge
import com.wallpaperapp.app.ui.theme.GlassFill
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.ZeulDisplay
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.ZeulMotion
import com.wallpaperapp.app.ui.theme.pressScale
import com.wallpaperapp.app.ui.theme.rememberSoftPress
import com.wallpaperapp.app.ui.theme.softClickable
import com.wallpaperapp.app.ui.theme.softRaised

/*
 * Photo-led pieces from the B14 redesign: big rounded image cards (label pill top-left, ↗ button
 * top-right, two-line title bottom-left), small pills for use over photos, outlined tag pills and
 * icon tabs. Colours stay Zeul black + orange; see docs/design/app-ui-soft.md.
 */

/**
 * Big rounded image card. The caller sizes it (height or aspectRatio). [label] is the small pill in
 * the top-left corner (a count, "New"…); [title] and [subtitle] sit bottom-left over a dark fade.
 */
@Composable
fun ImageCard(
    imageUrl: String?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    label: String? = null,
    labelIcon: ImageVector? = null,
    blurhash: String? = null,
    dominantColor: String? = null,
    corner: Dp = ZeulRadius.Card,
    titleSize: Int = 22,
) {
    val soft = LocalSoft.current
    val press = rememberSoftPress()
    val blur = remember(blurhash) { blurhash?.let { BlurHash.decodeImageBitmap(it) } }
    val background = remember(dominantColor) { parseHexColor(dominantColor) } ?: soft.raised
    val style = LocalCardStyle.current
    val shape = RoundedCornerShape(if (style == CardStyle.SOFT) corner else style.corner)
    Box(
        modifier
            .pressScale(press, 0.03f)
            .then(
                when (style) {
                    CardStyle.SOFT -> Modifier.softRaised(shape, depth = 5.dp, pressed = press.progress)
                    CardStyle.MAT -> Modifier
                        .background(matColor(soft.isDark), RoundedCornerShape(4.dp))
                        .border(1.dp, soft.textMuted.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                    else -> Modifier
                },
            )
            .clip(shape)
            .background(background)
            .softClickable(press, title, onClick = onClick),
    ) {
        if (blur != null) Image(blur, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (imageUrl != null) AsyncImage(imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
        if (label != null) PhotoPill(label, Modifier.align(Alignment.TopStart).padding(14.dp), icon = labelIcon)
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(40.dp).clip(RoundedCornerShape(ZeulRadius.Control)).background(GlassFill).border(1.dp, GlassEdge, RoundedCornerShape(ZeulRadius.Control)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ZeulIcons.ArrowUpRight, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
            Text(title, fontFamily = ZeulDisplay, fontSize = titleSize.sp, lineHeight = (titleSize + 4).sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, fontFamily = ZeulDisplay, fontSize = (titleSize - 5).sp, lineHeight = titleSize.sp, fontWeight = FontWeight.Light, color = Color.White.copy(alpha = 0.88f), maxLines = 1)
            }
        }
    }
}

/** Small frosted pill for text over a photo ("12 wallpapers", "PREVIEW"). */
@Composable
fun PhotoPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.height(30.dp).clip(RoundedCornerShape(ZeulRadius.Small)).background(GlassFill).border(1.dp, GlassEdge, RoundedCornerShape(ZeulRadius.Small)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Outlined pill (tags). [onDark] = drawn over a photo/dark card, so it's white-on-glass. */
@Composable
fun OutlinePill(text: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, onDark: Boolean = false) {
    val soft = LocalSoft.current
    val press = rememberSoftPress()
    val content = if (onDark) Color.White.copy(alpha = 0.9f) else soft.text
    val edge = if (onDark) Color.White.copy(alpha = 0.28f) else soft.textMuted.copy(alpha = 0.5f)
    Box(
        modifier
            .height(32.dp)
            .pressScale(press, 0.05f)
            .clip(RoundedCornerShape(ZeulRadius.Small))
            .border(BorderStroke(1.dp, edge), RoundedCornerShape(ZeulRadius.Small))
            .then(if (onClick != null) Modifier.softClickable(press, text, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Icon + label tab pill. Selected = filled Zeul orange; others are raised soft pills. */
@Composable
fun IconTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val soft = LocalSoft.current
    val press = rememberSoftPress()
    val haptics = LocalHapticFeedback.current
    val fill by animateColorAsState(if (selected) soft.accent else soft.raised, ZeulMotion.snappy(), label = "tabFill")
    val content by animateColorAsState(if (selected) soft.onAccent else soft.text, ZeulMotion.snappy(), label = "tabText")
    Row(
        modifier
            .height(44.dp)
            .pressScale(press, 0.04f)
            .softRaised(RoundedCornerShape(ZeulRadius.Control), depth = if (selected) 4.dp else 3.dp, fill = fill, pressed = press.progress)
            .selectable(
                selected = selected,
                interactionSource = press.interaction,
                indication = null,
                role = Role.Tab,
                onClick = { if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onClick() },
            )
            .padding(start = 14.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Text(label, color = content, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1)
    }
}

/** Round soft button (search, ↗ …) in the screen header. */
@Composable
fun RoundSoftButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 52.dp, tint: Color? = null) {
    val soft = LocalSoft.current
    val press = rememberSoftPress()
    Box(
        modifier
            .size(size)
            .pressScale(press, 0.06f)
            .softRaised(RoundedCornerShape(ZeulRadius.Control), depth = 5.dp, pressed = press.progress)
            .softClickable(press, contentDescription, haptic = HapticFeedbackType.ContextClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint ?: soft.wordmark, modifier = Modifier.size(size * 0.44f))
    }
}
