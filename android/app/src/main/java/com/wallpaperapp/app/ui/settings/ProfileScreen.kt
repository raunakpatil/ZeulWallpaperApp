package com.wallpaperapp.app.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.wallpaperapp.app.ui.intro.IntroPolicy
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import com.wallpaperapp.app.ui.theme.SoftScreenTitle
import com.wallpaperapp.app.ui.theme.SoftGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wallpaperapp.app.data.account.AccountUser
import com.wallpaperapp.app.data.account.AuthState

/** Public legal pages (A12). The privacy policy refers to the exact row labels used below. */
private const val LEGAL_SITE = "https://raunakpatil.github.io/wallpaper-app-site"
private const val PRIVACY_URL = "$LEGAL_SITE/privacy.html"
private const val TERMS_URL = "$LEGAL_SITE/terms.html"

@Composable
fun ProfileScreen(
    onGoPremium: () -> Unit,
    onStyles: () -> Unit,
    onSignIn: () -> Unit,
    onAutoChanger: () -> Unit,
    onUpload: () -> Unit,
    onMyUploads: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val privacyOptionsRequired by viewModel.privacyOptionsRequired.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val accountBusy by viewModel.accountBusy.collectAsStateWithLifecycle()
    val accountMessage by viewModel.accountMessage.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SoftScreenTitle(stringResource(R.string.nav_profile))
        // ---- account (B7)
        SoftGroup {
        when (val s = authState) {
            is AuthState.SignedIn -> {
                AccountRow(s.user)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sign_out)) },
                    supportingContent = { Text(stringResource(R.string.sign_out_body)) },
                    modifier = Modifier.clickable(enabled = !accountBusy, onClick = viewModel::signOut),
                )
            }
            AuthState.SignedOut -> ListItem(
                headlineContent = { Text(stringResource(R.string.sign_in)) },
                supportingContent = { Text(stringResource(R.string.sign_in_body)) },
                leadingContent = { Icon(Icons.Outlined.AccountCircle, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onSignIn),
            )
            AuthState.Loading, AuthState.Unavailable -> Unit
        }
        if (authState is AuthState.SignedIn) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.upload_title)) },
                supportingContent = { Text(stringResource(R.string.upload_sign_in_body)) },
                modifier = Modifier.clickable(onClick = onUpload),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.my_uploads)) },
                supportingContent = { Text(stringResource(R.string.my_uploads_body)) },
                modifier = Modifier.clickable(onClick = onMyUploads),
            )
        }
        accountMessage?.asString()?.let { msg ->
            ListItem(
                headlineContent = { Text(msg, style = MaterialTheme.typography.bodyMedium) },
                trailingContent = { TextButton(onClick = viewModel::dismissAccountMessage) { Text(stringResource(R.string.ok)) } },
            )
        }
        }

        // ---- plan
        SoftGroup {
        Text(
            text = if (isPremium) stringResource(R.string.plan_premium) else stringResource(R.string.plan_free),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        ListItem(
            headlineContent = { Text(if (isPremium) stringResource(R.string.manage_subscription) else stringResource(R.string.go_premium)) },
            supportingContent = { Text(if (isPremium) stringResource(R.string.manage_subscription_body) else stringResource(R.string.go_premium_body)) },
            modifier = Modifier.clickable(onClick = onGoPremium),
        )
        if (privacyOptionsRequired && activity != null) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.privacy_settings)) },
                supportingContent = { Text(stringResource(R.string.privacy_settings_body)) },
                modifier = Modifier.clickable { viewModel.showPrivacyOptions(activity) },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.your_styles)) },
            supportingContent = { Text(stringResource(R.string.your_styles_body)) },
            modifier = Modifier.clickable(onClick = onStyles),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.auto_changer)) },
            supportingContent = { Text(if (isPremium) stringResource(R.string.auto_changer_body) else stringResource(R.string.premium_perk)) },
            modifier = Modifier.clickable(onClick = onAutoChanger),
        )
        }
        SoftGroup { NotificationSettings() }
        SoftGroup { StartupAnimationSetting() }
        SoftGroup {
        ListItem(
            headlineContent = { Text(stringResource(R.string.privacy_policy)) },
            modifier = Modifier.clickable { uriHandler.openUri(PRIVACY_URL) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.terms)) },
            modifier = Modifier.clickable { uriHandler.openUri(TERMS_URL) },
        )
        if (authState is AuthState.SignedIn) {
            // Keep the Delete account label (R.string.delete_account): the privacy policy and Play listing point to it.
            ListItem(
                headlineContent = { Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text(stringResource(R.string.delete_account_body)) },
                modifier = Modifier.clickable(enabled = !accountBusy) { confirmDelete = true },
            )
        }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_account_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_account_confirm_body),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteAccount() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AccountRow(user: AccountUser) {
    ListItem(
        headlineContent = { Text(user.name ?: user.email ?: stringResource(R.string.signed_in)) },
        supportingContent = { if (user.name != null && user.email != null) Text(user.email) },
        leadingContent = {
            if (user.avatarUrl != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Icon(Icons.Outlined.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        },
    )
}

/** Boot intro switch (docs/design/boot-intro.md); read on the next cold start. */
@Composable
private fun StartupAnimationSetting() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(IntroPolicy.isEnabled(context)) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.startup_animation)) },
        supportingContent = { Text(stringResource(R.string.startup_animation_body)) },
        trailingContent = {
            Switch(checked = enabled, onCheckedChange = { enabled = it; IntroPolicy.setEnabled(context, it) })
        },
        modifier = Modifier.toggleable(value = enabled, role = Role.Switch) { enabled = it; IntroPolicy.setEnabled(context, it) },
    )
}
