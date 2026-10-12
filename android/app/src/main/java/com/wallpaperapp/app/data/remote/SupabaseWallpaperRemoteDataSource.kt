package com.wallpaperapp.app.data.remote

import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.domain.model.EventType
import com.wallpaperapp.app.domain.model.PopularRange
import com.wallpaperapp.app.domain.model.SearchFilters
import com.wallpaperapp.app.domain.model.SearchSuggestion
import com.wallpaperapp.app.domain.model.Section
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.WallpaperCollection
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseWallpaperRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) : WallpaperRemoteDataSource {

    override suspend fun getSection(section: Section, page: Int, pageSize: Int) =
        rpcWallpapers("get_section", params {
            put("section", section.key)
            paging(page, pageSize)
        })

    override suspend fun getPopular(range: PopularRange, page: Int, pageSize: Int) =
        rpcWallpapers("get_popular", params {
            put("range", range.key)
            paging(page, pageSize)
        })

    override suspend fun search(query: String?, filters: SearchFilters, page: Int, pageSize: Int) =
        rpcWallpapers("search_wallpapers", params {
            // Omitted args use the SQL defaults (null = no filter).
            query?.takeIf { it.isNotBlank() }?.let { put("q", it) }
            filters.color?.let { put("color", it) }
            filters.device?.let { put("device", it) }
            filters.amoled?.let { put("amoled", it) }
            filters.category?.let { put("category", it) }
            paging(page, pageSize)
        })

    override suspend fun suggest(query: String): List<SearchSuggestion> =
        supabase.postgrest.rpc("search_suggest", params { put("q", query) })
            .decodeList<SuggestionDto>()
            .mapNotNull { it.toDomain() }

    override suspend fun getForYou(page: Int, pageSize: Int, seedTags: List<String>?) =
        rpcWallpapers("get_for_you", params {
            paging(page, pageSize)
            if (!seedTags.isNullOrEmpty()) {
                put("seed_tags", buildJsonArray { seedTags.forEach { add(JsonPrimitive(it)) } })
            }
        })

    override suspend fun getWallpaper(id: String): Wallpaper? =
        supabase.from("wallpapers_public")
            .select { filter { eq("id", id) } }
            .decodeList<WallpaperDto>()
            .firstOrNull()
            ?.toDomain()

    override suspend fun recordEvent(wallpaperId: String, type: EventType, deviceId: String) {
        supabase.postgrest.rpc("record_event", params {
            put("wallpaper_id", wallpaperId)
            put("type", type.key)
            put("device_id", deviceId)
        })
    }

    override suspend fun incrementDownload(wallpaperId: String, deviceId: String): Int =
        supabase.postgrest.rpc("increment_download", params {
            put("wallpaper_id", wallpaperId)
            put("device_id", deviceId)
        }).decodeAs<Int>()

    override suspend fun fetchAppConfig(): Map<String, JsonElement> =
        supabase.from("app_config").select().decodeList<AppConfigRow>().associate { it.key to it.value }

    override suspend fun getCategories(): List<Category> =
        supabase.from("categories")
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList<CategoryDto>()
            .map { it.toDomain() }

    // Collections go through RPCs, not the tables: they hide drafts and pick covers (CONTRACTS v1.3).
    override suspend fun getCollections(): List<WallpaperCollection> =
        supabase.postgrest.rpc("get_collections").decodeList<CollectionDto>().map { it.toDomain() }

    override suspend fun getCollection(collectionId: String, page: Int, pageSize: Int) =
        rpcWallpapers("get_collection", params {
            put("collection_id", collectionId)
            paging(page, pageSize)
        })

    override suspend fun reportWallpaper(wallpaperId: String, reason: String, deviceId: String) {
        // Insert only: anon users can't read reports back, so ask for no returned row.
        supabase.from("reports").insert(ReportDto(wallpaperId, deviceId, reason))
    }

    override suspend fun recordPushOpen(pushId: String, deviceId: String) {
        supabase.postgrest.rpc("record_push_open", params {
            put("push_id", pushId)
            put("device_id", deviceId)
        })
    }

    private suspend fun rpcWallpapers(function: String, parameters: JsonObject): List<Wallpaper> =
        supabase.postgrest.rpc(function, parameters).decodeList<WallpaperDto>().map { it.toDomain() }

    private inline fun params(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

    private fun JsonObjectBuilder.paging(page: Int, pageSize: Int) {
        put("page", page)
        put("page_size", pageSize)
    }
}
