package com.wallpaperapp.app.ui.settings

import android.Manifest
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(private val preferences: UserPreferences) : ViewModel() {
    val newDrops: StateFlow<Boolean> = preferences.pushNewDrops.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val daily: StateFlow<Boolean> = preferences.pushDaily.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setNewDrops(on: Boolean) = viewModelScope.launch { preferences.setPushNewDrops(on) }
    fun setDaily(on: Boolean) = viewModelScope.launch { preferences.setPushDaily(on) }

    /** True the first time only: the permission is asked once, at a good moment. */
    suspend fun takePermissionAsk(): Boolean = preferences.takeNotificationPermissionAsk()
}

/** Profile → Notifications: the two topics users choose (CONTRACTS §8); "all" is always on. */
@Composable
fun NotificationSettings(viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val newDrops by viewModel.newDrops.collectAsStateWithLifecycle()
    val daily by viewModel.daily.collectAsStateWithLifecycle()
    val askPermission = rememberNotificationPermissionRequest(force = true)
    Column {
        Text(stringResource(R.string.notifications), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() })
        ListItem(
            headlineContent = { Text(stringResource(R.string.notif_new_drops)) },
            supportingContent = { Text(stringResource(R.string.notif_new_drops_body)) },
            trailingContent = {
                Switch(checked = newDrops, onCheckedChange = { on -> viewModel.setNewDrops(on); if (on) askPermission() })
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.notif_daily)) },
            supportingContent = { Text(stringResource(R.string.notif_daily_body)) },
            trailingContent = {
                Switch(checked = daily, onCheckedChange = { on -> viewModel.setDaily(on); if (on) askPermission() })
            },
        )
    }
}

/**
 * Returns a function that asks for POST_NOTIFICATIONS on Android 13+ if it isn't granted.
 * Without [force] it asks only the first time ever (used after the first download/apply);
 * with [force] it asks whenever the user turns a notification switch on.
 */
@Composable
fun rememberNotificationPermissionRequest(
    force: Boolean = false,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needed) {
            scope.launch {
                if (viewModel.takePermissionAsk() || force) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
