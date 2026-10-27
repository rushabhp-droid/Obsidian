package com.obsidian.mobile.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * "Continue with Google" through Credential Manager. Google issues an ID token
 * for the server's web OAuth client ([serverClientId], from GET /status); the
 * server checks it. Needs an Android OAuth client for this package and signing
 * key in the same Google Cloud project (README.md, "Android app").
 */
object GoogleSignIn {

    sealed interface Result {
        data class Token(val idToken: String) : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    suspend fun requestIdToken(activity: Activity, serverClientId: String): Result {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.Failed("Google didn't return an account.")
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Result.Failed("No Google account on this phone. Add one in Settings, or use email.")
        } catch (e: GetCredentialException) {
            Result.Failed("Google sign-in isn't available: ${e.message ?: e.type}")
        }
    }
}
