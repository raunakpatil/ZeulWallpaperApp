package com.wallpaperapp.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wallpaperapp.app.domain.model.DeviceType
import com.wallpaperapp.app.domain.model.Wallpaper
import java.time.Instant

/**
 * Snapshot of a wallpaper the user interacted with (favourite, unlock, download, preview),
 * so those screens keep working offline.
 */
@Entity(tableName = "wallpapers")
data class WallpaperEntity(
    @PrimaryKey val id: String,
    val title: String,
    val storageKey: String,
    val originalExt: String,
    val width: Int?,
    val height: Int?,
    val aspectRatio: Double?,
    val deviceType: String,
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
    val publishedAt: Long?,
    val createdAt: Long?,
    /** When this snapshot was last refreshed from the server. */
    val cachedAt: Long,
)

@Entity(
    tableName = "favorites",
    foreignKeys = [ForeignKey(WallpaperEntity::class, ["id"], ["wallpaperId"], onDelete = ForeignKey.CASCADE)],
)
data class FavoriteEntity(
    @PrimaryKey val wallpaperId: String,
    val createdAt: Long,
    /** False until pushed to the signed-in account's `favorites` table (B7). */
    val synced: Boolean = false,
)

@Entity(
    tableName = "unlocks",
    foreignKeys = [ForeignKey(WallpaperEntity::class, ["id"], ["wallpaperId"], onDelete = ForeignKey.CASCADE)],
)
data class UnlockEntity(
    @PrimaryKey val wallpaperId: String,
    val unlockedAt: Long,
    val synced: Boolean = false,
)

@Entity(
    tableName = "downloads",
    foreignKeys = [ForeignKey(WallpaperEntity::class, ["id"], ["wallpaperId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("downloadedAt")],
)
data class DownloadEntity(
    @PrimaryKey val wallpaperId: String,
    val downloadedAt: Long,
    /** MediaStore uri of the saved file, filled in by B5. */
    val localUri: String? = null,
)

fun WallpaperEntity.toDomain() = Wallpaper(
    id = id, title = title, storageKey = storageKey, originalExt = originalExt,
    width = width, height = height, aspectRatio = aspectRatio,
    deviceType = DeviceType.fromKey(deviceType),
    isAmoled = isAmoled, isCurated = isCurated, curatedOrder = curatedOrder, isPremium = isPremium,
    dominantColor = dominantColor, colorFamily = colorFamily, blurhash = blurhash,
    categoryId = categoryId, categorySlug = categorySlug, categoryName = categoryName,
    fileSizeKb = fileSizeKb, authorName = authorName, license = license, sourceUrl = sourceUrl,
    downloadCount = downloadCount, favCount = favCount, viewCount = viewCount,
    popularityScore = popularityScore, tags = tags,
    publishedAt = publishedAt?.let(Instant::ofEpochMilli),
    createdAt = createdAt?.let(Instant::ofEpochMilli),
)

fun Wallpaper.toEntity(cachedAt: Long = System.currentTimeMillis()) = WallpaperEntity(
    id = id, title = title, storageKey = storageKey, originalExt = originalExt,
    width = width, height = height, aspectRatio = aspectRatio,
    deviceType = deviceType.key,
    isAmoled = isAmoled, isCurated = isCurated, curatedOrder = curatedOrder, isPremium = isPremium,
    dominantColor = dominantColor, colorFamily = colorFamily, blurhash = blurhash,
    categoryId = categoryId, categorySlug = categorySlug, categoryName = categoryName,
    fileSizeKb = fileSizeKb, authorName = authorName, license = license, sourceUrl = sourceUrl,
    downloadCount = downloadCount, favCount = favCount, viewCount = viewCount,
    popularityScore = popularityScore, tags = tags,
    publishedAt = publishedAt?.toEpochMilli(),
    createdAt = createdAt?.toEpochMilli(),
    cachedAt = cachedAt,
)
