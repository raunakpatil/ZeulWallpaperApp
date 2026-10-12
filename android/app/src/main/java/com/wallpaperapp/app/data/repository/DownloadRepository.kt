package com.wallpaperapp.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.wallpaperapp.app.data.local.AppDatabase
import com.wallpaperapp.app.data.local.DownloadEntity
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.local.toDomain
import com.wallpaperapp.app.data.local.toEntity
import com.wallpaperapp.app.data.remote.WallpaperRemoteDataSource
import com.wallpaperapp.app.domain.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Download history, which lets users re-apply without another ad. The file saving itself is B5. */
@Singleton
class DownloadRepository @Inject constructor(
    private val db: AppDatabase,
    private val remote: WallpaperRemoteDataSource,
    private val preferences: UserPreferences,
) {
    private val dao = db.downloadDao()

    fun downloads(): Flow<List<Wallpaper>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun isDownloaded(wallpaperId: String): Boolean = dao.isDownloaded(wallpaperId)

    /**
     * Call after the file is saved. Stores it in history and counts it on the server via
     * `increment_download` (never `record_event`). Returns the server count, or null if offline.
     */
    suspend fun recordDownload(wallpaper: Wallpaper, localUri: String? = null): Int? {
        db.withTransaction {
            db.wallpaperDao().upsert(wallpaper.toEntity())
            dao.upsert(DownloadEntity(wallpaper.id, downloadedAt = System.currentTimeMillis(), localUri = localUri))
        }
        return try {
            remote.incrementDownload(wallpaper.id, preferences.deviceId())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DownloadRepository", "increment_download failed", e)
            null
        }
    }
}
