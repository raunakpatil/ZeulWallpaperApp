package com.wallpaperapp.app.ui.account

import com.wallpaperapp.app.ui.theme.softRaised
import com.wallpaperapp.app.ui.theme.ZeulIcons
import com.wallpaperapp.app.ui.theme.ZeulDisplay
import com.wallpaperapp.app.ui.theme.SoftTopBar
import com.wallpaperapp.app.ui.theme.SoftTextField
import com.wallpaperapp.app.ui.theme.SoftTextButton
import com.wallpaperapp.app.ui.theme.SoftButton
import com.wallpaperapp.app.ui.theme.LocalSoft
import com.wallpaperapp.app.ui.theme.AccentButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.activity.compose.LocalActivity
import com.wallpaperapp.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.wallpaperapp.app.data.account.GoogleIdToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Sign in (B7): Continue with Google always; the email-code option only when
 * `app_config.auth_email_enabled` is on. Closes itself once signed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(onDone: () -> Unit, viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val emailEnabled by viewModel.emailEnabled.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val uiScope = rememberCoroutineScope()

    LaunchedEffect(signedIn) { if (signedIn) onDone() }

    val soft = LocalSoft.current
    Column(Modifier.fillMaxSize().background(soft.base).statusBarsPadding().navigationBarsPadding()) {
        SoftTopBar(onDone, stringResource(R.string.back), title = stringResource(R.string.sign_in))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(112.dp).softRaised(CircleShape, depth = 8.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painterResource(R.drawable.boot_splash_icon),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(104.dp).clip(CircleShape).graphicsLayer { scaleX = 1.35f; scaleY = 1.35f },
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.sign_in_title),
                    fontFamily = ZeulDisplay,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = if (soft.isDark) FontWeight.Normal else FontWeight.Light,
                    color = soft.wordmark,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sign_in_explainer), style = MaterialTheme.typography.bodyMedium, color = soft.textMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))

                SoftButton(
                    text = stringResource(R.string.continue_google),
                    onClick = {
                        val act = activity ?: return@SoftButton
                        // Called from the screen's scope so the Activity is never held by a ViewModel coroutine.
                        uiScope.launch {
                            viewModel.onGoogleStarted()
                            try {
                                viewModel.onGoogleResult(GoogleIdToken.request(act, viewModel.googleWebClientId))
                            } catch (e: CancellationException) {
                                viewModel.onGoogleAborted()
                                throw e
                            }
                        }
                    },
                    enabled = !state.busy && activity != null,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (emailEnabled) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.dp).background(soft.textMuted.copy(alpha = 0.25f)))
                        Text(stringResource(R.string.or_separator), style = MaterialTheme.typography.labelMedium, color = soft.textMuted, modifier = Modifier.padding(horizontal = 12.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(soft.textMuted.copy(alpha = 0.25f)))
                    }
                    EmailCodeSection(state, viewModel)
                }

                if (state.busy) {
                    Spacer(Modifier.height(20.dp))
                    val signingIn = stringResource(R.string.signing_in)
                    CircularProgressIndicator(Modifier.size(28.dp).semantics { contentDescription = signingIn }, color = soft.accent)
                }
                state.message?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.sign_in_legal), style = MaterialTheme.typography.bodySmall, color = soft.textMuted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun EmailCodeSection(state: SignInUiState, viewModel: SignInViewModel) {
    val soft = LocalSoft.current
    val sentTo = state.codeSentTo
    if (sentTo == null) {
        SoftTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            placeholder = stringResource(R.string.email),
            enabled = !state.busy,
            leading = ZeulIcons.Mail,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { viewModel.sendCode() }),
        )
        Spacer(Modifier.height(14.dp))
        AccentButton(stringResource(R.string.email_me_code), viewModel::sendCode, Modifier.fillMaxWidth(), enabled = !state.busy && state.email.isNotBlank())
    } else {
        Text(stringResource(R.string.code_sent_to, sentTo), style = MaterialTheme.typography.bodyMedium, color = soft.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        SoftTextField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            placeholder = stringResource(R.string.code_placeholder),
            enabled = !state.busy,
            letterSpacing = 6f,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.verifyCode() }),
        )
        Spacer(Modifier.height(14.dp))
        AccentButton(stringResource(R.string.verify_sign_in), viewModel::verifyCode, Modifier.fillMaxWidth(), enabled = !state.busy && state.code.length == 6)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            SoftTextButton(stringResource(R.string.change_email), viewModel::changeEmail, enabled = !state.busy, color = soft.textMuted)
            SoftTextButton(
                if (state.resendInSeconds > 0) stringResource(R.string.resend_in, state.resendInSeconds) else stringResource(R.string.resend_code),
                viewModel::sendCode,
                enabled = !state.busy && state.resendInSeconds == 0,
            )
        }
    }
}
