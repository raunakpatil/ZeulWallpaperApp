package com.wallpaperapp.app.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {
    @Upsert
    suspend fun upsert(wallpaper: WallpaperEntity)

    @Upsert
    suspend fun upsertAll(wallpapers: List<WallpaperEntity>)

    @Query("SELECT * FROM wallpapers WHERE id = :id")
    suspend fun get(id: String): WallpaperEntity?

    /** Drops preview-only snapshots that nothing references any more. */
    @Query(
        """DELETE FROM wallpapers WHERE cachedAt < :olderThan
           AND id NOT IN (SELECT wallpaperId FROM favorites)
           AND id NOT IN (SELECT wallpaperId FROM unlocks)
           AND id NOT IN (SELECT wallpaperId FROM downloads)""",
    )
    suspend fun pruneUnreferenced(olderThan: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT w.* FROM wallpapers w JOIN favorites f ON f.wallpaperId = w.id ORDER BY f.createdAt DESC")
    fun pagingSource(): PagingSource<Int, WallpaperEntity>

    @Query("SELECT w.* FROM wallpapers w JOIN favorites f ON f.wallpaperId = w.id ORDER BY f.createdAt DESC")
    fun observeAll(): Flow<List<WallpaperEntity>>

    @Query("SELECT wallpaperId FROM favorites")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE wallpaperId = :id)")
    fun observeIsFavorite(id: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE wallpaperId = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE wallpaperId = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM favorites WHERE synced = 0")
    suspend fun unsynced(): List<FavoriteEntity>

    // B7 account sync
    @Query("SELECT wallpaperId FROM favorites")
    suspend fun allIds(): List<String>

    @Query("UPDATE favorites SET synced = 1 WHERE wallpaperId IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM favorites WHERE wallpaperId IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()
}

@Dao
interface UnlockDao {
    @Query("SELECT * FROM unlocks WHERE wallpaperId = :id")
    fun observe(id: String): Flow<UnlockEntity?>

    @Query("SELECT * FROM unlocks WHERE wallpaperId = :id")
    suspend fun get(id: String): UnlockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(unlock: UnlockEntity)

    // B7 account sync
    @Query("SELECT * FROM unlocks WHERE synced = 0")
    suspend fun unsynced(): List<UnlockEntity>

    @Query("SELECT wallpaperId FROM unlocks")
    suspend fun allIds(): List<String>

    @Query("UPDATE unlocks SET synced = 1 WHERE wallpaperId IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT * FROM unlocks")
    suspend fun all(): List<UnlockEntity>

    @Query("UPDATE unlocks SET synced = 1 WHERE wallpaperId = :id AND unlockedAt <= :at")
    suspend fun markSyncedIfNotNewer(id: String, at: Long)

    @Query("DELETE FROM unlocks")
    suspend fun deleteAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT w.* FROM wallpapers w JOIN downloads d ON d.wallpaperId = w.id ORDER BY d.downloadedAt DESC")
    fun observeAll(): Flow<List<WallpaperEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE wallpaperId = :id)")
    suspend fun isDownloaded(id: String): Boolean

    @Upsert
    suspend fun upsert(download: DownloadEntity)
}
