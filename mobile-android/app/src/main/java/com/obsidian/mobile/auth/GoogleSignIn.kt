package com.obsidian.mobile.auth

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.obsidian.mobile.BuildConfig
import java.security.MessageDigest

/**
 * "Continue with Google" through Credential Manager. Google issues an ID token
 * for the server's web OAuth client ([serverClientId], from GET /status); the
 * server checks it. Needs an Android OAuth client for this package and signing
 * key in the same Google Cloud project (README.md, "Android app").
 */
object GoogleSignIn {

    private const val TAG = "ObsidianGoogleSignIn"

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
            // Also what Google reports when this app isn't registered for sign-in, so say both.
            Log.w(TAG, "No credential: ${e.type} ${e.message}")
            Result.Failed("Google didn't offer an account. Check that this phone has a Google account. " +
                registrationHint(activity))
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Sign-in failed: ${e.type} ${e.message}")
            val text = e.message.orEmpty()
            Result.Failed(if ("28444" in text || "developer console" in text.lowercase()) {
                "Google doesn't recognise this app yet. " + registrationHint(activity)
            } else {
                "Google sign-in isn't available: ${text.ifEmpty { e.type }}"
            })
        } catch (e: GoogleIdTokenParsingException) {
            Result.Failed("Google returned an answer the app couldn't read. Try again.")
        }
    }

    /**
     * What the server's Google Cloud project needs: an Android OAuth client for this package and
     * signing key. Debug builds name the exact SHA-1; a release build just points at the setup.
     */
    private fun registrationHint(context: Context): String {
        val sha1 = if (BuildConfig.DEBUG) signingSha1(context) else null
        return if (sha1 != null) {
            "If it does, the app isn't registered with Google: in the server's Google Cloud project add an " +
                "OAuth client of type Android for ${context.packageName} with SHA-1 $sha1."
        } else {
            "If it does, Google sign-in isn't set up for this app yet. Use email for now."
        }
    }

    /** SHA-1 of the certificate this APK is signed with, as Google Cloud shows it (AA:BB:…). */
    @Suppress("DEPRECATION")
    fun signingSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        } ?: return null
        MessageDigest.getInstance("SHA-1").digest(cert.toByteArray()).joinToString(":") { "%02X".format(it) }
    }.getOrNull()
}
