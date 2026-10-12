package com.wallpaperapp.app.domain.model

import kotlin.random.Random

/** Auto wallpaper changer settings (premium perk). */
data class AutoChangerSettings(
    val enabled: Boolean = false,
    val intervalHours: Int = 24,
    /** [SOURCE_FAVORITES], [SOURCE_FOR_YOU], or `category:{slug}`. */
    val source: String = SOURCE_FAVORITES,
    val target: ApplyTarget = ApplyTarget.HOME,
    /** Last one set, so the next run picks something different. */
    val lastWallpaperId: String? = null,
) {
    val categorySlug: String? get() = source.removePrefix(CATEGORY_PREFIX).takeIf { source.startsWith(CATEGORY_PREFIX) }

    companion object {
        const val SOURCE_FAVORITES = "favorites"
        const val SOURCE_FOR_YOU = "for_you"
        const val CATEGORY_PREFIX = "category:"
        val INTERVALS = listOf(6, 12, 24)

        fun categorySource(slug: String) = "$CATEGORY_PREFIX$slug"
    }
}

object AutoChangerPicker {
    /**
     * A random wallpaper that isn't the current one (unless it's the only choice). Premium-only
     * wallpapers are fine: only premium users can run the changer.
     */
    fun pick(candidates: List<Wallpaper>, lastId: String?, random: Random = Random.Default): Wallpaper? {
        val usable = candidates.filter { it.deviceType != DeviceType.TABLET }.ifEmpty { candidates }
        val fresh = usable.filter { it.id != lastId }.ifEmpty { usable }
        return fresh.randomOrNull(random)
    }
}
