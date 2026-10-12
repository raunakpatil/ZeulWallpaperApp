package com.wallpaperapp.app.data.remote

import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.domain.model.EventType
import com.wallpaperapp.app.domain.model.PopularRange
import com.wallpaperapp.app.domain.model.SearchFilters
import com.wallpaperapp.app.domain.model.SearchSuggestion
import com.wallpaperapp.app.domain.model.Section
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.WallpaperCollection
import kotlinx.serialization.json.JsonElement

/**
 * The backend as the app sees it: one method per RPC in CONTRACTS section 2 plus two reads.
 * Pages are 0-based. [SupabaseWallpaperRemoteDataSource] is the real one;
 * [FakeWallpaperRemoteDataSource] is used when no Supabase URL is configured.
 */
interface WallpaperRemoteDataSource {
    suspend fun getSection(section: Section, page: Int, pageSize: Int): List<Wallpaper>
    suspend fun getPopular(range: PopularRange, page: Int, pageSize: Int): List<Wallpaper>
    suspend fun search(query: String?, filters: SearchFilters, page: Int, pageSize: Int): List<Wallpaper>
    suspend fun suggest(query: String): List<SearchSuggestion>
    suspend fun getForYou(page: Int, pageSize: Int, seedTags: List<String>?): List<Wallpaper>
    suspend fun getWallpaper(id: String): Wallpaper?
    suspend fun recordEvent(wallpaperId: String, type: EventType, deviceId: String)
    /** Returns the current download count. */
    suspend fun incrementDownload(wallpaperId: String, deviceId: String): Int
    suspend fun fetchAppConfig(): Map<String, JsonElement>
    suspend fun getCategories(): List<Category>
    suspend fun getCollections(): List<WallpaperCollection>
    suspend fun getCollection(collectionId: String, page: Int, pageSize: Int): List<Wallpaper>
    /** Files a row in `reports` (RLS: anyone may insert an 'open' report). */
    suspend fun reportWallpaper(wallpaperId: String, reason: String, deviceId: String)
    /** Counts a notification tap (deduplicated per device server-side). */
    suspend fun recordPushOpen(pushId: String, deviceId: String)
}
