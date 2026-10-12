package com.wallpaperapp.app.domain.model

import java.time.Instant

/** One row of the `wallpapers_public` view (CONTRACTS section 1). */
data class Wallpaper(
    val id: String,
    val title: String,
    val storageKey: String,
    val originalExt: String,
    val width: Int?,
    val height: Int?,
    val aspectRatio: Double?,
    val deviceType: DeviceType,
    val isAmoled: Boolean,
    val isCurated: Boolean,
    val curatedOrder: Int?,
    val isPremium: Boolean,
    val dominantColor: String?,
    val colorFamily: String?,
    val blurhash: String?,
    val categoryId: String?,
    val categorySlug: String?,
    val categoryName: String?,
    val fileSizeKb: Int?,
    val authorName: String?,
    val license: String?,
    val sourceUrl: String?,
    val downloadCount: Int,
    val favCount: Int,
    val viewCount: Int,
    val popularityScore: Double,
    val tags: List<String>,
    val publishedAt: Instant?,
    val createdAt: Instant?,
)

enum class DeviceType(val key: String) {
    PHONE("phone"), TABLET("tablet"), BOTH("both");

    companion object {
        fun fromKey(key: String?): DeviceType = entries.firstOrNull { it.key == key } ?: PHONE
    }
}

/** CDN file variants (CONTRACTS section 3). `ORIGINAL` is premium only and uses [Wallpaper.originalExt]. */
enum class ImageVariant { THUMB, PREVIEW, FULL, ORIGINAL }
