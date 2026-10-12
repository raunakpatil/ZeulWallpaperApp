package com.wallpaperapp.app.domain.model

import java.net.URI

/** Where an incoming link should take the user. */
sealed interface AppLink {
    data class Wallpaper(val id: String) : AppLink
    data class Collection(val id: String) : AppLink

    /** A Home chip, by its `home_sections` key (e.g. `amoled`). */
    data class Section(val name: String) : AppLink
}

/**
 * Share links (CONTRACTS §7) and app links (§8, §9). The web host is also in AndroidManifest.xml.
 * Everything that comes in is validated strictly: links are untrusted input.
 */
object ShareLinks {
    const val WEB_BASE = "https://raunakpatil.github.io/wallpaper-app-site/w/"
    /** Current scheme first; the pre-rename one is still accepted during development. */
    private val APP_SCHEMES = setOf("zeul", "wallpaperapp")
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val SECTION = Regex("^[a-z_]{1,32}$")

    /** The link we share. Always the web URL, so it works for people without the app. */
    fun webUrl(wallpaperId: String): String = "$WEB_BASE?id=$wallpaperId"

    fun parse(link: String?): AppLink? {
        if (link.isNullOrBlank()) return null
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            in APP_SCHEMES -> {
                val value = uri.path?.trim('/').orEmpty()
                when (uri.host) {
                    "wallpaper" -> value.uuidOrNull()?.let(AppLink::Wallpaper)
                    "collection" -> value.uuidOrNull()?.let(AppLink::Collection)
                    "section" -> value.takeIf { SECTION.matches(it) }?.let(AppLink::Section)
                    else -> null
                }
            }
            "https" -> uri.takeIf { it.host == "raunakpatil.github.io" && it.path.orEmpty().startsWith("/wallpaper-app-site/w") }
                ?.rawQuery?.split('&')
                ?.firstNotNullOfOrNull { part -> part.split('=', limit = 2).takeIf { it[0] == "id" && it.size == 2 }?.get(1) }
                ?.uuidOrNull()?.let(AppLink::Wallpaper)
            else -> null
        }
    }

    /** Wallpaper id from a wallpaper link, or null. */
    fun parseWallpaperId(link: String?): String? = (parse(link) as? AppLink.Wallpaper)?.id

    private fun String.uuidOrNull(): String? = takeIf { UUID.matches(it) }?.lowercase()
}
