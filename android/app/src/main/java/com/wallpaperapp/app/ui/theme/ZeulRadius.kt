package com.wallpaperapp.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Zeul's corner radii (B15): tight, not pill-shaped. Use these instead of one-off values;
 * circles are only for avatars, the hamster badge and colour dots.
 */
object ZeulRadius {
    /** Photo cards, wallpaper tiles, ad slots. */
    val Card = 14.dp
    /** Floating panels, bottom sheets, grouped settings, the nav bar. */
    val Panel = 18.dp
    /** Buttons, text fields, icon buttons, tabs. */
    val Control = 12.dp
    /** Filter chips. */
    val Chip = 10.dp
    /** Small pills and labels over photos, tags. */
    val Small = 8.dp
}

/** Material shapes on the same scale, so sheets, dialogs and menus match. */
val ZeulShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(ZeulRadius.Small),
    medium = RoundedCornerShape(ZeulRadius.Control),
    large = RoundedCornerShape(ZeulRadius.Card),
    extraLarge = RoundedCornerShape(ZeulRadius.Panel),
)
