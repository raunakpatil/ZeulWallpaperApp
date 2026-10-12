package com.wallpaperapp.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Thin-line icon set for the soft UI (24×24, 1.7 stroke, round caps). Drawn for Zeul. */
object ZeulIcons {
    private fun line(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.7f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home = line("ZeulHome", "M3.5 10.6L12 3.6l8.5 7v8.9a1 1 0 0 1-1 1H15v-6h-6v6H4.5a1 1 0 0 1-1-1z")
    val Grid = line(
        "ZeulGrid",
        "M5 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
        "M15 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
        "M5 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z",
        "M15 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1-1 1h-4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1z",
    )
    val Search = line("ZeulSearch", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0", "M16.2 16.2L20.5 20.5")
    private const val HEART = "M12 20s-8-4.9-8-11a4.4 4.4 0 0 1 8-2.5A4.4 4.4 0 0 1 20 9c0 6.1-8 11-8 11z"
    val Heart = line("ZeulHeart", HEART)
    val HeartFilled = line("ZeulHeartFilled", HEART, filled = true)
    val Person = line("ZeulPerson", "M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0", "M4.5 20.5c0-3.9 3.4-6.5 7.5-6.5s7.5 2.6 7.5 6.5")
    val Download = line("ZeulDownload", "M12 4v11", "M7.5 10.5L12 15l4.5-4.5", "M5 19.5h14")
    val Back = line("ZeulBack", "M14.5 5.5L8 12l6.5 6.5")
    val Close = line("ZeulClose", "M6 6l12 12", "M18 6L6 18")
    val Share = line("ZeulShare", "M12 15V4", "M8 7.5L12 3.5l4 4", "M5.5 12v6.5a1.5 1.5 0 0 0 1.5 1.5h10a1.5 1.5 0 0 0 1.5-1.5V12")
    val Lock = line("ZeulLock", "M6.5 11h11a1 1 0 0 1 1 1v7.5a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1V12a1 1 0 0 1 1-1z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3")
    val Info = line("ZeulInfo", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0-17 0", "M12 11v5.5", "M12 7.8v.2")
    val Check = line("ZeulCheck", "M5 12.5l4.5 4.5L19 7.5")
    val Sparkle = line("ZeulSparkle", "M12 3.5c.7 4.4 2.6 6.4 7 7.5c-4.4 1.1-6.3 3.1-7 7.5c-.7-4.4-2.6-6.4-7-7.5c4.4-1.1 6.3-3.1 7-7.5z", "M19 3.5v3", "M17.5 5h3")
    val Play = line("ZeulPlay", "M8 5.5v13l10.5-6.5z")
    val Reset = line("ZeulReset", "M4.5 12a7.5 7.5 0 1 0 2.2-5.3", "M4.5 4.5v3.8h3.8")
    val ChevronDown = line("ZeulChevronDown", "M7 10l5 5l5-5")
    val Upload = line("ZeulUpload", "M12 15.5V4.5", "M7.5 9L12 4.5 16.5 9", "M5 19.5h14")
    val Mail = line("ZeulMail", "M4.5 6h15a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z", "M4 7l8 6l8-6")
    val Gem = line("ZeulGem", "M7 4h10l4 5.5L12 20 3 9.5z", "M3 9.5h18", "M12 20L8.6 9.5 10 4", "M12 20l3.4-10.5L14 4")
    val ArrowUpRight = line("ZeulArrowUpRight", "M7 17L17 7", "M8.5 7H17v8.5")
    val ArrowRight = line("ZeulArrowRight", "M4.5 12h15", "M13.5 6l6 6-6 6")
    val Flame = line("ZeulFlame", "M12 21c-3.9 0-6.5-2.6-6.5-6.2c0-3.6 2.8-5.6 4-9.3c2.6 1.6 3.4 3.7 3.2 5.6c1-.6 1.7-1.6 1.9-2.9c1.9 1.6 3.9 3.9 3.9 6.6C18.5 18.4 15.9 21 12 21z")
    val Star = line("ZeulStar", "M12 3.8l2.5 5.2 5.7.8-4.1 4 1 5.6L12 16.7l-5.1 2.7 1-5.6-4.1-4 5.7-.8z")
    val Tablet = line("ZeulTablet", "M6.5 3.5h11a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19V5a1.5 1.5 0 0 1 1.5-1.5z", "M11 17.5h2")
    val Moon = line("ZeulMoon", "M19.5 14.6A8 8 0 1 1 9.4 4.5a6.4 6.4 0 0 0 10.1 10.1z")
    val Shuffle = line(
        "ZeulShuffle",
        "M4 7h3.2c2 0 3.2 1 4.3 2.6l2.6 4.1c1.1 1.6 2.3 2.3 4.3 2.3H20", "M17.5 13.5L20 16l-2.5 2.5",
        "M4 17h3.2c1.4 0 2.4-.4 3.2-1.2", "M13.6 8.2c.8-.8 1.8-1.2 3.2-1.2H20", "M17.5 4.5L20 7l-2.5 2.5",
    )
}
