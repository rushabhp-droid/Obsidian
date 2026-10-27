package com.obsidian.mobile.data

import com.obsidian.mobile.network.ApiClient
import com.obsidian.mobile.network.CodeSent
import com.obsidian.mobile.network.EmailRequest
import com.obsidian.mobile.network.GoogleRequest
import com.obsidian.mobile.network.LoginRequest
import com.obsidian.mobile.network.RegisterRequest
import com.obsidian.mobile.network.ResetPasswordRequest
import com.obsidian.mobile.network.ServerStatus
import com.obsidian.mobile.network.SignedIn
import com.obsidian.mobile.network.VerifyEmailRequest

/** Sign-up, sign-in and sign-out against /api/b2c/v1/auth. */
class AccountRepository(
    private val session: SessionStore,
    private val client: ApiClient,
    private val onSignedOut: () -> Unit,
) {

    suspend fun status(serverUrl: String = session.settings.value.serverUrl): ServerStatus =
        client.callOn(serverUrl) { status() }

    suspend fun register(name: String, email: String, password: String): CodeSent =
        client.call { register(RegisterRequest(name.trim(), email.trim(), password)) }

    suspend fun verifyEmail(email: String, code: String) = keep(client.call { verifyEmail(VerifyEmailRequest(email.trim(), code.trim())) })

    suspend fun resendCode(email: String): CodeSent = client.call { resendCode(EmailRequest(email.trim())) }

    suspend fun login(email: String, password: String) = keep(client.call { login(LoginRequest(email.trim(), password)) })

    suspend fun google(idToken: String) = keep(client.call { google(GoogleRequest(idToken)) })

    suspend fun forgotPassword(email: String): CodeSent = client.call { forgotPassword(EmailRequest(email.trim())) }

    suspend fun resetPassword(email: String, code: String, newPassword: String) =
        keep(client.call { resetPassword(ResetPasswordRequest(email.trim(), code.trim(), newPassword)) })

    suspend fun refreshProfile() {
        session.updateProfile(client.call { me() })
    }

    /** Revokes the token on the server when it can, then forgets everything on the phone. */
    suspend fun signOut() {
        runCatching { client.call { logout() } }
        session.signOut()
        onSignedOut()
    }

    private fun keep(signedIn: SignedIn): SignedIn {
        session.signedIn(signedIn.token, signedIn.consumer)
        return signedIn
    }
}
