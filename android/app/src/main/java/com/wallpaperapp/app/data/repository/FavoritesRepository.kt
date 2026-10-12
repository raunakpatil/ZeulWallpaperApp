package com.wallpaperapp.app.data.repository

import com.wallpaperapp.app.data.account.AccountSync
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.wallpaperapp.app.data.local.AppDatabase
import com.wallpaperapp.app.data.local.FavoriteEntity
import com.wallpaperapp.app.data.local.toDomain
import com.wallpaperapp.app.data.local.toEntity
import com.wallpaperapp.app.domain.model.EventType
import com.wallpaperapp.app.domain.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favourites live in Room first, so they work for guests and offline.
 * Signed-in users also get them in their account (`favorites` table) through [AccountSync] (B7).
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val db: AppDatabase,
    private val wallpapers: WallpaperRepository,
    private val accountSync: AccountSync,
) {
    private val dao = db.favoriteDao()

    fun favorites(): Flow<List<Wallpaper>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun pagedFavorites(): Flow<PagingData<Wallpaper>> =
        Pager(PagingConfig(pageSize = 60, enablePlaceholders = false)) { dao.pagingSource() }
            .flow.map { page -> page.map { it.toDomain() } }

    val favoriteIds: Flow<Set<String>> = dao.observeIds().map { it.toSet() }

    fun isFavorite(wallpaperId: String): Flow<Boolean> = dao.observeIsFavorite(wallpaperId)

    /** Returns the new state (true = now a favourite). */
    suspend fun toggle(wallpaper: Wallpaper): Boolean {
        val nowFavorite = db.withTransaction {
            if (dao.isFavorite(wallpaper.id)) {
                dao.delete(wallpaper.id)
                false
            } else {
                db.wallpaperDao().upsert(wallpaper.toEntity())
                dao.insert(FavoriteEntity(wallpaper.id, createdAt = System.currentTimeMillis()))
                true
            }
        }
        // CONTRACTS section 2: report every toggle, guest or not.
        wallpapers.report(wallpaper.id, if (nowFavorite) EventType.FAV else EventType.UNFAV)
        accountSync.onFavoriteChanged(wallpaper.id, nowFavorite) // B7: no-op for guests
        return nowFavorite
    }
}
