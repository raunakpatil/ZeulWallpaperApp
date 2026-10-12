package com.wallpaperapp.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.wallpaperapp.app.ui.components.LocalSharedScope
import com.wallpaperapp.app.ui.components.WithNavScope
import com.wallpaperapp.app.ui.theme.ZeulMotion
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wallpaperapp.app.ui.components.SoftNavBar
import com.wallpaperapp.app.ui.explore.CollectionScreen
import com.wallpaperapp.app.ui.explore.ExploreScreen
import com.wallpaperapp.app.ui.navigation.CollectionRoute
import com.wallpaperapp.app.ui.navigation.PaywallRoute
import com.wallpaperapp.app.ui.navigation.SearchRoute
import com.wallpaperapp.app.ui.navigation.StylesRoute
import com.wallpaperapp.app.ui.navigation.AutoChangerRoute
import com.wallpaperapp.app.ui.settings.AutoChangerScreen
import com.wallpaperapp.app.ui.navigation.SignInRoute
import com.wallpaperapp.app.ui.navigation.UploadRoute
import com.wallpaperapp.app.ui.navigation.MyUploadsRoute
import com.wallpaperapp.app.ui.upload.UploadScreen
import com.wallpaperapp.app.ui.upload.MyUploadsScreen
import com.wallpaperapp.app.ui.account.SignInScreen
import com.wallpaperapp.app.domain.model.AppLink
import com.wallpaperapp.app.ui.common.AppGate
import com.wallpaperapp.app.ui.home.HomeSectionRequest
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.wallpaperapp.app.ui.common.AppGateViewModel
import com.wallpaperapp.app.ui.common.MaintenanceScreen
import com.wallpaperapp.app.ui.common.UpdateRequiredScreen
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.LaunchedEffect
import com.wallpaperapp.app.ui.onboarding.OnboardingScreen
import com.wallpaperapp.app.ui.onboarding.StylesScreen
import androidx.compose.material3.Surface
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperapp.app.ui.paywall.PaywallScreen
import com.wallpaperapp.app.ui.search.SearchScreen
import com.wallpaperapp.app.ui.favorites.FavouritesScreen
import com.wallpaperapp.app.ui.home.HomeScreen
import com.wallpaperapp.app.ui.apply.ApplyScreen
import com.wallpaperapp.app.ui.navigation.ApplyRoute
import com.wallpaperapp.app.ui.navigation.ExploreRoute
import com.wallpaperapp.app.ui.navigation.FavouritesRoute
import com.wallpaperapp.app.ui.navigation.HomeRoute
import com.wallpaperapp.app.ui.navigation.PreviewRoute
import com.wallpaperapp.app.ui.navigation.ProfileRoute
import com.wallpaperapp.app.ui.preview.PreviewScreen
import com.wallpaperapp.app.ui.navigation.TopLevelDestination
import com.wallpaperapp.app.ui.settings.ProfileScreen

/** Lets any screen read the window size (grid columns, two-pane layouts) without threading it through. */
val LocalWindowSizeClass = staticCompositionLocalOf<WindowSizeClass> {
    error("LocalWindowSizeClass not provided")
}

@Composable
fun WallpaperAppRoot(
    windowSizeClass: WindowSizeClass,
    deepLink: AppLink?,
    onDeepLinkHandled: () -> Unit,
    viewModel: AppGateViewModel = hiltViewModel(),
) {
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
        when (val g = gate) {
            AppGate.Loading -> Surface(Modifier.fillMaxSize()) {} // a blink while DataStore loads; avoids flashing Home first
            is AppGate.Maintenance -> MaintenanceScreen(g.message, onRetry = viewModel::retry)
            AppGate.UpdateRequired -> UpdateRequiredScreen(LocalActivity.current)
            AppGate.Onboarding -> Surface(Modifier.fillMaxSize()) { OnboardingScreen(onDone = {}) } // the gate moves on by itself
            AppGate.Ready -> MainScaffold(windowSizeClass, deepLink, onDeepLinkHandled)
        }
    }
}

@Composable
private fun MainScaffold(
    windowSizeClass: WindowSizeClass,
    deepLink: AppLink?,
    onDeepLinkHandled: () -> Unit,
    linkRouter: LinkRouterViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    // A shared link (CONTRACTS section 7) opens straight into the preview.
    LaunchedEffect(deepLink) {
        when (deepLink) {
            null -> return@LaunchedEffect
            is AppLink.Wallpaper -> navController.navigate(PreviewRoute(deepLink.id))
            is AppLink.Collection -> navController.navigate(CollectionRoute(deepLink.id, title = ""))
            is AppLink.Section -> {
                linkRouter.homeSection.open(deepLink.name)
                navController.navigateToTopLevel(TopLevelDestination.HOME)
            }
        }
        onDeepLinkHandled()
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val useNavRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    // Bars only on the top-level screens; detail screens like the preview are full-screen.
    val showNav = currentDestination == null || TopLevelDestination.entries.any { currentDestination.isOn(it) }

    CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
        if (useNavRail) {
            Row(Modifier.fillMaxSize()) {
                if (showNav) NavigationRail {
                    TopLevelDestination.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = currentDestination.isOn(dest),
                            onClick = { navController.navigateToTopLevel(dest) },
                            icon = { DestinationIcon(dest, currentDestination.isOn(dest)) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
                Scaffold(contentWindowInsets = if (showNav) ScaffoldDefaults.contentWindowInsets else WindowInsets(0)) { padding ->
                    AppNavHost(navController, Modifier.padding(padding).consumeWindowInsets(padding))
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = if (showNav) ScaffoldDefaults.contentWindowInsets else WindowInsets(0),
                bottomBar = {
                    if (showNav) SoftNavBar(
                        isSelected = { currentDestination.isOn(it) },
                        onSelect = { navController.navigateToTopLevel(it) },
                    )
                },
            ) { padding ->
                AppNavHost(navController, Modifier.padding(padding).consumeWindowInsets(padding))
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, modifier: Modifier) {
    SharedTransitionLayout(modifier) {
    CompositionLocalProvider(LocalSharedScope provides this) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = { zeulEnter(initialState.destination, targetState.destination) },
        exitTransition = { zeulExit(initialState.destination, targetState.destination) },
        popEnterTransition = { zeulEnter(initialState.destination, targetState.destination, pop = true) },
        popExitTransition = { zeulExit(initialState.destination, targetState.destination, pop = true) },
    ) {
        composable<HomeRoute> {
            WithNavScope {
                HomeScreen(
                    onWallpaperClick = { navController.navigate(PreviewRoute(it.id)) },
                    onSearch = { navController.navigate(SearchRoute(focus = true)) },
                    onPremium = { navController.navigate(PaywallRoute) },
                    onUpload = { navController.navigate(UploadRoute) },
                )
            }
        }
        composable<ExploreRoute> {
            WithNavScope {
                ExploreScreen(
                    onSearch = { navController.navigate(SearchRoute(focus = true)) },
                    onCategory = { navController.navigate(SearchRoute(category = it.slug)) },
                    onCollection = { navController.navigate(CollectionRoute(it.id, it.title)) },
                )
            }
        }
        composable<SearchRoute> { entry ->
            val route = entry.toRoute<SearchRoute>()
            WithNavScope {
                SearchScreen(
                    // The bare tab has no back arrow and doesn't pop the keyboard over the tab bar.
                    showBack = route != SearchRoute(),
                    autoFocus = route.focus,
                    onBack = navController::popBackStack, onWallpaperClick = { navController.navigate(PreviewRoute(it.id)) })
            }
        }
        composable<CollectionRoute> {
            WithNavScope {
                CollectionScreen(onBack = navController::popBackStack, onWallpaperClick = { navController.navigate(PreviewRoute(it.id)) })
            }
        }
        composable<FavouritesRoute> { WithNavScope { FavouritesScreen(onWallpaperClick = { navController.navigate(PreviewRoute(it.id)) }, onGoPremium = { navController.navigate(PaywallRoute) }) } }
        composable<ProfileRoute> {
            WithNavScope {
                ProfileScreen(
                    onGoPremium = { navController.navigate(PaywallRoute) },
                    onStyles = { navController.navigate(StylesRoute) },
                    onSignIn = { navController.navigate(SignInRoute) },
                    onAutoChanger = { navController.navigate(AutoChangerRoute) },
                    onUpload = { navController.navigate(UploadRoute) },
                    onMyUploads = { navController.navigate(MyUploadsRoute) },
                )
            }
        }
        composable<AutoChangerRoute> {
            WithNavScope {
                AutoChangerScreen(onBack = navController::popBackStack, onGoPremium = { navController.navigate(PaywallRoute) })
            }
        }
        composable<UploadRoute> {
            WithNavScope {
                UploadScreen(
                    onBack = navController::popBackStack,
                    onSignIn = { navController.navigate(SignInRoute) },
                    onMyUploads = { navController.navigate(MyUploadsRoute) },
                    onOpenWallpaper = { navController.navigate(PreviewRoute(it)) },
                )
            }
        }
        composable<MyUploadsRoute> {
            WithNavScope {
                MyUploadsScreen(
                    onBack = navController::popBackStack,
                    onUpload = { navController.navigate(UploadRoute) },
                    onOpenWallpaper = { navController.navigate(PreviewRoute(it)) },
                )
            }
        }
        composable<StylesRoute> { WithNavScope { StylesScreen(onBack = navController::popBackStack) } }
        composable<SignInRoute> { WithNavScope { SignInScreen(onDone = navController::popBackStack) } }
        composable<PaywallRoute> { WithNavScope { PaywallScreen(onClose = navController::popBackStack) } }
        composable<PreviewRoute> {
            WithNavScope {
                PreviewScreen(
                    onBack = navController::popBackStack,
                    onApply = { navController.navigate(ApplyRoute(it.id)) },
                    onTagClick = { navController.navigate(SearchRoute(query = it)) },
                    onGoPremium = { navController.navigate(PaywallRoute) },
                )
            }
        }
        composable<ApplyRoute> { WithNavScope { ApplyScreen(onBack = navController::popBackStack) } }
    }
    }
    }
}

/*
 * Screen transitions (Zeul motion): tab ↔ tab = fade-through (quick fade out, fade in with a hint
 * of scale); into a detail screen = slide in from the right with a fade (back reverses it);
 * the preview just fades, because the wallpaper itself grows out of its card (sharedWallpaper).
 */
private fun NavDestination.isPreview() = hasRoute(PreviewRoute::class)
private fun NavDestination.isTopLevel() = TopLevelDestination.entries.any { hasRoute(it.route::class) }

private fun zeulEnter(from: NavDestination, to: NavDestination, pop: Boolean = false): EnterTransition = when {
    to.isPreview() || from.isPreview() -> fadeIn(tween(ZeulMotion.MEDIUM))
    from.isTopLevel() && to.isTopLevel() -> fadeIn(tween(ZeulMotion.MEDIUM, delayMillis = 70)) + scaleIn(tween(ZeulMotion.MEDIUM, delayMillis = 70), initialScale = 0.97f)
    pop -> slideInHorizontally(tween(ZeulMotion.MEDIUM, easing = ZeulMotion.Emphasized)) { -it / 6 } + fadeIn(tween(ZeulMotion.MEDIUM))
    else -> slideInHorizontally(tween(ZeulMotion.MEDIUM, easing = ZeulMotion.Emphasized)) { it / 5 } + fadeIn(tween(ZeulMotion.MEDIUM))
}

private fun zeulExit(from: NavDestination, to: NavDestination, pop: Boolean = false): ExitTransition = when {
    to.isPreview() || from.isPreview() -> fadeOut(tween(ZeulMotion.MEDIUM))
    from.isTopLevel() && to.isTopLevel() -> fadeOut(tween(90))
    pop -> slideOutHorizontally(tween(ZeulMotion.MEDIUM, easing = ZeulMotion.Emphasized)) { it / 5 } + fadeOut(tween(ZeulMotion.SHORT))
    else -> slideOutHorizontally(tween(ZeulMotion.MEDIUM, easing = ZeulMotion.Emphasized)) { -it / 6 } + fadeOut(tween(ZeulMotion.SHORT))
}

@Composable
private fun DestinationIcon(dest: TopLevelDestination, selected: Boolean) {
    Icon(
        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
        contentDescription = null, // the visible label describes it
    )
}

private fun NavDestination?.isOn(dest: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(dest.route::class) } == true

private fun NavHostController.navigateToTopLevel(dest: TopLevelDestination) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Gives the root access to app-wide link targets (e.g. Home's section request). */
@HiltViewModel
class LinkRouterViewModel @Inject constructor(val homeSection: HomeSectionRequest) : ViewModel()
