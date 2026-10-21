package org.wizjer.android.ui.profile

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.wizjer.android.R
import org.wizjer.android.data.account.ProfileStore
import org.wizjer.android.ui.components.InfoLink
import org.wizjer.android.ui.components.OnboardingStepLayout
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.TwoToneHeading
import org.wizjer.android.ui.components.UnderlineTextField
import java.util.Locale

/**
 * "jULIAN" → "Julian": first letter of each part upper case, the rest lower case.
 * Double names keep both parts capitalised: "anna maria" → "Anna Maria", "nowak-kowalska" → "Nowak-Kowalska".
 */
internal fun normalizeName(input: String): String =
    input.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.split('-').joinToString("-") { part ->
                part.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        }

/** Onboarding step 1: first name (required, used to greet the user) and optional last name. */
@Composable
fun NameScreen(
    store: ProfileStore,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var firstName by rememberSaveable { mutableStateOf(store.firstName) }
    var lastName by rememberSaveable { mutableStateOf(store.lastName) }
    var showWhy by rememberSaveable { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val lastFocus = remember { FocusRequester() }
    val canContinue = firstName.isNotBlank()

    fun submit() {
        if (!canContinue) return
        store.firstName = normalizeName(firstName)
        store.lastName = normalizeName(lastName)
        onContinue()
    }

    // Open the keyboard once the slide-in has settled.
    LaunchedEffect(Unit) {
        delay(450)
        firstFocus.requestFocus()
    }

    OnboardingStepLayout(
        step = 0,
        onBack = onBack,
        modifier = modifier,
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = ::submit,
                enabled = canContinue,
            )
        },
    ) {
        TwoToneHeading(
            lead = stringResource(R.string.name_title_lead),
            rest = stringResource(R.string.name_title_rest),
        )
        Spacer(Modifier.height(40.dp))
        UnderlineTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = stringResource(R.string.name_first),
            focusRequester = firstFocus,
            contentType = ContentType.PersonFirstName,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { lastFocus.requestFocus() }),
        )
        Spacer(Modifier.height(28.dp))
        UnderlineTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = stringResource(R.string.name_last),
            focusRequester = lastFocus,
            contentType = ContentType.PersonLastName,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        Spacer(Modifier.height(20.dp))
        InfoLink(stringResource(R.string.why_link), onClick = { showWhy = true })
    }

    if (showWhy) WhyWeAskSheet(onDismiss = { showWhy = false })
}
