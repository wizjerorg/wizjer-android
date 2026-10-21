package org.wizjer.android.data.account

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import java.time.LocalDate

/**
 * The user's profile and onboarding state, kept on the device and refreshed from the API.
 * Reads are observable: a composable showing e.g. [firstName] updates when it changes.
 */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val version = mutableIntStateOf(0)

    var firstName: String
        get() = read { getString(KEY_FIRST_NAME, null).orEmpty() }
        set(value) = write { putString(KEY_FIRST_NAME, value) }

    var lastName: String
        get() = read { getString(KEY_LAST_NAME, null).orEmpty() }
        set(value) = write { putString(KEY_LAST_NAME, value) }

    var email: String
        get() = read { getString(KEY_EMAIL, null).orEmpty() }
        set(value) = write { putString(KEY_EMAIL, value) }

    /** Set once the account is created and its email confirmed; the app then opens straight to home. */
    var onboardingComplete: Boolean
        get() = read { getBoolean(KEY_ONBOARDING_COMPLETE, false) }
        set(value) = write { putBoolean(KEY_ONBOARDING_COMPLETE, value) }

    var birthDate: LocalDate?
        get() = read { getString(KEY_BIRTH_DATE, null)?.let(LocalDate::parse) }
        set(value) = write { putString(KEY_BIRTH_DATE, value?.toString()) }

    /** The server's guess from the first name; may be wrong, see [confidentGender]. */
    val gender: GenderGuess?
        get() = read {
            val name = getString(KEY_GENDER, null) ?: return@read null
            val gender = runCatching { Gender.valueOf(name) }.getOrNull() ?: return@read null
            GenderGuess(gender, getInt(KEY_GENDER_CONFIDENCE, 0))
        }

    /**
     * The guessed gender only when the server is confident enough to rely on it (e.g. for
     * gendered Polish/Ukrainian wording); otherwise null, so callers fall back to neutral text.
     */
    fun confidentGender(minConfidence: Int = 85): Gender? =
        gender?.takeIf { it.confidence >= minConfidence }?.gender

    /** Takes the server's copy of the account (fetched on every launch). */
    fun update(account: AccountData) = write {
        putString(KEY_FIRST_NAME, account.firstName)
        putString(KEY_LAST_NAME, account.lastName)
        putString(KEY_EMAIL, account.email.orEmpty())
        putString(KEY_BIRTH_DATE, account.birthDate?.toString())
        putString(KEY_GENDER, account.gender?.gender?.name)
        putInt(KEY_GENDER_CONFIDENCE, account.gender?.confidence ?: 0)
    }

    private inline fun <T> read(get: SharedPreferences.() -> T): T {
        version.intValue // registers the read so Compose recomposes on change
        return prefs.get()
    }

    private inline fun write(crossinline block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit { block() }
        version.intValue++
    }

    private companion object {
        const val KEY_FIRST_NAME = "first_name"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_BIRTH_DATE = "birth_date"
        const val KEY_EMAIL = "email"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_GENDER = "gender"
        const val KEY_GENDER_CONFIDENCE = "gender_confidence"
    }
}
