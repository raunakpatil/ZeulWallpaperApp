package com.wallpaperapp.app.ui.preview

import com.wallpaperapp.app.domain.model.Wallpaper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the list a grid was showing to the preview, so the user can swipe through it in the
 * same order. In-memory only: after process death the preview falls back to the single id
 * in its route. Lists are capped so a long scroll doesn't pin thousands of items.
 */
@Singleton
class PreviewSession @Inject constructor() {

    private var wallpapers: List<Wallpaper> = emptyList()

    fun open(list: List<Wallpaper>, selected: Wallpaper) {
        val index = list.indexOfFirst { it.id == selected.id }
        wallpapers = if (index < 0) {
            listOf(selected)
        } else {
            // Centre on the selection, shifting the window when it runs into either end.
            val from = (index - MAX_ITEMS / 2).coerceIn(0, (list.size - MAX_ITEMS).coerceAtLeast(0))
            list.subList(from, (from + MAX_ITEMS).coerceAtMost(list.size)).toList()
        }
    }

    /** The list to swipe through, or null if the session doesn't contain [wallpaperId]. */
    fun listFor(wallpaperId: String): List<Wallpaper>? = wallpapers.takeIf { list -> list.any { it.id == wallpaperId } }

    companion object {
        const val MAX_ITEMS = 300
    }
}
