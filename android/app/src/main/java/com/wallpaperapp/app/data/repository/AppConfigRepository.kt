package com.wallpaperapp.app.data.repository

import android.util.Log
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.remote.AppConfigParser
import com.wallpaperapp.app.data.remote.WallpaperRemoteDataSource
import com.wallpaperapp.app.di.ApplicationScope
import com.wallpaperapp.app.domain.model.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Holds the live `app_config`. Starts from the last cached copy, then refreshes from the server. */
@Singleton
class AppConfigRepository @Inject constructor(
    private val remote: WallpaperRemoteDataSource,
    private val preferences: UserPreferences,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        scope.launch {
            preferences.cachedAppConfig()?.let { _config.value = AppConfigParser.parse(it) }
            refresh()
        }
    }

    /** Returns false if the server couldn't be reached; the cached config stays in place. */
    suspend fun refresh(): Boolean = try {
        val values = remote.fetchAppConfig()
        _config.value = AppConfigParser.parse(values)
        preferences.cacheAppConfig(values)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "app_config refresh failed", e)
        false
    }

    private companion object {
        const val TAG = "AppConfigRepository"
    }
}
