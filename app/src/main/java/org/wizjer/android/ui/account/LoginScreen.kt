package org.wizjer.android.ui.account

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.data.account.AccountSession
import org.wizjer.android.data.account.LoginResult
import org.wizjer.android.ui.components.OnboardingStepLayout
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.TwoToneHeading
import org.wizjer.android.ui.components.UnderlineTextField

/** Phone numbers: optional +, then 9 to 15 digits (spaces and dashes allowed while typing). */
private val PhonePattern = Regex("""^\+?\d{9,15}$""")

/** "Mam już konto": email or phone number + password, then the account data is loaded. */
@Composable
fun LoginScreen(
    session: AccountSession,
    onBack: () -> Unit,
    onLoggedIn: (confirmed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var identifierTouched by rememberSaveable { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val identifierFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    // Emails as typed (trimmed, lower case); phone numbers without spaces and dashes.
    val cleaned = identifier.trim().let { if ('@' in it) it.lowercase() else it.replace(Regex("[\\s-]"), "") }
    val identifierValid = Patterns.EMAIL_ADDRESS.matcher(cleaned).matches() || PhonePattern.matches(cleaned)
    val canSubmit = identifierValid && password.isNotEmpty() && !submitting

    fun submit() {
        if (!identifierValid) identifierTouched = true
        if (!canSubmit) return
        focusManager.clearFocus()
        submitting = true
        error = null
        scope.launch {
            when (val result = session.logIn(cleaned, password)) {
                is LoginResult.Success -> {
                    submitting = false
                    onLoggedIn(result.confirmed)
                }
                LoginResult.InvalidCredentials -> {
                    submitting = false
                    error = R.string.login_error_credentials
                    password = ""
                    passwordFocus.requestFocus()
                }
                LoginResult.NoConnection -> {
                    submitting = false
                    error = R.string.signup_error_network
                }
                LoginResult.Failed -> {
                    submitting = false
                    error = R.string.signup_error_failed
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(450)
        identifierFocus.requestFocus()
    }

    OnboardingStepLayout(
        step = -1,
        onBack = onBack,
        modifier = modifier,
        bottom = {
            AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
            ) {
                var shown by remember { mutableStateOf(error) }
                if (error != null) shown = error
                Text(
                    shown?.let { stringResource(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.login_submit),
                onClick = ::submit,
                enabled = identifierValid && password.isNotEmpty(),
                loading = submitting,
            )
        },
    ) {
        TwoToneHeading(
            lead = stringResource(R.string.login_title_lead),
            rest = stringResource(R.string.login_title_rest),
        )
        Spacer(Modifier.height(36.dp))
        UnderlineTextField(
            value = identifier,
            onValueChange = {
                identifier = it
                error = null
            },
            label = stringResource(R.string.login_identifier),
            focusRequester = identifierFocus,
            contentType = ContentType.EmailAddress + ContentType.PhoneNumber + ContentType.Username,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            error = if (identifierTouched && identifier.isNotBlank() && !identifierValid) {
                stringResource(R.string.login_identifier_invalid)
            } else {
                null
            },
            onFocusChange = { focused -> if (!focused && identifier.isNotBlank()) identifierTouched = true },
        )
        Spacer(Modifier.height(28.dp))
        UnderlineTextField(
            value = password,
            onValueChange = {
                password = it.take(128)
                error = null
            },
            label = stringResource(R.string.signup_password),
            focusRequester = passwordFocus,
            contentType = ContentType.Password,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { passwordVisible = !passwordVisible }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(if (passwordVisible) R.string.signup_hide else R.string.signup_show),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
