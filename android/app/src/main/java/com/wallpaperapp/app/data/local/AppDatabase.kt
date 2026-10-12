package com.wallpaperapp.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json

@Database(
    entities = [WallpaperEntity::class, FavoriteEntity::class, UnlockEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun unlockDao(): UnlockDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "wallpapers.db"
    }
}

class Converters {
    @TypeConverter
    fun tagsToJson(tags: List<String>): String = Json.encodeToString(tags)

    @TypeConverter
    fun jsonToTags(json: String): List<String> = runCatching { Json.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())
}
