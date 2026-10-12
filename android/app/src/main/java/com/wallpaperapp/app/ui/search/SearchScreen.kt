package com.wallpaperapp.app.ui.search

import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.SoftTextField
import com.wallpaperapp.app.ui.theme.SoftTextButton
import com.wallpaperapp.app.ui.theme.SoftSectionLabel
import com.wallpaperapp.app.ui.theme.SoftIconButton
import com.wallpaperapp.app.ui.theme.SoftChip
import com.wallpaperapp.app.ui.theme.LocalSoft
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import com.wallpaperapp.app.ui.components.colorFamilyLabel
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.domain.model.SearchFilters
import com.wallpaperapp.app.domain.model.SearchSuggestion
import com.wallpaperapp.app.domain.model.Wallpaper
import com.wallpaperapp.app.ui.components.ColorFamilySwatches
import com.wallpaperapp.app.ui.components.WallpaperGrid
import com.wallpaperapp.app.ui.components.rememberGridAds

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onWallpaperClick: (Wallpaper) -> Unit,
    showBack: Boolean = true,
    autoFocus: Boolean = false,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }

    // From a search button: typing straight away. From a tag/category: results. The tab: just the page.
    LaunchedEffect(Unit) { if (autoFocus && !state.showResults) focus.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                SoftIconButton(ZeulIcons.Back, stringResource(R.string.back), onBack, size = 46.dp, tint = LocalSoft.current.text)
                Spacer(Modifier.width(12.dp))
            }
            SoftTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                placeholder = stringResource(R.string.search_hint),
                leading = ZeulIcons.Search,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.submit()
                    focusManager.clearFocus()
                }),
                modifier = Modifier.weight(1f),
                fieldModifier = Modifier.focusRequester(focus).onFocusChanged { fieldFocused = it.isFocused },
                trailing = if (state.text.isEmpty()) null else {
                    {
                        IconButton(onClick = { viewModel.onTextChange("") }, modifier = Modifier.size(36.dp)) {
                            Icon(ZeulIcons.Close, contentDescription = stringResource(R.string.clear), tint = LocalSoft.current.textMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                },
            )
        }

        FilterRow(state.filters, state.categories, viewModel)

        Box(Modifier.fillMaxSize()) {
            when {
                state.showResults -> WallpaperGrid(
                    items = results,
                    cdnBaseUrl = state.cdnBaseUrl,
                    onClick = { wallpaper, list ->
                        viewModel.onWallpaperOpened(wallpaper, list)
                        onWallpaperClick(wallpaper)
                    },
                    emptyTitle = stringResource(R.string.search_no_matches),
                    ads = rememberGridAds(),
                    emptyBody = stringResource(R.string.search_no_matches_body),
                )
                else -> StartPage(state.recent, state.trending, onPick = {
                    viewModel.submit(it)
                    focusManager.clearFocus()
                }, onClearRecent = viewModel::clearRecent)
            }
            // Autocomplete floats over whatever is below while typing.
            if (fieldFocused && state.suggestions.isNotEmpty()) {
                Suggestions(state.suggestions) {
                    viewModel.onSuggestion(it)
                    focusManager.clearFocus()
                }
            }
        }
    }
}

@Composable
private fun FilterRow(filters: SearchFilters, categories: List<Category>, viewModel: SearchViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            MenuChip(
                label = filters.color?.let { colorFamilyLabel(it) } ?: stringResource(R.string.filter_colour),
                selected = filters.color != null,
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.filter_any_colour)) + ColorFamilySwatches.map { (name, _) -> name to colorFamilyLabel(name) },
                onSelect = viewModel::setColor,
                swatch = { key -> ColorFamilySwatches.firstOrNull { it.first == key }?.second },
            )
        }
        item {
            MenuChip(
                label = when (filters.device) { "phone" -> stringResource(R.string.device_phone); "tablet" -> stringResource(R.string.device_tablet); else -> stringResource(R.string.filter_device) },
                selected = filters.device != null,
                options = listOf(null to stringResource(R.string.filter_any_device), "phone" to stringResource(R.string.device_phone), "tablet" to stringResource(R.string.device_tablet)),
                onSelect = viewModel::setDevice,
            )
        }
        item {
            SoftChip(stringResource(R.string.amoled), selected = filters.amoled == true, onClick = { viewModel.setAmoled(if (filters.amoled == true) null else true) })
        }
        item {
            MenuChip(
                label = categories.firstOrNull { it.slug == filters.category }?.name ?: filters.category?.replaceFirstChar(Char::uppercase) ?: stringResource(R.string.filter_category),
                selected = filters.category != null,
                options = listOf<Pair<String?, String>>(null to stringResource(R.string.filter_any_category)) + categories.map { it.slug to it.name },
                onSelect = viewModel::setCategory,
            )
        }
        if (filters != SearchFilters()) {
            item { SoftTextButton(stringResource(R.string.clear), viewModel::clearFilters) }
        }
    }
}

@Composable
private fun MenuChip(
    label: String,
    selected: Boolean,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
    swatch: (String?) -> Color? = { null },
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SoftChip(label, selected = selected, onClick = { open = true }, trailing = ZeulIcons.ChevronDown)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    leadingIcon = swatch(key)?.let { color ->
                        {
                            Box(
                                Modifier.size(18.dp).background(color, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            )
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(key)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartPage(recent: List<String>, trending: List<String>, onPick: (String) -> Unit, onClearRecent: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (recent.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SoftSectionLabel(stringResource(R.string.search_recent), Modifier.weight(1f).semantics { heading() })
                    SoftTextButton(stringResource(R.string.clear), onClearRecent, color = LocalSoft.current.textMuted)
                }
            }
            items(recent) { query ->
                ListItem(headlineContent = { Text(query) }, modifier = Modifier.clickable { onPick(query) })
            }
            item { Spacer(Modifier.size(8.dp)) }
        }
        if (trending.isNotEmpty()) {
            item { SoftSectionLabel(stringResource(R.string.search_trending), Modifier.semantics { heading() }) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                    trending.forEach { tag -> SoftChip("#$tag", selected = false, onClick = { onPick(tag) }) }
                }
            }
        }
    }
}

@Composable
private fun Suggestions(suggestions: List<SearchSuggestion>, onPick: (SearchSuggestion) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        suggestions.forEach { s ->
            ListItem(
                headlineContent = { Text(s.label) },
                supportingContent = {
                    Text(
                        when (s.kind) {
                            SearchSuggestion.Kind.TAG -> stringResource(R.string.suggestion_tag)
                            SearchSuggestion.Kind.CATEGORY -> stringResource(R.string.filter_category)
                            SearchSuggestion.Kind.TITLE -> stringResource(R.string.suggestion_wallpaper)
                        },
                    )
                },
                leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.clickable { onPick(s) },
            )
        }
        HorizontalDivider()
    }
}
