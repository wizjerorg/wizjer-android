package org.wizjer.android.ui.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import org.wizjer.android.R

/**
 * Languages the app ships in. Until the user picks one, Android resolves the device language
 * against the bundled resources, so anything other than Polish or Ukrainian falls back to English.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    English("en", "English"),
    Polish("pl", "Polski"),
    Ukrainian("uk", "Українська");

    companion object {
        fun fromTag(tag: String): AppLanguage = entries.firstOrNull { it.tag == tag } ?: English

        /** Persists the choice (system per-app language on Android 13+) and recreates the activity. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}

/** The language the UI is currently shown in. */
@Composable
fun currentAppLanguage(): AppLanguage = AppLanguage.fromTag(stringResource(R.string.language_tag))
