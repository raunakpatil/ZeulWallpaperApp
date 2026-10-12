package com.wallpaperapp.app.data.remote

import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.domain.model.WallpaperCollection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReportDto(
    @SerialName("wallpaper_id") val wallpaperId: String,
    @SerialName("device_id") val deviceId: String,
    val reason: String,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val slug: String,
    val icon: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
)

fun CategoryDto.toDomain() = Category(id, name, slug, icon, sortOrder ?: 0)

/** Row of `get_collections()` (CONTRACTS v1.3). */
@Serializable
data class CollectionDto(
    val id: String,
    val title: String,
    @SerialName("is_premium") val isPremium: Boolean = false,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("cover_storage_key") val coverStorageKey: String? = null,
    @SerialName("cover_blurhash") val coverBlurhash: String? = null,
    @SerialName("cover_dominant_color") val coverDominantColor: String? = null,
)

fun CollectionDto.toDomain() =
    WallpaperCollection(id, title, isPremium, itemCount, coverStorageKey, coverBlurhash, coverDominantColor)
