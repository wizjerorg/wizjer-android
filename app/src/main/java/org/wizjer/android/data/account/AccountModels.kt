package org.wizjer.android.data.account

import java.time.LocalDate

/** Details sent when creating an account; the profile comes from onboarding. */
data class Registration(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate?,
)

enum class Gender { Male, Female }

/** A guess of the user's gender from their first name, with its confidence (0–100). */
data class GenderGuess(val gender: Gender, val confidence: Int)

/** The account as stored on the server. */
data class AccountData(
    val email: String?,
    val phone: String?,
    val confirmed: Boolean,
    val firstName: String,
    val lastName: String,
    val birthDate: LocalDate?,
    val gender: GenderGuess?,
)

// Outcomes of each account action. NoConnection = no response (offline, timeout);
// Failed = an answer the app didn't expect.

sealed interface RegisterResult {
    data object Success : RegisterResult
    data object EmailTaken : RegisterResult
    data object Rejected : RegisterResult
    data object NoConnection : RegisterResult
    data object Failed : RegisterResult
}

sealed interface ConfirmResult {
    data object Confirmed : ConfirmResult
    data object WrongCode : ConfirmResult
    data object Expired : ConfirmResult
    data object TooManyAttempts : ConfirmResult
    data object NoConnection : ConfirmResult
    data object Failed : ConfirmResult
}

sealed interface ResendResult {
    data object Sent : ResendResult
    data object TooSoon : ResendResult
    data object NoConnection : ResendResult
    data object Failed : ResendResult
}

sealed interface LoginResult {
    /** [confirmed] is false when the email was never confirmed; a new code is on its way. */
    data class Success(val confirmed: Boolean) : LoginResult
    data object InvalidCredentials : LoginResult
    data object NoConnection : LoginResult
    data object Failed : LoginResult
}

sealed interface RefreshResult {
    data class Success(val account: AccountData) : RefreshResult
    /** The session is no longer valid (e.g. the account was deleted). */
    data object SignedOut : RefreshResult
    data object NoConnection : RefreshResult
    data object Failed : RefreshResult
}
