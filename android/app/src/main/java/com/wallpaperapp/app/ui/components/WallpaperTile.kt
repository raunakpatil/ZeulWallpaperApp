package com.wallpaperapp.app.ui.components

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import com.wallpaperapp.app.ui.theme.LocalCardStyle
import com.wallpaperapp.app.ui.theme.CardStyle
import com.wallpaperapp.app.ui.theme.ZeulRadius
import com.wallpaperapp.app.ui.theme.softShimmer
import com.wallpaperapp.app.ui.theme.rememberSoftPress
import com.wallpaperapp.app.ui.theme.pressScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.Dp
import com.wallpaperapp.app.ui.theme.GlassIconButton
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.popOnActivate
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.softRaised
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wallpaperapp.app.domain.model.ImageVariant
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.imageUrl

/**
 * Grid tile: dominant colour, then blurhash, then thumbnail (crossfade). Keeps the wallpaper's own
 * aspect ratio within sane bounds. Used as-is where there is no room for actions (detail pane).
 */
@Composable
fun WallpaperTile(
    wallpaper: Wallpaper,
    cdnBaseUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = tileAspectRatio(wallpaper),
    corner: Dp = 12.dp,
    interaction: MutableInteractionSource? = null,
    premiumAt: Alignment = Alignment.TopEnd,
) {
    val placeholderColor = remember(wallpaper.dominantColor) { parseHexColor(wallpaper.dominantColor) }
    val blur = remember(wallpaper.blurhash) { wallpaper.blurhash?.let { BlurHash.decodeImageBitmap(it) } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .sharedWallpaper(wallpaper.id, enabled = interaction != null, corner = corner)
            .clip(RoundedCornerShape(corner))
            .background(placeholderColor ?: MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (interaction != null) Modifier.clickable(interaction, indication = null, onClickLabel = wallpaper.title, role = Role.Image, onClick = onClick)
                else Modifier.clickable(onClickLabel = wallpaper.title, role = Role.Image, onClick = onClick),
            ),
    ) {
        if (blur != null) {
            Image(blur, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        AsyncImage(
            model = wallpaper.imageUrl(cdnBaseUrl, ImageVariant.THUMB),
            contentDescription = wallpaper.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (wallpaper.isPremium) {
            Icon(
                imageVector = ZeulIcons.Gem,
                contentDescription = stringResource(R.string.premium),
                tint = Color.White,
                modifier = Modifier
                    .align(premiumAt)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(5.dp)
                    .size(13.dp),
            )
        }
    }
}

/**
 * Photo card for the grids. Its frame follows [LocalCardStyle]: a raised soft frame, a bare photo, a
 * tight gallery tile, a photo with a caption, or a framed print. Downloading lives in the preview.
 */
@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    cdnBaseUrl: String,
    isFavourite: Boolean,
    onClick: () -> Unit,
    onFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val press = rememberSoftPress()
    val soft = LocalSoft.current
    val style = LocalCardStyle.current
    val heart: @Composable BoxScope.(plain: Boolean) -> Unit = { plain ->
        TileHeart(isFavourite, onFavourite, plain, Modifier.align(Alignment.TopEnd).padding(if (style == CardStyle.GALLERY) 4.dp else 8.dp))
    }
    val tile: @Composable () -> Unit = {
        WallpaperTile(wallpaper, cdnBaseUrl, onClick, aspectRatio = CARD_RATIO, corner = style.corner, interaction = press.interaction, premiumAt = Alignment.TopStart)
    }
    when (style) {
        CardStyle.SOFT -> Box(
            modifier.fillMaxWidth().pressScale(press, 0.03f)
                .softRaised(RoundedCornerShape(style.corner), depth = 5.dp, pressed = press.progress),
        ) { tile(); heart(false) }
        CardStyle.FLAT, CardStyle.GALLERY -> Box(modifier.fillMaxWidth().pressScale(press, 0.02f)) { tile(); heart(true) }
        CardStyle.CAPTION -> Column(modifier.fillMaxWidth().pressScale(press, 0.02f)) {
            tile()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 2.dp)) {
                    Text(wallpaper.title, style = MaterialTheme.typography.titleSmall, color = soft.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    wallpaper.categoryName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = soft.textMuted, maxLines = 1) }
                }
                FavouriteToggle(isFavourite, onFavourite, tint = if (isFavourite) soft.accent else soft.textMuted)
            }
        }
        CardStyle.MAT -> Box(
            modifier.fillMaxWidth().pressScale(press, 0.02f)
                .background(matColor(soft.isDark), RoundedCornerShape(4.dp))
                .border(1.dp, soft.textMuted.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                .padding(6.dp),
        ) { tile(); heart(true) }
    }
}

/** Paper mat behind a framed tile: white on the grey light theme, charcoal on black. */
internal fun matColor(dark: Boolean): Color = if (dark) Color(0xFF1C1C1F) else Color(0xFFFAFAFB)

/** Heart over a photo: a frosted square ([plain] = false) or a bare icon on a soft dark glow. */
@Composable
private fun TileHeart(isFavourite: Boolean, onFavourite: () -> Unit, plain: Boolean, modifier: Modifier) {
    val soft = LocalSoft.current
    val label = stringResource(if (isFavourite) R.string.favourite_remove else R.string.favourite_add)
    val icon: @Composable () -> Unit = {
        Icon(
            if (isFavourite) ZeulIcons.HeartFilled else ZeulIcons.Heart,
            label,
            tint = if (isFavourite) soft.accent else Color.White,
            modifier = Modifier.size(if (plain) 20.dp else 17.dp).popOnActivate(isFavourite),
        )
    }
    if (!plain) {
        GlassIconButton(
            ZeulIcons.Heart, label, onFavourite, modifier, size = 36.dp,
            haptic = if (isFavourite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
            content = icon,
        )
    } else {
        val haptics = LocalHapticFeedback.current
        Box(
            modifier
                .size(40.dp)
                .background(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent)), CircleShape)
                .clickable(onClickLabel = label, role = Role.Button) {
                    haptics.performHapticFeedback(if (isFavourite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onFavourite()
                },
            contentAlignment = Alignment.Center,
        ) { icon() }
    }
}

/** Heart beside a caption (no photo behind it). */
@Composable
private fun FavouriteToggle(isFavourite: Boolean, onFavourite: () -> Unit, tint: Color) {
    val label = stringResource(if (isFavourite) R.string.favourite_remove else R.string.favourite_add)
    Box(Modifier.size(40.dp).clickable(onClickLabel = label, role = Role.Button, onClick = onFavourite), contentAlignment = Alignment.Center) {
        Icon(if (isFavourite) ZeulIcons.HeartFilled else ZeulIcons.Heart, label, tint = tint, modifier = Modifier.size(20.dp).popOnActivate(isFavourite))
    }
}

/** Placeholder card while the first page loads, in the current card style. */
@Composable
fun WallpaperTileSkeleton(aspectRatio: Float, modifier: Modifier = Modifier) {
    val style = LocalCardStyle.current
    Box(
        modifier
            .fillMaxWidth()
            .then(if (style == CardStyle.SOFT) Modifier.softRaised(RoundedCornerShape(style.corner), depth = 5.dp) else Modifier)
            .aspectRatio(CARD_RATIO)
            .clip(RoundedCornerShape(style.corner))
            .softShimmer(),
    )
}

private val CARD_CORNER = ZeulRadius.Card

/** Cards share one shape (like the reference grid); the image is centre-cropped into it. */
private const val CARD_RATIO = 0.7f

internal fun tileAspectRatio(wallpaper: Wallpaper): Float {
    val w = wallpaper.width ?: return 9f / 16f
    val h = wallpaper.height ?: return 9f / 16f
    if (w <= 0 || h <= 0) return 9f / 16f
    return (w.toFloat() / h).coerceIn(0.45f, 1.6f)
}

internal fun parseHexColor(hex: String?): Color? {
    if (hex == null || hex.length != 7 || hex[0] != '#') return null
    return hex.substring(1).toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}
