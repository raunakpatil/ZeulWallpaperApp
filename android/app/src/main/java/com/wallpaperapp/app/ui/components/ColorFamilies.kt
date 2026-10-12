package com.wallpaperapp.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.wallpaperapp.app.R

/** Swatch colours for the `search_wallpapers` colour families (CONTRACTS section 2). */
val ColorFamilySwatches: List<Pair<String, Color>> = listOf(
    "red" to Color(0xFFE53935),
    "orange" to Color(0xFFFB8C00),
    "yellow" to Color(0xFFFDD835),
    "green" to Color(0xFF43A047),
    "teal" to Color(0xFF00897B),
    "blue" to Color(0xFF1E88E5),
    "purple" to Color(0xFF8E24AA),
    "pink" to Color(0xFFD81B60),
    "brown" to Color(0xFF6D4C41),
    "black" to Color(0xFF000000),
    "white" to Color(0xFFFFFFFF),
    "gray" to Color(0xFF9E9E9E),
)

private val COLOR_LABELS = mapOf(
    "red" to R.string.color_red, "orange" to R.string.color_orange, "yellow" to R.string.color_yellow,
    "green" to R.string.color_green, "teal" to R.string.color_teal, "blue" to R.string.color_blue,
    "purple" to R.string.color_purple, "pink" to R.string.color_pink, "brown" to R.string.color_brown,
    "black" to R.string.color_black, "white" to R.string.color_white, "gray" to R.string.color_gray,
)

/** Display name of a colour family (falls back to the key for anything new from the server). */
@Composable
fun colorFamilyLabel(key: String): String =
    COLOR_LABELS[key]?.let { stringResource(it) } ?: key.replaceFirstChar(Char::uppercase)
