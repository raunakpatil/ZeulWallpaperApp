package com.wallpaperapp.app.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.data.account.AuthRepository
import com.wallpaperapp.app.data.account.AuthResult
import com.wallpaperapp.app.data.account.AuthState
import com.wallpaperapp.app.data.account.GoogleTokenResult
import com.wallpaperapp.app.data.repository.AppConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUiState(
    val busy: Boolean = false,
    val email: String = "",
    val code: String = "",
    val codeSentTo: String? = null,
    val resendInSeconds: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
    appConfig: AppConfigRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {

    // The email step survives process death (re-sending would hit Supabase's rate limit).
    private val _state = MutableStateFlow(SignInUiState(email = saved[KEY_EMAIL] ?: "", codeSentTo = saved[KEY_SENT_TO]))
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    /** Email codes need custom SMTP (A17); the dashboard turns this on with `auth_email_enabled`. */
    val emailEnabled: StateFlow<Boolean> = appConfig.config.map { it.authEmailEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), appConfig.config.value.authEmailEnabled)

    /** The screen closes itself as soon as this turns true. */
    val signedIn: StateFlow<Boolean> = auth.state.map { it is AuthState.SignedIn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.state.value is AuthState.SignedIn)

    private var countdown: Job? = null

    val googleWebClientId: String get() = auth.googleWebClientId

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value.take(254), message = null) }
        saved[KEY_EMAIL] = _state.value.email
    }
    fun onCodeChange(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(6), message = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** The screen shows Google's sheet itself (it needs the Activity); these bracket that call. */
    fun onGoogleStarted() = _state.update { it.copy(busy = true, message = null) }
    fun onGoogleAborted() = _state.update { it.copy(busy = false) }

    fun onGoogleResult(result: GoogleTokenResult) {
        when (result) {
            GoogleTokenResult.Cancelled -> _state.update { it.copy(busy = false) }
            is GoogleTokenResult.Error -> _state.update { it.copy(busy = false, message = result.message) }
            is GoogleTokenResult.Token -> {
                _state.update { it.copy(busy = false) }
                run { auth.signInWithGoogleToken(result) }
            }
        }
    }

    fun sendCode() {
        val email = _state.value.email.trim()
        run(onSuccess = {
            _state.update { it.copy(codeSentTo = email, code = "") }
            saved[KEY_SENT_TO] = email
            startCountdown()
        }) { auth.sendEmailCode(email) }
    }

    fun verifyCode() {
        val email = _state.value.codeSentTo ?: return
        run { auth.verifyEmailCode(email, _state.value.code) }
    }

    fun changeEmail() {
        countdown?.cancel()
        saved[KEY_SENT_TO] = null
        _state.update { it.copy(codeSentTo = null, code = "", resendInSeconds = 0, message = null) }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (s in RESEND_AFTER_SECONDS downTo 0) {
                _state.update { it.copy(resendInSeconds = s) }
                if (s > 0) delay(1_000)
            }
        }
    }

    private fun run(onSuccess: () -> Unit = {}, block: suspend () -> AuthResult) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            when (val result = block()) {
                AuthResult.Success -> { _state.update { it.copy(busy = false) }; onSuccess() }
                AuthResult.Cancelled -> _state.update { it.copy(busy = false) }
                is AuthResult.Error -> _state.update { it.copy(busy = false, message = result.message) }
            }
        }
    }

    private companion object {
        const val RESEND_AFTER_SECONDS = 60
        const val KEY_EMAIL = "signin_email"
        const val KEY_SENT_TO = "signin_sent_to"
    }
}
