package com.wallpaperapp.app.ui.common

import com.wallpaperapp.app.ui.theme.AccentButton
import android.app.Activity
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.wallpaperapp.app.BuildConfig
import com.wallpaperapp.app.data.local.UserPreferences
import com.wallpaperapp.app.data.repository.AppConfigRepository
import com.wallpaperapp.app.domain.model.AppConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the whole app shows, in priority order. */
sealed interface AppGate {
    data object Loading : AppGate
    data class Maintenance(val message: String) : AppGate
    data object UpdateRequired : AppGate
    data object Onboarding : AppGate
    data object Ready : AppGate

    companion object {
        /** Maintenance beats everything; a too-old app must update before anything else. */
        fun decide(config: AppConfig, versionCode: Int, onboardingDone: Boolean?): AppGate = when {
            config.maintenance.enabled -> Maintenance(config.maintenance.message)
            versionCode < config.minAppVersion -> UpdateRequired
            onboardingDone == null -> Loading
            !onboardingDone -> Onboarding
            else -> Ready
        }
    }
}

@HiltViewModel
class AppGateViewModel @Inject constructor(
    preferences: UserPreferences,
    private val appConfig: AppConfigRepository,
) : ViewModel() {

    val gate: StateFlow<AppGate> = combine(appConfig.config, preferences.onboardingDone) { config, done ->
        AppGate.decide(config, BuildConfig.VERSION_CODE, done)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppGate.Loading)

    fun retry() {
        viewModelScope.launch { appConfig.refresh() }
    }
}

@Composable
fun MaintenanceScreen(message: String, onRetry: () -> Unit) {
    GateMessage(
        title = stringResource(R.string.maintenance_title),
        body = message.ifBlank { stringResource(R.string.maintenance_body) },
        action = stringResource(R.string.try_again),
        onAction = onRetry,
        icon = { Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(48.dp)) },
    )
}

@Composable
fun UpdateRequiredScreen(activity: Activity?) {
    GateMessage(
        title = stringResource(R.string.update_title),
        body = stringResource(R.string.update_body),
        action = stringResource(R.string.update),
        onAction = { activity?.let(::startUpdate) },
        icon = { Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(48.dp)) },
    )
}

/** Play's immediate in-app update when available; otherwise the store listing (sideloads, debug builds). */
private fun startUpdate(activity: Activity) {
    val manager = AppUpdateManagerFactory.create(activity)
    manager.appUpdateInfo
        .addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                manager.startUpdateFlow(info, activity, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))
            } else {
                openStoreListing(activity)
            }
        }
        .addOnFailureListener {
            Log.w("AppGate", "in-app update unavailable", it)
            openStoreListing(activity)
        }
}

private fun openStoreListing(context: Context) {
    val pkg = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
    }
}

@Composable
private fun GateMessage(title: String, body: String, action: String, onAction: () -> Unit, icon: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            icon()
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            AccentButton(action, onAction)
        }
    }
}
