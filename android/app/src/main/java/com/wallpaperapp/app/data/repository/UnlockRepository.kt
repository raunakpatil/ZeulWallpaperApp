package com.wallpaperapp.app.data.repository

import com.wallpaperapp.app.data.account.AccountSync
import androidx.room.withTransaction
import com.wallpaperapp.app.data.local.AppDatabase
import com.wallpaperapp.app.data.local.UnlockEntity
import com.wallpaperapp.app.data.local.toEntity
import com.wallpaperapp.app.domain.model.AppConfig
import com.wallpaperapp.app.domain.model.Wallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wallpapers unlocked by watching a rewarded ad. Honors `app_config.unlock_mode`
 * ('permanent' or '24h'). Premium users skip this entirely (checked by the caller, B8).
 */
@Singleton
class UnlockRepository @Inject constructor(
    private val db: AppDatabase,
    private val appConfig: AppConfigRepository,
    private val accountSync: AccountSync,
) {
    private val dao = db.unlockDao()

    fun isUnlocked(wallpaperId: String): Flow<Boolean> =
        combine(dao.observe(wallpaperId), appConfig.config) { unlock, config -> unlock.isValid(config) }

    suspend fun isUnlockedNow(wallpaperId: String): Boolean =
        dao.get(wallpaperId).isValid(appConfig.config.value)

    suspend fun unlock(wallpaper: Wallpaper) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            db.wallpaperDao().upsert(wallpaper.toEntity())
            dao.insert(UnlockEntity(wallpaper.id, unlockedAt = now))
        }
        accountSync.onUnlocked(wallpaper.id, now) // B7: no-op for guests
    }

    private fun UnlockEntity?.isValid(config: AppConfig, now: Long = System.currentTimeMillis()): Boolean =
        this != null && when (config.unlockMode) {
            AppConfig.UnlockMode.PERMANENT -> true
            AppConfig.UnlockMode.HOURS_24 -> now - unlockedAt < TimeUnit.HOURS.toMillis(24)
        }
}
