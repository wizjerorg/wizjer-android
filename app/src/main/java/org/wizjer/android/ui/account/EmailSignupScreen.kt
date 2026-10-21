package org.wizjer.android.ui.account

import android.util.Patterns
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.data.account.AccountSession
import org.wizjer.android.data.account.RegisterResult
import org.wizjer.android.ui.components.CheckIcon
import org.wizjer.android.ui.components.OnboardingStepLayout
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.TwoToneHeading
import org.wizjer.android.ui.components.UnderlineTextField

private const val MaxPasswordLength = 128

/** Password rules shown live while typing. */
private enum class PasswordRule(@StringRes val label: Int, val isMet: (String) -> Boolean) {
    Length(R.string.rule_length, { it.length >= 8 }),
    Case(R.string.rule_case, { p -> p.any(Char::isUpperCase) && p.any(Char::isLowerCase) }),
    DigitOrSymbol(R.string.rule_digit, { p -> p.any { !it.isLetter() && !it.isWhitespace() } }),
}

/** Account step, email path: email + password with live rules, then the account is created. */
@Composable
fun EmailSignupScreen(
    session: AccountSession,
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var emailTouched by rememberSaveable { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var serverError by remember { mutableStateOf<Int?>(null) }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordValid = PasswordRule.entries.all { it.isMet(password) }
    val canSubmit = emailValid && passwordValid && !submitting

    fun submit() {
        if (!emailValid) emailTouched = true
        if (!canSubmit) return
        focusManager.clearFocus()
        submitting = true
        serverError = null
        scope.launch {
            val result = session.register(email.trim(), password)
            submitting = false
            when (result) {
                RegisterResult.Success -> onRegistered()
                RegisterResult.EmailTaken -> serverError = R.string.signup_error_taken
                RegisterResult.Rejected -> serverError = R.string.signup_error_rejected
                RegisterResult.NoConnection -> serverError = R.string.signup_error_network
                RegisterResult.Failed -> serverError = R.string.signup_error_failed
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(450)
        emailFocus.requestFocus()
    }

    OnboardingStepLayout(
        step = 3,
        onBack = onBack,
        modifier = modifier,
        bottom = {
            AnimatedVisibility(
                visible = serverError != null,
                enter = fadeIn(tween(200)) + expandVertically(tween(250)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
            ) {
                var shown by remember { mutableStateOf(serverError) }
                if (serverError != null) shown = serverError
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
                text = stringResource(R.string.signup_submit),
                onClick = ::submit,
                enabled = emailValid && passwordValid,
                loading = submitting,
            )
        },
    ) {
        TwoToneHeading(
            lead = stringResource(R.string.signup_title_lead),
            rest = stringResource(R.string.signup_title_rest),
        )
        Spacer(Modifier.height(36.dp))
        UnderlineTextField(
            value = email,
            onValueChange = {
                email = it.trim()
                serverError = null
            },
            label = stringResource(R.string.signup_email),
            focusRequester = emailFocus,
            contentType = ContentType.EmailAddress,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            // Only complain once the user has left the field, not while they type.
            error = if (emailTouched && email.isNotEmpty() && !emailValid) stringResource(R.string.signup_email_invalid) else null,
            onFocusChange = { focused -> if (!focused && email.isNotEmpty()) emailTouched = true },
        )
        Spacer(Modifier.height(28.dp))
        UnderlineTextField(
            value = password,
            onValueChange = {
                password = it.take(MaxPasswordLength)
                serverError = null
            },
            label = stringResource(R.string.signup_password),
            focusRequester = passwordFocus,
            contentType = ContentType.NewPassword,
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
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PasswordRule.entries.forEach { rule ->
                RuleRow(stringResource(rule.label), met = rule.isMet(password))
            }
        }
    }
}

/** A rule with a ring that fills and pops a check mark when met. */
@Composable
private fun RuleRow(text: String, met: Boolean) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (met) colors.secondary else Color.Transparent, tween(250), label = "ruleFill")
    val ring by animateColorAsState(
        if (met) colors.secondary else colors.onSurfaceVariant.copy(alpha = 0.4f), tween(250), label = "ruleRing",
    )
    val textColor by animateColorAsState(
        if (met) colors.onBackground else colors.onSurfaceVariant, tween(250), label = "ruleText",
    )
    val checkScale by animateFloatAsState(
        if (met) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ruleCheck",
    )
    val state = stringResource(if (met) R.string.rule_met else R.string.rule_unmet)

    Row(
        Modifier.semantics(mergeDescendants = true) { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.5.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CheckIcon(
                colors.onSecondary,
                size = 14.dp,
                modifier = Modifier.graphicsLayer { scaleX = checkScale; scaleY = checkScale },
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}
