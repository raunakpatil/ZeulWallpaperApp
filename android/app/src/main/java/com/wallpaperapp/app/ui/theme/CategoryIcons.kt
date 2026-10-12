package com.wallpaperapp.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.wallpaperapp.app.domain.model.Category

/**
 * Thin-line category icons in the ZeulIcons style. Picked by category slug, then by the dashboard's
 * icon name (Material Symbols names like "forest" or "rocket"); anything unknown gets [Picture].
 */
object CategoryIcons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun line(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.7f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Minimal = line("CatMinimal", circle(12f, 10f, 3f), "M4.5 16.5h15")
    val Nature = line("CatNature", "M5 19c0-8.2 5.4-14 14-14c0 8.6-5.8 14-14 14z", "M5 19l8.5-8.5")
    val Space = line("CatSpace", circle(12f, 12f, 5f), "M4.2 16.3c-1.4-1.9 2.7-5.6 8.4-7.8s10.1-2.5 10.6-.7")
    val Abstract = line("CatAbstract", "M3.5 19.5l6-11 6 11z", circle(16.5f, 8f, 4f))
    val Dark = line("CatDark", "M19.5 14.6A8 8 0 1 1 9.4 4.5a6.4 6.4 0 0 0 10.1 10.1z")
    val Gradient = line(
        "CatGradient",
        "M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
        "M4 12.5L12.5 4", "M4 20L20 4", "M11.5 20L20 11.5",
    )
    val Architecture = line("CatArchitecture", "M3 9l9-5 9 5", "M4 9.5h16", "M6.5 9.5v8", "M10.5 9.5v8", "M13.5 9.5v8", "M17.5 9.5v8", "M3 20h18")
    val Cityscape = line("CatCityscape", "M3 20h18", "M4.5 20v-8.5h4.5V20", "M9 20V5h6v15", "M15 20v-6.5h4.5V20", "M11.3 8.5h1.4", "M11.3 12h1.4")
    val Cars = line(
        "CatCars",
        "M3 15.5v-3l2.3-4.5h9.4l3.8 4.5H21v3",
        "M3 15.5h1.7", "M10 15.5h4", "M19.3 15.5H21",
        circle(7.3f, 15.8f, 2f), circle(16.7f, 15.8f, 2f),
    )
    val Animals = line(
        "CatAnimals",
        "M8 16.6c0-2.6 1.8-4.6 4-4.6s4 2 4 4.6c0 1.6-1.4 2.4-4 2.4s-4-.8-4-2.4z",
        circle(6.2f, 11.6f, 1.6f), circle(9.6f, 7.6f, 1.6f), circle(14.4f, 7.6f, 1.6f), circle(17.8f, 11.6f, 1.6f),
    )
    val Art = line(
        "CatArt",
        "M12 3.5a8.5 8.5 0 0 0 0 17c1.4 0 2-.9 2-1.8c0-1.4-1-1.7-1-3c0-1 .8-1.7 1.8-1.7h2.2a3.5 3.5 0 0 0 3.5-3.5C20.5 6.6 16.7 3.5 12 3.5z",
        circle(8f, 10.5f, 1f), circle(10.8f, 7f, 1f), circle(15f, 7.4f, 1f),
    )
    val Patterns = line("CatPatterns", "M3 8c2-2 4-2 6 0s4 2 6 0s4-2 6 0", "M3 12.5c2-2 4-2 6 0s4 2 6 0s4-2 6 0", "M3 17c2-2 4-2 6 0s4 2 6 0s4-2 6 0")
    val Picture = line(
        "CatPicture",
        "M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
        "M4 16.5l5-5 4 4 2.5-2.5 4.5 4.5", circle(15.5f, 8.5f, 1.5f),
    )

    private val bySlug = mapOf(
        "minimal" to Minimal, "nature" to Nature, "space" to Space, "abstract" to Abstract, "dark" to Dark,
        "gradient" to Gradient, "architecture" to Architecture, "cityscape" to Cityscape, "city" to Cityscape,
        "cars" to Cars, "animals" to Animals, "art" to Art, "patterns" to Patterns,
    )

    /** Dashboard icon names (Material Symbols) people are likely to type. */
    private val byIconName = mapOf(
        "forest" to Nature, "park" to Nature, "eco" to Nature, "nature" to Nature, "landscape" to Nature,
        "rocket" to Space, "rocket_launch" to Space, "public" to Space, "planet" to Space, "nights_stay" to Dark,
        "dark_mode" to Dark, "bedtime" to Dark, "gradient" to Gradient, "category" to Abstract, "interests" to Abstract,
        "account_balance" to Architecture, "apartment" to Cityscape, "location_city" to Cityscape, "directions_car" to Cars,
        "pets" to Animals, "palette" to Art, "brush" to Art, "texture" to Patterns, "pattern" to Patterns,
        "crop_square" to Minimal, "filter_center_focus" to Minimal,
    )

    fun forCategory(category: Category): ImageVector =
        bySlug[category.slug.lowercase()] ?: category.icon?.lowercase()?.let(byIconName::get) ?: Picture
}
