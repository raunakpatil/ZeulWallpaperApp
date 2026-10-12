package com.wallpaperapp.app.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.filter
import com.wallpaperapp.app.data.local.WallpaperDao
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.local.toDomain
import com.wallpaperapp.app.data.local.toEntity
import com.wallpaperapp.app.data.remote.WallpaperRemoteDataSource
import com.wallpaperapp.app.di.ApplicationScope
import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.domain.model.EventType
import com.wallpaperapp.app.domain.model.PopularRange
import com.wallpaperapp.app.domain.model.SearchFilters
import com.wallpaperapp.app.domain.model.SearchSuggestion
import com.wallpaperapp.app.domain.model.Section
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.WallpaperCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Browsing, search and event reporting. Lists page straight from the network; Coil caches images. */
@Singleton
class WallpaperRepository @Inject constructor(
    private val remote: WallpaperRemoteDataSource,
    private val wallpaperDao: WallpaperDao,
    private val preferences: UserPreferences,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    init {
        // Preview-only snapshots older than 30 days; favourites, unlocks and downloads are kept.
        appScope.launch { wallpaperDao.pruneUnreferenced(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)) }
    }

    fun section(section: Section): Flow<PagingData<Wallpaper>> =
        pager { page, size -> remote.getSection(section, page, size) }

    fun popular(range: PopularRange): Flow<PagingData<Wallpaper>> =
        pager { page, size -> remote.getPopular(range, page, size) }

    /** Onboarding "pick your styles" choices (tag slugs). */
    val seedTags: Flow<Set<String>> = preferences.seedTags

    suspend fun setSeedTags(tags: Set<String>) = preferences.setSeedTags(tags)

    /**
     * Signed-in users get server-side affinity; guests are steered by their onboarding picks
     * (`seed_tags`). Pass [seeds] so a change of picks builds a fresh pager.
     */
    fun forYou(seeds: Set<String>): Flow<PagingData<Wallpaper>> =
        pager { page, size -> remote.getForYou(page, size, seeds.toList().ifEmpty { null }) }

    fun search(query: String?, filters: SearchFilters = SearchFilters()): Flow<PagingData<Wallpaper>> =
        pager { page, size -> remote.search(query, filters, page, size) }

    fun collection(collectionId: String): Flow<PagingData<Wallpaper>> =
        pager { page, size -> remote.getCollection(collectionId, page, size) }

    suspend fun categories(): List<Category> = remote.getCategories()

    suspend fun collections(): List<WallpaperCollection> = remote.getCollections()

    /** The first few popular wallpapers (onboarding's landing photos). */
    suspend fun popularSample(range: PopularRange, size: Int): List<Wallpaper> =
        remote.getPopular(range, page = 0, pageSize = size)

    /**
     * A category's cover image for Explore: the most-downloaded of its first few wallpapers, so the
     * card shows a crowd favourite rather than whatever was uploaded last. Null if the category is empty.
     */
    suspend fun categoryCover(slug: String): Wallpaper? =
        remote.search(null, SearchFilters(category = slug), page = 0, pageSize = 8).maxByOrNull { it.downloadCount }

    /**
     * Most common tags among this week's popular wallpapers. Computed here because there is
     * no trending-search RPC; good enough to seed the empty search screen.
     */
    suspend fun trendingTags(limit: Int = 10, range: PopularRange = PopularRange.WEEK, sample: Int = 60): List<String> =
        remote.getPopular(range, page = 0, pageSize = sample)
            .flatMap { it.tags }
            .filter { it !in NOT_A_STYLE }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }

    suspend fun suggest(query: String): List<SearchSuggestion> =
        if (query.isBlank()) emptyList() else remote.suggest(query)

    /** Network first (fresh counts), falling back to the offline snapshot. */
    suspend fun getWallpaper(id: String): Wallpaper? {
        val fresh = try {
            remote.getWallpaper(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (fresh != null) {
            wallpaperDao.upsert(fresh.toEntity())
            return fresh
        }
        return wallpaperDao.get(id)?.toDomain()
    }

    /** Analytics only, like [report]'s events: failures are logged and dropped. */
    fun recordPushOpen(pushId: String) {
        appScope.launch {
            try {
                remote.recordPushOpen(pushId, preferences.deviceId())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "record_push_open failed", e)
            }
        }
    }

    suspend fun report(wallpaperId: String, reason: String) =
        remote.reportWallpaper(wallpaperId, reason, preferences.deviceId())

    /** Keeps an offline copy of a wallpaper the user is looking at or acting on. */
    suspend fun cache(wallpaper: Wallpaper) = wallpaperDao.upsert(wallpaper.toEntity())

    fun recordView(wallpaperId: String) = report(wallpaperId, EventType.VIEW)
    fun recordApply(wallpaperId: String) = report(wallpaperId, EventType.APPLY)

    /** Analytics only: failures (offline, rate limit) are logged and dropped. */
    internal fun report(wallpaperId: String, type: EventType) {
        appScope.launch {
            try {
                remote.recordEvent(wallpaperId, type, preferences.deviceId())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "record_event($type) failed", e)
            }
        }
    }

    private fun pager(load: suspend (page: Int, pageSize: Int) -> List<Wallpaper>): Flow<PagingData<Wallpaper>> =
        Pager(RpcPagingSource.config) { RpcPagingSource(RpcPagingSource.PAGE_SIZE, load) }.flow
            .map { page ->
                // Rankings can shift between page loads; drop repeats so grid keys stay unique.
                val seen = HashSet<String>()
                page.filter { seen.add(it.id) }
            }

    private companion object {
        const val TAG = "WallpaperRepository"
    }
}

/** Words that show up as tags from photo descriptions but don't describe a style ("through", "light"…). */
internal val NOT_A_STYLE = setOf(
    "a", "an", "the", "and", "or", "of", "in", "on", "at", "to", "with", "by", "for", "from", "into",
    "through", "across", "over", "under", "behind", "between", "near", "above", "below", "around",
    "background", "wallpaper", "wallpapers", "image", "photo", "picture", "hd", "4k", "light", "view",
)
