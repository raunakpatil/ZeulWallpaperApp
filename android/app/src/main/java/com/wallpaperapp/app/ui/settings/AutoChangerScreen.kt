package com.wallpaperapp.app.ui.settings

import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.SoftTopBar
import com.wallpaperapp.app.ui.theme.SoftSegmented
import com.wallpaperapp.app.ui.theme.SoftSectionLabel
import com.wallpaperapp.app.ui.theme.SoftGroup
import com.wallpaperapp.app.ui.theme.SoftChip
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.AccentButton
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import com.wallpaperapp.app.ui.common.UiText
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.repository.WallpaperRepository
import com.wallpaperapp.app.domain.model.ApplyTarget
import com.wallpaperapp.app.domain.model.AutoChangerSettings
import com.wallpaperapp.app.domain.model.Category
import com.wallpaperapp.app.work.AutoChangerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoChangerViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val scheduler: AutoChangerScheduler,
    repository: WallpaperRepository,
    premiumManager: PremiumManager,
) : ViewModel() {
    val settings: StateFlow<AutoChangerSettings> =
        preferences.autoChanger.stateIn(viewModelScope, SharingStarted.Eagerly, AutoChangerSettings())
    val isPremium: StateFlow<Boolean> = premiumManager.isPremium

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    init {
        viewModelScope.launch { _categories.value = runCatching { repository.categories() }.getOrDefault(emptyList()) }
    }

    fun update(transform: (AutoChangerSettings) -> AutoChangerSettings) {
        viewModelScope.launch { preferences.updateAutoChanger(transform) }
    }

    fun changeNow() {
        scheduler.runNow()
        _message.value = UiText.Res(R.string.changing_wallpaper)
    }

    fun messageShown() {
        _message.value = null
    }
}

/** Premium perk: rotates the wallpaper from favourites, For You or a category. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AutoChangerScreen(onBack: () -> Unit, onGoPremium: () -> Unit, viewModel: AutoChangerViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val message = viewModel.message.collectAsStateWithLifecycle().value?.asString()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }

    val soft = LocalSoft.current
    Column(Modifier.fillMaxSize()) {
        SoftTopBar(onBack, stringResource(R.string.back), modifier = Modifier.statusBarsPadding(), title = stringResource(R.string.auto_changer))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!isPremium) {
                SoftGroup {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.auto_changer_upsell), style = MaterialTheme.typography.bodyLarge, color = soft.text)
                        AccentButton(stringResource(R.string.go_premium_trial), onGoPremium, Modifier.fillMaxWidth(), leading = ZeulIcons.Gem)
                    }
                }
                return@Column
            }
            SoftGroup {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.auto_changer_toggle)) },
                    supportingContent = { Text(stringResource(R.string.auto_changer_toggle_body)) },
                    trailingContent = { Switch(checked = settings.enabled, onCheckedChange = { on -> viewModel.update { it.copy(enabled = on) } }) },
                )
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftSectionLabel(stringResource(R.string.auto_changer_every))
                SoftSegmented(
                    options = AutoChangerSettings.INTERVALS.map { hours -> if (hours == 24) stringResource(R.string.interval_day) else stringResource(R.string.interval_hours, hours) },
                    selectedIndex = AutoChangerSettings.INTERVALS.indexOf(settings.intervalHours),
                    onSelect = { i -> viewModel.update { it.copy(intervalHours = AutoChangerSettings.INTERVALS[i]) } },
                )
                SoftSectionLabel(stringResource(R.string.auto_changer_set_on), Modifier.padding(top = 8.dp))
                val targets = listOf(ApplyTarget.HOME to stringResource(R.string.target_home), ApplyTarget.LOCK to stringResource(R.string.target_lock), ApplyTarget.BOTH to stringResource(R.string.target_both))
                SoftSegmented(
                    options = targets.map { it.second },
                    selectedIndex = targets.indexOfFirst { it.first == settings.target },
                    onSelect = { i -> viewModel.update { it.copy(target = targets[i].first) } },
                )
                SoftSectionLabel(stringResource(R.string.auto_changer_pick_from), Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val sources = listOf(AutoChangerSettings.SOURCE_FAVORITES to stringResource(R.string.favourites_tab), AutoChangerSettings.SOURCE_FOR_YOU to stringResource(R.string.for_you)) +
                        categories.map { AutoChangerSettings.categorySource(it.slug) to it.name }
                    sources.forEach { (source, label) ->
                        SoftChip(label, selected = settings.source == source, onClick = { viewModel.update { it.copy(source = source) } })
                    }
                }
                AccentButton(stringResource(R.string.change_now), viewModel::changeNow, Modifier.fillMaxWidth().padding(top = 12.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.navigationBarsPadding())
    }
}
