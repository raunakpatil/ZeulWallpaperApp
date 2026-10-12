package com.wallpaperapp.app.ui.settings

import android.app.Activity
import com.wallpaperapp.app.R
import com.wallpaperapp.app.ui.common.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wallpaperapp.app.ads.AdsManager
import com.wallpaperapp.app.billing.PremiumManager
import com.wallpaperapp.app.data.account.AccountSync
import com.wallpaperapp.app.data.account.AuthRepository
import com.wallpaperapp.app.data.account.AuthResult
import com.wallpaperapp.app.data.account.AuthState
import com.wallpaperapp.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    premiumManager: PremiumManager,
    private val adsManager: AdsManager,
    private val auth: AuthRepository,
    private val accountSync: AccountSync,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val isPremium: StateFlow<Boolean> = premiumManager.isPremium

    /** GDPR/UK users must be able to change their ad consent later (CONTRACTS section 5). */
    val privacyOptionsRequired: StateFlow<Boolean> = adsManager.privacyOptionsRequired

    /** B7: account row at the top of Profile. */
    val authState: StateFlow<AuthState> = auth.state

    private val _accountBusy = MutableStateFlow(false)
    val accountBusy: StateFlow<Boolean> = _accountBusy.asStateFlow()

    private val _accountMessage = MutableStateFlow<UiText?>(null)
    val accountMessage: StateFlow<UiText?> = _accountMessage.asStateFlow()

    init {
        // A message like "Signed out" shouldn't linger after signing back in.
        viewModelScope.launch { auth.state.collect { if (it is AuthState.SignedIn) _accountMessage.value = null } }
    }

    fun showPrivacyOptions(activity: Activity) = adsManager.showPrivacyOptions(activity)

    fun signOut() = busy {
        auth.signOut()
        _accountMessage.value = UiText.Res(R.string.signed_out_message)
    }

    /** Play policy: in-app account deletion. Removes the account and its data on this phone too. */
    fun deleteAccount() = busy {
        when (val r = accountSync.deleteAccount()) {
            AuthResult.Success -> _accountMessage.value = UiText.Res(R.string.account_deleted_message)
            is AuthResult.Error -> _accountMessage.value = UiText.Raw(r.message)
            AuthResult.Cancelled -> Unit
        }
    }

    fun dismissAccountMessage() { _accountMessage.value = null }

    /** App scope: leaving Profile mid-way must not cancel a sign-out or an account deletion. */
    private fun busy(block: suspend () -> Unit) {
        if (_accountBusy.value) return
        appScope.launch {
            _accountBusy.value = true
            try { block() } finally { _accountBusy.value = false }
        }
    }
}
