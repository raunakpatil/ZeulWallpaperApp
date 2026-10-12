package com.wallpaperapp.app.data.remote

import com.wallpaperapp.app.domain.model.DeviceType
import com.wallpaperapp.app.domain.model.SearchSuggestion
import com.wallpaperapp.app.domain.model.Wallpaper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

/** Wire format of a `wallpapers_public` row. */
@Serializable
data class WallpaperDto(
    val id: String,
    val title: String,
    @SerialName("storage_key") val storageKey: String,
    @SerialName("original_ext") val originalExt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("aspect_ratio") val aspectRatio: Double? = null,
    @SerialName("device_type") val deviceType: String? = null,
    @SerialName("is_amoled") val isAmoled: Boolean = false,
    @SerialName("is_curated") val isCurated: Boolean = false,
    @SerialName("curated_order") val curatedOrder: Int? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("dominant_color") val dominantColor: String? = null,
    @SerialName("color_family") val colorFamily: String? = null,
    val blurhash: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("category_slug") val categorySlug: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("file_size_kb") val fileSizeKb: Int? = null,
    @SerialName("author_name") val authorName: String? = null,
    val license: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("fav_count") val favCount: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    @SerialName("popularity_score") val popularityScore: Double = 0.0,
    val tags: List<String>? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

fun WallpaperDto.toDomain() = Wallpaper(
    id = id,
    title = title,
    storageKey = storageKey,
    originalExt = originalExt ?: "jpg",
    width = width,
    height = height,
    aspectRatio = aspectRatio,
    deviceType = DeviceType.fromKey(deviceType),
    isAmoled = isAmoled,
    isCurated = isCurated,
    curatedOrder = curatedOrder,
    isPremium = isPremium,
    dominantColor = dominantColor,
    colorFamily = colorFamily,
    blurhash = blurhash,
    categoryId = categoryId,
    categorySlug = categorySlug,
    categoryName = categoryName,
    fileSizeKb = fileSizeKb,
    authorName = authorName,
    license = license,
    sourceUrl = sourceUrl,
    downloadCount = downloadCount,
    favCount = favCount,
    viewCount = viewCount,
    popularityScore = popularityScore,
    tags = tags.orEmpty(),
    publishedAt = parseTimestamp(publishedAt),
    createdAt = parseTimestamp(createdAt),
)

/** Postgres timestamptz, e.g. `2026-09-30T10:15:00.123456+00:00`. */
internal fun parseTimestamp(value: String?): Instant? =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

@Serializable
data class SuggestionDto(val label: String, val kind: String)

fun SuggestionDto.toDomain(): SearchSuggestion? {
    val kind = when (kind) {
        "tag" -> SearchSuggestion.Kind.TAG
        "category" -> SearchSuggestion.Kind.CATEGORY
        "title" -> SearchSuggestion.Kind.TITLE
        else -> return null
    }
    return SearchSuggestion(label, kind)
}
