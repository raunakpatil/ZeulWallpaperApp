package com.wallpaperapp.app.data.remote

import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.domain.model.DeviceType
import com.wallpaperapp.app.domain.model.EventType
import com.wallpaperapp.app.domain.model.PopularRange
import com.wallpaperapp.app.domain.model.SearchFilters
import com.wallpaperapp.app.domain.model.SearchSuggestion
import com.wallpaperapp.app.domain.model.Section
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.WallpaperCollection
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * In-memory backend used until the Supabase keys are in local.properties and A6 has seeded
 * real wallpapers. Mirrors the RPC rules in CONTRACTS section 2 closely enough for UI work.
 * Items reuse real CDN uploads (round-robin) so images load; the metadata around them is made up.
 */
@Singleton
class FakeWallpaperRemoteDataSource @Inject constructor() : WallpaperRemoteDataSource {

    /** Artificial latency so loading states are visible. Tests set it to 0. */
    var latencyMs: Long = 400

    val wallpapers: List<Wallpaper> = generate(count = 120)
    private val downloads = mutableMapOf<String, Int>()

    override suspend fun getSection(section: Section, page: Int, pageSize: Int): List<Wallpaper> {
        val phone = wallpapers.filter { it.deviceType != DeviceType.TABLET }
        val list = when (section) {
            Section.CURATED -> phone.filter { it.isCurated }.sortedBy { it.curatedOrder }
            Section.NEW -> phone.sortedByDescending { it.publishedAt }
            Section.AMOLED -> phone.filter { it.isAmoled }.sortedByDescending { it.publishedAt }
            Section.TABLET -> wallpapers.filter { it.deviceType != DeviceType.PHONE }.sortedByDescending { it.publishedAt }
            Section.PREMIUM -> phone.filter { it.isPremium }.sortedByDescending { it.publishedAt }
        }
        return list.page(page, pageSize)
    }

    override suspend fun getPopular(range: PopularRange, page: Int, pageSize: Int): List<Wallpaper> {
        val window = when (range) {
            PopularRange.WEEK -> 7L
            PopularRange.MONTH -> 30L
            PopularRange.ALL -> null
        }
        val cutoff = window?.let { Instant.now().minus(it, ChronoUnit.DAYS) }
        return wallpapers
            .filter { it.deviceType != DeviceType.TABLET }
            .filter { cutoff == null || (it.publishedAt ?: Instant.EPOCH) >= cutoff }
            .sortedByDescending { it.popularityScore }
            .page(page, pageSize)
    }

    override suspend fun search(query: String?, filters: SearchFilters, page: Int, pageSize: Int): List<Wallpaper> {
        val q = query?.trim()?.lowercase().orEmpty()
        return wallpapers.filter { w ->
            (q.isEmpty() || w.title.lowercase().contains(q) || w.tags.any { it.contains(q) } ||
                w.categorySlug?.contains(q) == true) &&
                (filters.color == null || w.colorFamily == filters.color) &&
                (filters.amoled == null || w.isAmoled == filters.amoled) &&
                (filters.category == null || w.categorySlug == filters.category) &&
                when (filters.device) {
                    "phone" -> w.deviceType != DeviceType.TABLET
                    "tablet" -> w.deviceType != DeviceType.PHONE
                    else -> true
                }
        }.sortedByDescending { it.popularityScore }.page(page, pageSize)
    }

    override suspend fun suggest(query: String): List<SearchSuggestion> {
        simulateLatency()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val tags = TAGS.filter { it.startsWith(q) }.map { SearchSuggestion(it, SearchSuggestion.Kind.TAG) }
        val categories = CATEGORIES.filter { it.startsWith(q) }
            .map { SearchSuggestion(it.replaceFirstChar(Char::uppercase), SearchSuggestion.Kind.CATEGORY) }
        val titles = wallpapers.map { it.title }.distinct().filter { it.lowercase().startsWith(q) }
            .map { SearchSuggestion(it, SearchSuggestion.Kind.TITLE) }
        return (tags + categories + titles).take(8)
    }

    override suspend fun getForYou(page: Int, pageSize: Int, seedTags: List<String>?): List<Wallpaper> {
        if (seedTags.isNullOrEmpty()) return getPopular(PopularRange.WEEK, page, pageSize)
        return wallpapers
            .filter { it.deviceType != DeviceType.TABLET }
            .sortedWith(
                compareByDescending<Wallpaper> { w -> w.tags.count { it in seedTags } }
                    .thenByDescending { it.popularityScore },
            )
            .page(page, pageSize)
    }

    override suspend fun getWallpaper(id: String): Wallpaper? {
        simulateLatency()
        return wallpapers.firstOrNull { it.id == id }
    }

    override suspend fun recordEvent(wallpaperId: String, type: EventType, deviceId: String) = Unit

    override suspend fun incrementDownload(wallpaperId: String, deviceId: String): Int {
        val base = wallpapers.firstOrNull { it.id == wallpaperId }?.downloadCount
            ?: throw NoSuchElementException("Unknown wallpaper $wallpaperId") // real RPC raises P0002
        val extra = (downloads[wallpaperId] ?: 0) + 1
        downloads[wallpaperId] = extra
        return base + extra
    }

    /** What `app_config` returns; tests can replace it. */
    var appConfigValues: Map<String, JsonElement> =
        mapOf("cdn_base_url" to JsonPrimitive("https://raunakpatil.github.io/wallpaper-cdn"))

    override suspend fun fetchAppConfig(): Map<String, JsonElement> = appConfigValues

    override suspend fun getCategories(): List<Category> {
        simulateLatency()
        return CATEGORIES.mapIndexed { i, slug ->
            Category("00000000-0000-4000-9000-%012d".format(i), slug.replaceFirstChar(Char::uppercase), slug, null, (i + 1) * 10)
        }
    }

    /** One fake pack per tag group, covered by its first item, like the real RPC. */
    private val collections: Map<WallpaperCollection, List<Wallpaper>> by lazy {
        listOf("neon" to "Neon Nights", "ocean" to "Ocean Calm", "stars" to "Starfield", "minimal" to "Less Is More")
            .mapIndexed { i, (tag, title) ->
                val items = wallpapers.filter { tag in it.tags }.take(12)
                val cover = items.first()
                WallpaperCollection(
                    id = "00000000-0000-4000-a000-%012d".format(i), title = title, isPremium = i == 2,
                    itemCount = items.size, coverStorageKey = cover.storageKey,
                    coverBlurhash = cover.blurhash, coverDominantColor = cover.dominantColor,
                ) to items
            }.toMap()
    }

    override suspend fun getCollections(): List<WallpaperCollection> {
        simulateLatency()
        return collections.keys.toList()
    }

    override suspend fun getCollection(collectionId: String, page: Int, pageSize: Int): List<Wallpaper> =
        collections.entries.firstOrNull { it.key.id == collectionId }?.value.orEmpty().page(page, pageSize)

    val reports = mutableListOf<Pair<String, String>>()
    val pushOpens = mutableListOf<String>()

    override suspend fun recordPushOpen(pushId: String, deviceId: String) {
        pushOpens += pushId
    }

    override suspend fun reportWallpaper(wallpaperId: String, reason: String, deviceId: String) {
        simulateLatency()
        reports += wallpaperId to reason
    }

    private suspend fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
        simulateLatency()
        return drop(page * pageSize).take(pageSize)
    }

    private suspend fun simulateLatency() {
        if (latencyMs > 0) delay(latencyMs)
    }

    private companion object {
        val CATEGORIES = listOf(
            "minimal", "nature", "space", "abstract", "dark", "gradient",
            "architecture", "cityscape", "cars", "animals", "art", "patterns",
        )
        val TAGS = listOf(
            "mountains", "ocean", "forest", "neon", "sunset", "stars", "galaxy", "city", "rain",
            "minimal", "geometric", "pastel", "dark", "blue", "purple", "flowers", "snow", "desert",
        )
        val ADJECTIVES = listOf("Silent", "Neon", "Golden", "Frozen", "Midnight", "Velvet", "Crimson", "Misty", "Cosmic", "Quiet")
        val NOUNS = listOf("Peaks", "Waves", "Skyline", "Forest", "Nebula", "Dunes", "Lines", "Bloom", "Horizon", "Grid")

        /** Real CDN folders (thumb/preview/full; most have no original). Dev data only. */
        val REAL_UPLOADS = listOf(
            "w/ce50ce5481", "w/269093e6e6", "w/f3e3590117", "w/d46c41df91", "w/f4c72fcfa8", "w/32eda5e5f4",
            "w/166b540f20", "w/1811c504d5", "w/226d75e0fb", "w/3952d61265", "w/3dde02eb59", "w/45ebc3c055",
        )

        // Valid blurhashes so placeholder rendering can be built before real data exists.
        val BLURHASHES = listOf(
            "LEHV6nWB2yk8pyo0adR*.7kCMdnj",
            "LGF5]+Yk^6#M@-5c,1J5@[or[Q6.",
            "L6PZfSi_.AyE_3t7t7R**0o#DgR4",
            "LKO2:N%2Tw=w]~RBVZRi};RPxuwH",
        )
        val COLOURS = listOf(
            "#1E3A8A" to "blue", "#7C3AED" to "purple", "#DC2626" to "red", "#059669" to "green",
            "#F59E0B" to "orange", "#0D9488" to "teal", "#DB2777" to "pink", "#000000" to "black",
            "#F5F5F4" to "white", "#78716C" to "gray", "#EAB308" to "yellow", "#92400E" to "brown",
        )

        fun generate(count: Int): List<Wallpaper> {
            val random = Random(42) // deterministic across runs
            val now = Instant.now()
            return List(count) { i ->
                val category = CATEGORIES[i % CATEGORIES.size]
                val device = when {
                    i % 10 == 0 -> DeviceType.TABLET
                    i % 7 == 0 -> DeviceType.BOTH
                    else -> DeviceType.PHONE
                }
                val (width, height) = if (device == DeviceType.TABLET) 2560 to 1600 else 1440 to 3200
                val amoled = i % 5 == 0
                val (hex, family) = if (amoled) "#000000" to "black" else COLOURS[i % COLOURS.size]
                val downloads = random.nextInt(0, 5000)
                val favs = random.nextInt(0, 800)
                val published = now.minus((i * 11L) % 90, ChronoUnit.DAYS).minus(i.toLong(), ChronoUnit.HOURS)
                Wallpaper(
                    id = "00000000-0000-4000-8000-%012d".format(i + 1),
                    title = "${ADJECTIVES[i % ADJECTIVES.size]} ${NOUNS[(i / ADJECTIVES.size) % NOUNS.size]}",
                    storageKey = REAL_UPLOADS[i % REAL_UPLOADS.size],
                    originalExt = if (i % 3 == 0) "png" else "jpg",
                    width = width,
                    height = height,
                    aspectRatio = width.toDouble() / height,
                    deviceType = device,
                    isAmoled = amoled,
                    isCurated = i % 4 == 0,
                    curatedOrder = if (i % 4 == 0) i / 4 else null,
                    isPremium = i % 9 == 0,
                    dominantColor = hex,
                    colorFamily = family,
                    blurhash = BLURHASHES[i % BLURHASHES.size],
                    categoryId = "00000000-0000-4000-9000-%012d".format(i % CATEGORIES.size),
                    categorySlug = category,
                    categoryName = category.replaceFirstChar(Char::uppercase),
                    fileSizeKb = random.nextInt(300, 4000),
                    authorName = "Test Author",
                    license = "Test data (not real content)",
                    sourceUrl = null,
                    downloadCount = downloads,
                    favCount = favs,
                    viewCount = downloads * 4,
                    popularityScore = downloads * 0.1 + favs * 2.0,
                    tags = List(3) { TAGS[(i * 3 + it * 5) % TAGS.size] }.distinct(),
                    publishedAt = published,
                    createdAt = published,
                )
            }
        }
    }
}
