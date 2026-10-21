package org.wizjer.android.ui.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.data.account.AccountSession
import org.wizjer.android.data.account.ConfirmResult
import org.wizjer.android.data.account.ResendResult
import org.wizjer.android.ui.components.CodeField
import org.wizjer.android.ui.components.OnboardingStepLayout
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.TwoToneHeading

private const val CodeLength = 6
private const val ResendCooldownSeconds = 30

/** After email sign-up: enter the 6-digit code from the email to confirm the address. */
@Composable
fun EmailCodeScreen(
    session: AccountSession,
    onBack: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val email = session.profile.email
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val codeFocus = remember { FocusRequester() }

    var code by rememberSaveable { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var messageIsError by remember { mutableStateOf(true) }
    var shakeKey by remember { mutableIntStateOf(0) }
    // The first code was sent at sign-up, so start with the cooldown running.
    var resendAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() + ResendCooldownSeconds * 1000L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var resending by remember { mutableStateOf(false) }

    LaunchedEffect(resendAt) {
        while (System.currentTimeMillis() < resendAt) {
            now = System.currentTimeMillis()
            delay(250)
        }
        now = System.currentTimeMillis()
    }
    LaunchedEffect(Unit) {
        delay(450)
        codeFocus.requestFocus()
    }

    fun verify(entered: String) {
        if (entered.length != CodeLength || verifying) return
        verifying = true
        message = null
        scope.launch {
            val result = session.confirmEmail(entered)
            verifying = false
            if (result == ConfirmResult.Confirmed) {
                focusManager.clearFocus()
                onConfirmed()
                return@launch
            }
            messageIsError = true
            message = when (result) {
                ConfirmResult.WrongCode -> R.string.code_error_wrong
                ConfirmResult.Expired -> R.string.code_error_expired
                ConfirmResult.TooManyAttempts -> R.string.code_error_attempts
                ConfirmResult.NoConnection -> R.string.signup_error_network
                else -> R.string.signup_error_failed
            }
            if (result == ConfirmResult.WrongCode) {
                shakeKey++
                code = ""
                codeFocus.requestFocus()
            }
        }
    }

    fun resend() {
        if (resending) return
        resending = true
        scope.launch {
            val result = session.resendEmailCode()
            resending = false
            when (result) {
                ResendResult.Sent -> {
                    code = ""
                    messageIsError = false
                    message = R.string.code_resent
                    resendAt = System.currentTimeMillis() + ResendCooldownSeconds * 1000L
                    codeFocus.requestFocus()
                }
                ResendResult.TooSoon -> resendAt = System.currentTimeMillis() + ResendCooldownSeconds * 1000L
                ResendResult.NoConnection -> { messageIsError = true; message = R.string.signup_error_network }
                ResendResult.Failed -> { messageIsError = true; message = R.string.signup_error_failed }
            }
        }
    }

    OnboardingStepLayout(
        step = 3,
        onBack = onBack,
        modifier = modifier,
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.code_submit),
                onClick = { verify(code) },
                enabled = code.length == CodeLength,
                loading = verifying,
            )
        },
    ) {
        TwoToneHeading(
            lead = stringResource(R.string.code_title_lead),
            rest = stringResource(R.string.code_title_rest, email),
        )
        Spacer(Modifier.height(40.dp))
        CodeField(
            value = code,
            onValueChange = {
                code = it
                message = null
            },
            onComplete = ::verify, // submit as soon as the last digit lands
            length = CodeLength,
            focusRequester = codeFocus,
            enabled = !verifying,
            shakeKey = shakeKey,
        )
        Spacer(Modifier.height(16.dp))

        // One status line: an error, "new code sent", or the spam hint.
        AnimatedContent(
            targetState = message,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "codeMessage",
        ) { current ->
            Text(
                stringResource(current ?: R.string.code_spam_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = if (current != null && messageIsError) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        ResendLink(
            secondsLeft = ((resendAt - now + 999) / 1000).toInt().coerceAtLeast(0),
            onResend = ::resend,
        )
    }
}

@Composable
private fun ResendLink(secondsLeft: Int, onResend: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ready = secondsLeft == 0
    Box(
        Modifier
            .offset(x = (-10).dp)
            .clip(CircleShape)
            .clickable(enabled = ready, role = Role.Button, onClick = onResend)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            if (ready) stringResource(R.string.code_resend) else stringResource(R.string.code_resend_wait, secondsLeft),
            style = MaterialTheme.typography.labelMedium,
            color = if (ready) colors.onBackground else colors.onSurfaceVariant,
        )
    }
}
