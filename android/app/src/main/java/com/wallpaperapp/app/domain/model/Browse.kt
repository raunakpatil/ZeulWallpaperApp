package com.wallpaperapp.app.domain.model

/** Sections served by the `get_section` RPC. Popular and For You have their own RPCs. */
enum class Section(val key: String) {
    CURATED("curated"), NEW("new"), AMOLED("amoled"), TABLET("tablet"), PREMIUM("premium"),
}

enum class PopularRange(val key: String) { WEEK("week"), MONTH("month"), ALL("all") }

/** Colour families accepted by `search_wallpapers(color => …)`. */
val ColorFamilies = listOf(
    "red", "orange", "yellow", "green", "teal", "blue", "purple", "pink", "brown", "black", "white", "gray",
)

/** All fields optional, matching `search_wallpapers`. [device] is 'phone' or 'tablet', [category] a slug. */
data class SearchFilters(
    val color: String? = null,
    val device: String? = null,
    val amoled: Boolean? = null,
    val category: String? = null,
)

data class SearchSuggestion(val label: String, val kind: Kind) {
    enum class Kind { TAG, CATEGORY, TITLE }
}

/** Events accepted by `record_event`. Downloads go through `increment_download` instead. */
enum class EventType(val key: String) { VIEW("view"), APPLY("apply"), FAV("fav"), UNFAV("unfav") }

data class Category(val id: String, val name: String, val slug: String, val icon: String?, val sortOrder: Int)

/** A curated pack from `get_collections`. The cover is a CDN storage key, like a wallpaper's. */
data class WallpaperCollection(
    val id: String,
    val title: String,
    val isPremium: Boolean,
    val itemCount: Int,
    val coverStorageKey: String?,
    val coverBlurhash: String?,
    val coverDominantColor: String?,
) {
    fun coverThumbUrl(cdnBaseUrl: String): String? {
        if (cdnBaseUrl.isBlank() || coverStorageKey.isNullOrBlank()) return null
        return "${cdnBaseUrl.trimEnd('/')}/${coverStorageKey.trim('/')}/thumb.webp"
    }
}
