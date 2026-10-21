package org.wizjer.android.data.account

import android.content.Context

/**
 * The user's account on this device: signing up and in, email confirmation, keeping the
 * profile in sync, and signing out. Screens talk to this instead of the API directly.
 */
class AccountSession(context: Context) {
    private val appContext = context.applicationContext
    private val api = AccountApi()
    private val tokens = TokenStore(appContext)

    /** Name, email, birth date etc. — observable from Compose. */
    val profile = ProfileStore(appContext)

    val hasToken: Boolean get() = tokens.token != null

    /** Creates an email account from the onboarding profile; the user then confirms the email. */
    suspend fun register(email: String, password: String): RegisterResult {
        val registration = Registration(email, password, profile.firstName, profile.lastName, profile.birthDate)
        val (result, token) = api.register(registration)
        if (token != null) {
            tokens.save(token)
            profile.email = email
        }
        return result
    }

    suspend fun confirmEmail(code: String): ConfirmResult =
        tokens.token?.let { api.confirmEmail(it, code) } ?: ConfirmResult.Failed

    suspend fun resendEmailCode(): ResendResult =
        tokens.token?.let { api.resendEmailCode(it) } ?: ResendResult.Failed

    /** Signs in with an email or phone number, then loads the account's profile. */
    suspend fun logIn(identifier: String, password: String): LoginResult {
        val (result, token) = api.logIn(identifier, password)
        if (token != null) {
            tokens.save(token)
            if ('@' in identifier) profile.email = identifier
            refresh() // if this fails, the next launch tries again
        }
        return result
    }

    /** Reloads the profile from the server; called on every launch. */
    suspend fun refresh(): RefreshResult {
        val token = tokens.token ?: return RefreshResult.SignedOut
        val result = api.fetchAccount(token)
        if (result is RefreshResult.Success) profile.update(result.account)
        return result
    }

    /** Forgets the session on this device; onboarding starts again. */
    fun signOut() {
        tokens.clear()
        profile.onboardingComplete = false
    }
}
