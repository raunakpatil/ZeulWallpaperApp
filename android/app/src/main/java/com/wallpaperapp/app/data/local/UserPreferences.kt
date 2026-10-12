package com.wallpaperapp.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import com.wallpaperapp.app.domain.model.ApplyTarget
import com.wallpaperapp.app.domain.model.AutoChangerSettings
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Small per-install settings kept in DataStore. */
@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /**
     * Random UUID generated once per install (CONTRACTS section 2). Required by
     * `record_event` / `increment_download`. Never tied to the user account.
     */
    suspend fun deviceId(): String {
        dataStore.data.first()[DEVICE_ID]?.let { return it }
        var id = ""
        dataStore.edit { prefs ->
            id = prefs[DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[DEVICE_ID] = it }
        }
        return id
    }

    /** Onboarding "pick your styles" tag slugs, passed to `get_for_you` as `seed_tags` for guests. */
    val seedTags: Flow<Set<String>> = dataStore.data.map { it[SEED_TAGS].orEmpty() }

    suspend fun setSeedTags(tags: Set<String>) {
        dataStore.edit { it[SEED_TAGS] = tags }
    }

    /** True once onboarding was finished or skipped; it never shows again after that. */
    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_DONE] ?: false }

    suspend fun setOnboardingDone() {
        dataStore.edit { it[ONBOARDING_DONE] = true }
    }

    /** Successful applies/downloads and when we last asked for a Play review (for [ReviewPolicy]). */
    suspend fun recordSuccessForReview(): Pair<Int, Long?> {
        var result = 0 to (null as Long?)
        dataStore.edit {
            val count = (it[REVIEW_SUCCESSES] ?: 0) + 1
            it[REVIEW_SUCCESSES] = count
            result = count to it[REVIEW_ASKED_AT]
        }
        return result
    }

    suspend fun markReviewAsked(now: Long) {
        dataStore.edit { it[REVIEW_ASKED_AT] = now }
    }

    /** Notification settings (CONTRACTS §8): both default on; Android still needs the permission. */
    val pushNewDrops: Flow<Boolean> = dataStore.data.map { it[PUSH_NEW_DROPS] ?: true }
    val pushDaily: Flow<Boolean> = dataStore.data.map { it[PUSH_DAILY] ?: true }

    suspend fun setPushNewDrops(on: Boolean) {
        dataStore.edit { it[PUSH_NEW_DROPS] = on }
    }

    suspend fun setPushDaily(on: Boolean) {
        dataStore.edit { it[PUSH_DAILY] = on }
    }

    /** Last premium status the app saw, for background work that runs before billing answers. */
    suspend fun lastKnownPremium(): Boolean = dataStore.data.first()[LAST_KNOWN_PREMIUM] ?: false

    suspend fun setLastKnownPremium(premium: Boolean) {
        dataStore.edit { it[LAST_KNOWN_PREMIUM] = premium }
    }

    /** Auto wallpaper changer settings (premium perk, B10). */
    val autoChanger: Flow<AutoChangerSettings> = dataStore.data.map { p ->
        AutoChangerSettings(
            enabled = p[AUTO_ENABLED] ?: false,
            intervalHours = p[AUTO_INTERVAL] ?: 24,
            source = p[AUTO_SOURCE] ?: AutoChangerSettings.SOURCE_FAVORITES,
            target = p[AUTO_TARGET]?.let { runCatching { ApplyTarget.valueOf(it) }.getOrNull() } ?: ApplyTarget.HOME,
            lastWallpaperId = p[AUTO_LAST_ID],
        )
    }

    suspend fun updateAutoChanger(transform: (AutoChangerSettings) -> AutoChangerSettings) {
        dataStore.edit { p ->
            val current = AutoChangerSettings(
                enabled = p[AUTO_ENABLED] ?: false,
                intervalHours = p[AUTO_INTERVAL] ?: 24,
                source = p[AUTO_SOURCE] ?: AutoChangerSettings.SOURCE_FAVORITES,
                target = p[AUTO_TARGET]?.let { runCatching { ApplyTarget.valueOf(it) }.getOrNull() } ?: ApplyTarget.HOME,
                lastWallpaperId = p[AUTO_LAST_ID],
            )
            val next = transform(current)
            p[AUTO_ENABLED] = next.enabled
            p[AUTO_INTERVAL] = next.intervalHours
            p[AUTO_SOURCE] = next.source
            p[AUTO_TARGET] = next.target.name
            next.lastWallpaperId?.let { p[AUTO_LAST_ID] = it } ?: p.remove(AUTO_LAST_ID)
        }
    }

    /** Asks for POST_NOTIFICATIONS only once, at a good moment (never on first launch). */
    suspend fun takeNotificationPermissionAsk(): Boolean {
        var first = false
        dataStore.edit {
            first = it[NOTIFICATION_PERMISSION_ASKED] != true
            it[NOTIFICATION_PERMISSION_ASKED] = true
        }
        return first
    }

    /** Most recent first, at most [MAX_RECENT]. */
    val recentSearches: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[RECENT_SEARCHES]?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
    }

    suspend fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[RECENT_SEARCHES]?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()
            val updated = (listOf(q) + current.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_RECENT)
            prefs[RECENT_SEARCHES] = Json.encodeToString(updated)
        }
    }

    suspend fun clearRecentSearches() {
        dataStore.edit { it.remove(RECENT_SEARCHES) }
    }

    /** Last fetched `app_config` rows, so the app starts with the right CDN URL while offline. */
    suspend fun cachedAppConfig(): Map<String, JsonElement>? =
        dataStore.data.first()[APP_CONFIG]?.let { json ->
            runCatching { Json.decodeFromString<Map<String, JsonElement>>(json) }.getOrNull()
        }

    suspend fun cacheAppConfig(values: Map<String, JsonElement>) {
        dataStore.edit { it[APP_CONFIG] = Json.encodeToString(values) }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val SEED_TAGS = stringSetPreferencesKey("seed_tags")
        val APP_CONFIG = stringPreferencesKey("app_config_json")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val REVIEW_SUCCESSES = intPreferencesKey("review_successes")
        val REVIEW_ASKED_AT = longPreferencesKey("review_asked_at")
        val PUSH_NEW_DROPS = booleanPreferencesKey("push_new_drops")
        val PUSH_DAILY = booleanPreferencesKey("push_daily")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        val AUTO_ENABLED = booleanPreferencesKey("auto_changer_enabled")
        val AUTO_INTERVAL = intPreferencesKey("auto_changer_interval_hours")
        val AUTO_SOURCE = stringPreferencesKey("auto_changer_source")
        val AUTO_TARGET = stringPreferencesKey("auto_changer_target")
        val AUTO_LAST_ID = stringPreferencesKey("auto_changer_last_id")
        val LAST_KNOWN_PREMIUM = booleanPreferencesKey("last_known_premium")
        const val MAX_RECENT = 10
    }
}
