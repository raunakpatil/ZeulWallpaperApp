package com.wallpaperapp.app.ui.explore

import com.wallpaperapp.app.ui.theme.LocalCardStyle
import com.wallpaperapp.app.ui.theme.SoftSectionLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpaperapp.app.R
import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.domain.model.ImageVariant
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.domain.model.WallpaperCollection
import com.wallpaperapp.app.domain.model.imageUrl
import com.wallpaperapp.app.ui.components.ImageCard
import com.wallpaperapp.app.ui.components.RoundSoftButton
import com.wallpaperapp.app.ui.components.gridColumns
import com.wallpaperapp.app.ui.theme.AccentButton
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.SoftScreenTitle
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.riseIn

@Composable
fun ExploreScreen(
    onSearch: () -> Unit,
    onCategory: (Category) -> Unit,
    onCollection: (WallpaperCollection) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val columns = gridColumns()
    val cards = LocalCardStyle.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(cards.gap)) {
        item {
            SoftScreenTitle(stringResource(R.string.nav_explore)) {
                RoundSoftButton(ZeulIcons.Search, stringResource(R.string.explore_search_hint), onSearch)
            }
        }

        if (state.collections.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.explore_collections)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = cards.sidePadding), horizontalArrangement = Arrangement.spacedBy(cards.gap)) {
                    items(state.collections, key = { it.id }) { collection ->
                        ImageCard(
                            imageUrl = collection.coverThumbUrl(state.cdnBaseUrl),
                            title = collection.title,
                            label = pluralStringResource(R.plurals.wallpaper_count, collection.itemCount, collection.itemCount),
                            labelIcon = if (collection.isPremium) ZeulIcons.Gem else null,
                            blurhash = collection.coverBlurhash,
                            dominantColor = collection.coverDominantColor,
                            titleSize = 18,
                            onClick = { onCollection(collection) },
                            modifier = Modifier.width(168.dp).aspectRatio(0.72f),
                        )
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.explore_categories)) }
        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.error -> item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.explore_categories_error), style = MaterialTheme.typography.bodyMedium)
                    AccentButton(stringResource(R.string.retry), viewModel::load, Modifier.padding(top = 12.dp))
                }
            }
            else -> {
                // The first category gets a full-width hero card, the rest a grid.
                state.categories.firstOrNull()?.let { first ->
                    item(key = "hero-${first.id}") {
                        CategoryCard(first, state.covers[first.slug], state.cdnBaseUrl, hero = true, Modifier.padding(horizontal = cards.sidePadding).fillMaxWidth().height(210.dp)) { onCategory(first) }
                    }
                }
                itemsIndexed(state.categories.drop(1).chunked(columns)) { rowIndex, row ->
                    Row(Modifier.padding(horizontal = cards.sidePadding).riseIn(rowIndex, animate = true), horizontalArrangement = Arrangement.spacedBy(cards.gap)) {
                        row.forEach { category ->
                            CategoryCard(category, state.covers[category.slug], state.cdnBaseUrl, hero = false, Modifier.weight(1f).aspectRatio(0.8f)) { onCategory(category) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) } // keep the last row aligned
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    SoftSectionLabel(text, Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp))
}

/** Category as a big photo card: cover wallpaper, "Name / Wallpapers" bottom-left, ↗ top-right. */
@Composable
private fun CategoryCard(category: Category, cover: Wallpaper?, cdnBaseUrl: String, hero: Boolean, modifier: Modifier, onClick: () -> Unit) {
    ImageCard(
        imageUrl = cover?.imageUrl(cdnBaseUrl, if (hero) ImageVariant.PREVIEW else ImageVariant.THUMB),
        title = category.name,
        subtitle = stringResource(R.string.category_card_subtitle),
        blurhash = cover?.blurhash,
        dominantColor = cover?.dominantColor,
        titleSize = if (hero) 26 else 20,
        onClick = onClick,
        modifier = modifier,
    )
}
