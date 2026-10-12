package com.wallpaperapp.app.ui.components

import com.wallpaperapp.app.ui.theme.LocalCardStyle
import com.wallpaperapp.app.ui.theme.softRaised
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.LocalSoft
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.wallpaperapp.app.ui.theme.ZeulRadius
import com.wallpaperapp.app.ui.theme.SoftTextButton
import com.wallpaperapp.app.ui.theme.AccentButton
import androidx.compose.runtime.remember
import com.wallpaperapp.app.ui.theme.riseIn
import androidx.compose.foundation.layout.Arrangement
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.ui.LocalWindowSizeClass

/** 2 columns on phones, 3 on medium width, 4 on large screens. */
@Composable
fun gridColumns(): Int = when (LocalWindowSizeClass.current.widthSizeClass) {
    WindowWidthSizeClass.Compact -> 2
    WindowWidthSizeClass.Medium -> 3
    else -> 4
}

/**
 * Staggered wallpaper grid with every paging state handled: skeletons, empty, error + retry,
 * load-more and pull to refresh. [header] adds full-width items above the wallpapers.
 * [onClick] also receives the loaded list so the preview can swipe through it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperGrid(
    items: LazyPagingItems<Wallpaper>,
    cdnBaseUrl: String,
    onClick: (Wallpaper, List<Wallpaper>) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    emptyTitle: String = stringResource(R.string.grid_empty_title),
    emptyBody: String = stringResource(R.string.grid_empty_body),
    emptyIcon: ImageVector = ZeulIcons.Search,
    contentPadding: PaddingValues = PaddingValues(start = LocalCardStyle.current.sidePadding, end = LocalCardStyle.current.sidePadding, top = 4.dp, bottom = 20.dp),
    ads: GridAds? = null,
    header: LazyStaggeredGridScope.() -> Unit = {},
) {
    // Large screens: list-detail. A tap shows the wallpaper in a side pane;
    // "Open" goes to the full preview, where Download/Apply live.
    if (LocalWindowSizeClass.current.widthSizeClass == WindowWidthSizeClass.Expanded) {
        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = selectedId?.let { id -> items.itemSnapshotList.items.firstOrNull { it.id == id } }
        Row(modifier.fillMaxSize()) {
            GridContent(
                items, cdnBaseUrl, onTileClick = { wallpaper, _ -> selectedId = wallpaper.id }, columns = 3,
                modifier = Modifier.weight(0.6f), state = state, emptyTitle = emptyTitle, emptyBody = emptyBody, emptyIcon = emptyIcon,
                contentPadding = contentPadding, ads = ads, header = header,
            )
            WallpaperDetailPane(
                wallpaper = selected,
                cdnBaseUrl = cdnBaseUrl,
                onOpen = { wallpaper -> onClick(wallpaper, items.itemSnapshotList.items) },
                modifier = Modifier.weight(0.4f).fillMaxHeight(),
            )
        }
    } else {
        GridContent(items, cdnBaseUrl, onClick, gridColumns(), modifier, state, emptyTitle, emptyBody, emptyIcon, contentPadding, ads, header)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridContent(
    items: LazyPagingItems<Wallpaper>,
    cdnBaseUrl: String,
    onTileClick: (Wallpaper, List<Wallpaper>) -> Unit,
    columns: Int,
    modifier: Modifier,
    state: LazyStaggeredGridState,
    emptyTitle: String,
    emptyBody: String,
    emptyIcon: ImageVector,
    contentPadding: PaddingValues,
    ads: GridAds?,
    header: LazyStaggeredGridScope.() -> Unit,
) {
    val onClick = onTileClick
    val actions: TileActionsViewModel = hiltViewModel()
    val favoriteIds by actions.favoriteIds.collectAsStateWithLifecycle()
    // Cards rise in once: the first screenful of each new list, never again when scrolled back into view.
    val revealed = remember { mutableSetOf<String>() }
    val refresh = items.loadState.refresh
    PullToRefreshBox(
        isRefreshing = refresh is LoadState.Loading && items.itemCount > 0,
        onRefresh = { items.refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(LocalCardStyle.current.gap),
            verticalItemSpacing = LocalCardStyle.current.gap,
            modifier = Modifier.fillMaxSize(),
        ) {
            header()
            when {
                refresh is LoadState.Loading && items.itemCount == 0 -> {
                    items(count = SKELETON_RATIOS.size) { WallpaperTileSkeleton(SKELETON_RATIOS[it], Modifier.riseIn(it, animate = true)) }
                }
                refresh is LoadState.Error && items.itemCount == 0 -> fullWidth("error") {
                    GridMessage(
                        title = stringResource(R.string.grid_error_title),
                        body = stringResource(R.string.check_connection),
                        action = stringResource(R.string.retry),
                        onAction = items::retry,
                        icon = ZeulIcons.Reset,
                    )
                }
                refresh is LoadState.NotLoading && items.itemCount == 0 -> fullWidth("empty") {
                    GridMessage(title = emptyTitle, body = emptyBody, icon = emptyIcon)
                }
                else -> {
                    val layout = ads?.layout ?: AdLayout.None
                    items(
                        count = layout.slotCount(items.itemCount),
                        key = { position ->
                            if (layout.isAd(position)) "ad-${layout.adIndex(position)}"
                            else items.peek(layout.wallpaperIndex(position))?.id ?: "slot-$position"
                        },
                        contentType = { position -> if (layout.isAd(position)) "ad" else "wallpaper" },
                    ) { position ->
                        if (layout.isAd(position)) {
                            // Native ads need at least ~160×240dp (CONTRACTS section 5).
                            ads?.slot?.invoke(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(LocalCardStyle.current.corner)))
                        } else {
                            items[layout.wallpaperIndex(position)]?.let { wallpaper ->
                                val open = { onClick(wallpaper, items.itemSnapshotList.items) }
                                val animateIn = position < 8 && revealed.add(wallpaper.id)
                                WallpaperCard(
                                    modifier = Modifier.riseIn(position, animateIn),
                                    wallpaper = wallpaper,
                                    cdnBaseUrl = cdnBaseUrl,
                                    isFavourite = wallpaper.id in favoriteIds,
                                    onClick = open,
                                    onFavourite = { actions.toggleFavorite(wallpaper) },
                                )
                            }
                        }
                    }
                }
            }
            when (items.loadState.append) {
                is LoadState.Loading -> fullWidth("append-loading") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
                is LoadState.Error -> fullWidth("append-error") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SoftTextButton(stringResource(R.string.grid_load_more_error), items::retry)
                    }
                }
                else -> Unit
            }
        }
    }
}

fun LazyStaggeredGridScope.fullWidth(key: String, content: @Composable () -> Unit) =
    item(key = key, span = StaggeredGridItemSpan.FullLine) { content() }

@Composable
fun GridMessage(title: String, body: String, action: String? = null, onAction: () -> Unit = {}, icon: ImageVector = ZeulIcons.Sparkle) {
    val soft = LocalSoft.current
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 64.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A raised tile with the accent icon, then a display-face heading and a short muted line.
        Box(Modifier.size(72.dp).softRaised(RoundedCornerShape(ZeulRadius.Panel), depth = 6.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = soft.accent, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = soft.text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 22.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = soft.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).widthIn(max = 300.dp))
        if (action != null) AccentButton(action, onAction, Modifier.padding(top = 24.dp))
    }
}

/** Varied heights so the loading state already looks like the staggered grid. */
private val SKELETON_RATIOS = listOf(0.56f, 0.75f, 0.62f, 0.5f, 0.7f, 0.56f, 0.8f, 0.6f)
