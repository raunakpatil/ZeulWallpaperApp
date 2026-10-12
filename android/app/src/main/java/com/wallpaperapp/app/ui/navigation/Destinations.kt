package com.wallpaperapp.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.R
import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.
@Serializable object HomeRoute
@Serializable object ExploreRoute
@Serializable object FavouritesRoute
@Serializable object ProfileRoute
@Serializable data class PreviewRoute(val wallpaperId: String)
@Serializable data class ApplyRoute(val wallpaperId: String)

/**
 * Opens search with an optional starting query and filters (from Explore, tags, etc.). [focus] opens
 * the keyboard straight away (the header search buttons); the plain tab route doesn't.
 */
@Serializable data class SearchRoute(val query: String? = null, val category: String? = null, val color: String? = null, val focus: Boolean = false)
@Serializable data class CollectionRoute(val collectionId: String, val title: String)
@Serializable object PaywallRoute
@Serializable object StylesRoute
@Serializable object SignInRoute
@Serializable object AutoChangerRoute
@Serializable object UploadRoute
@Serializable object MyUploadsRoute

enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, R.string.nav_home, ZeulIcons.Home, ZeulIcons.Home),
    EXPLORE(ExploreRoute, R.string.nav_explore, ZeulIcons.Grid, ZeulIcons.Grid),
    SEARCH(SearchRoute(), R.string.nav_search, ZeulIcons.Search, ZeulIcons.Search),
    FAVOURITES(FavouritesRoute, R.string.nav_favourites, ZeulIcons.HeartFilled, ZeulIcons.Heart),
    PROFILE(ProfileRoute, R.string.nav_profile, ZeulIcons.Person, ZeulIcons.Person),
}
