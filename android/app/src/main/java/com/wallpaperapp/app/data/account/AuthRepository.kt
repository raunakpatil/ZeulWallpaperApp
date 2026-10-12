package com.wallpaperapp.app.data.account

import android.app.Activity
import com.wallpaperapp.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.util.Log
import com.wallpaperapp.app.BuildConfig
import com.wallpaperapp.app.di.ApplicationScope
import com.wallpaperapp.app.di.DataModule
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class AccountUser(val id: String, val email: String?, val name: String?, val avatarUrl: String?)

sealed interface AuthState {
    /** Restoring a saved session at startup. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: AccountUser) : AuthState
    /** Fake backend (no Supabase keys): accounts can't work, so the UI hides them. */
    data object Unavailable : AuthState
}

sealed interface AuthResult {
    data object Success : AuthResult
    data object Cancelled : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Accounts (B7, built by Dev A): Google via Credential Manager and 6-digit email codes, on Supabase Auth.
 * The session is saved by supabase-kt and restored on the next launch. See CONTRACTS section 5.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AuthRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val supabase: Provider<SupabaseClient>,
    @ApplicationScope scope: CoroutineScope,
) {
    val available: Boolean = !DataModule.useFakeBackend

    val state: StateFlow<AuthState> =
        if (!available) MutableStateFlow(AuthState.Unavailable)
        else supabase.get().auth.sessionStatus
            .mapLatest { status ->
                when (status) {
                    is SessionStatus.Authenticated -> status.session.user?.let { AuthState.SignedIn(it.toAccountUser()) }
                        ?: AuthState.SignedOut
                    is SessionStatus.NotAuthenticated -> AuthState.SignedOut
                    // Temporary (offline/server hiccup): supabase-kt keeps the session and retries, so stay signed in.
                    is SessionStatus.RefreshFailure -> supabase.get().auth.sessionManager.loadSessionOrNull()?.user?.let { AuthState.SignedIn(it.toAccountUser()) }
                        ?: AuthState.SignedOut
                    SessionStatus.Initializing -> AuthState.Loading
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    val currentUser: AccountUser? get() = (state.value as? AuthState.SignedIn)?.user

    suspend fun signInWithGoogle(activity: Activity): AuthResult {
        if (!available) return AuthResult.Error(context.getString(R.string.auth_unavailable))
        return when (val token = GoogleIdToken.request(activity, BuildConfig.GOOGLE_WEB_CLIENT_ID)) {
            GoogleTokenResult.Cancelled -> AuthResult.Cancelled
            is GoogleTokenResult.Error -> AuthResult.Error(token.message)
            is GoogleTokenResult.Token -> signInWithGoogleToken(token)
        }
    }

    /** Second half of Google sign-in, for callers that obtained the token themselves (UI scope). */
    suspend fun signInWithGoogleToken(token: GoogleTokenResult.Token): AuthResult {
        if (!available) return AuthResult.Error(context.getString(R.string.auth_unavailable))
        return attempt("Google sign-in") {
            supabase.get().auth.signInWith(IDToken) {
                idToken = token.idToken
                provider = Google
                nonce = token.rawNonce
            }
        }
    }

    val googleWebClientId: String get() = BuildConfig.GOOGLE_WEB_CLIENT_ID

    /** Emails a 6-digit code (creates the account on first use). */
    suspend fun sendEmailCode(email: String): AuthResult {
        if (!available) return AuthResult.Error(context.getString(R.string.auth_unavailable))
        val clean = email.trim()
        if (!EMAIL_RE.matches(clean)) return AuthResult.Error(context.getString(R.string.auth_bad_email))
        return attempt("Sending the code") {
            supabase.get().auth.signInWith(OTP) {
                this.email = clean
                createUser = true
            }
        }
    }

    suspend fun verifyEmailCode(email: String, code: String): AuthResult {
        if (!available) return AuthResult.Error(context.getString(R.string.auth_unavailable))
        val digits = code.filter(Char::isDigit)
        if (digits.length != 6) return AuthResult.Error(context.getString(R.string.auth_code_length))
        return attempt("Checking the code") {
            supabase.get().auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = email.trim(), token = digits)
        }
    }

    /** Always ends signed out on this device, even offline (supabase-kt only clears the session after /logout). */
    suspend fun signOut() {
        if (!available) return
        val auth = supabase.get().auth
        try {
            auth.signOut()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Server sign-out failed (offline?), clearing the session on this device: ${e.message}")
        }
        if (auth.currentSessionOrNull() != null) auth.clearSession()
    }

    /** CONTRACTS section 2: `delete_my_account` removes the auth user; profile, favourites, unlocks cascade. */
    suspend fun deleteAccount(): AuthResult {
        if (!available || currentUser == null) return AuthResult.Error(context.getString(R.string.auth_not_signed_in))
        return attempt("Deleting your account") {
            supabase.get().postgrest.rpc("delete_my_account")
            // The server user is gone; clear the session locally without another network call.
            supabase.get().auth.clearSession()
        }
    }

    fun notSignedInMessage(): String = context.getString(R.string.auth_not_signed_in)

    private suspend fun attempt(what: String, block: suspend () -> Unit): AuthResult = try {
        block()
        AuthResult.Success
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.w(TAG, "$what failed", e)
        AuthResult.Error(context.getString(friendlyRes(e.message)))
    }

    private companion object {
        const val TAG = "AuthRepository"
        val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** Which friendly message fits a raw Supabase error (matched on the raw English error text). */
        fun friendlyRes(raw: String?): Int {
            val m = raw.orEmpty().lowercase()
            return when {
                "expired" in m || ("invalid" in m && ("otp" in m || "token" in m)) -> R.string.auth_code_wrong
                "rate limit" in m || "too many" in m || "security purposes" in m -> R.string.auth_too_many
                "network" in m || "unable to resolve" in m || "timeout" in m || "failed to connect" in m -> R.string.auth_no_connection
                else -> R.string.auth_generic_error
            }
        }
    }
}

internal fun UserInfo.toAccountUser(): AccountUser {
    fun meta(vararg keys: String) = keys.firstNotNullOfOrNull { (userMetadata?.get(it) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
    return AccountUser(
        id = id,
        email = email,
        name = meta("full_name", "name"),
        avatarUrl = meta("avatar_url", "picture"),
    )
}
