package com.wallpaperapp.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How photo cards (grid tiles, Explore cards) sit on the page (B17). GALLERY is the shipped look
 * (Ghanshyam's pick, 2026-10-06); the others stay for comparison: debug builds switch live with
 * `adb shell am start -n com.zeul.wallpapers/com.wallpaperapp.app.MainActivity -e card_style FLAT`.
 */
enum class CardStyle(
    /** Corner radius of the photo itself. */
    val corner: Dp,
    /** Gap between grid tiles and the grid's side padding. */
    val gap: Dp,
    val sidePadding: Dp,
) {
    /** Raised soft frame with a shadow (B14–B16, before Gallery). */
    SOFT(ZeulRadius.Card, 14.dp, 20.dp),
    /** Just the photo: no frame or shadow, a plain heart. */
    FLAT(10.dp, 10.dp, 16.dp),
    /** Gallery wall: tight gaps, near-square corners, edge to edge. The default. */
    GALLERY(4.dp, 4.dp, 4.dp),
    /** Photo with its title and category underneath. */
    CAPTION(12.dp, 12.dp, 16.dp),
    /** Framed print: a thin paper mat around the photo. */
    MAT(2.dp, 12.dp, 16.dp),
}

val LocalCardStyle = staticCompositionLocalOf { CardStyle.GALLERY }
