package com.wallpaperapp.app.data.account

import android.app.Activity
import com.wallpaperapp.app.R
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

/** Result of asking Android's Credential Manager for a Google ID token. */
sealed interface GoogleTokenResult {
    data class Token(val idToken: String, val rawNonce: String) : GoogleTokenResult
    data object Cancelled : GoogleTokenResult
    data class Error(val message: String) : GoogleTokenResult
}

/**
 * Shows the "Sign in with Google" account chooser and returns an ID token for Supabase.
 * Google receives SHA-256(nonce); Supabase receives the raw nonce and checks they match,
 * so a stolen ID token can't be replayed.
 */
object GoogleIdToken {

    suspend fun request(activity: Activity, webClientId: String): GoogleTokenResult {
        if (webClientId.isBlank()) return GoogleTokenResult.Error(activity.getString(R.string.google_not_configured))
        val rawNonce = newNonce()
        val option = GetSignInWithGoogleOption.Builder(webClientId).setNonce(sha256Hex(rawNonce)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
            } else {
                GoogleTokenResult.Error(activity.getString(R.string.google_unexpected))
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleTokenResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleTokenResult.Error(activity.getString(R.string.google_no_account))
        } catch (e: GetCredentialException) {
            GoogleTokenResult.Error(e.message ?: activity.getString(R.string.google_failed))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            GoogleTokenResult.Error(activity.getString(R.string.google_failed))
        }
    }

    internal fun newNonce(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    internal fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
