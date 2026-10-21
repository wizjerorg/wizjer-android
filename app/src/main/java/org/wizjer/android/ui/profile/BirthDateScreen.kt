package org.wizjer.android.ui.profile

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.wizjer.android.R
import org.wizjer.android.data.account.ProfileStore
import org.wizjer.android.ui.components.InfoLink
import org.wizjer.android.ui.components.OnboardingStepLayout
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.TwoToneHeading
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period

/** Onboarding step 2: date of birth, greeting the user by the name from step 1. */
@Composable
fun BirthDateScreen(
    store: ProfileStore,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saved = remember { store.birthDate }
    var day by rememberSaveable { mutableStateOf(saved?.dayOfMonth?.toString()?.padStart(2, '0').orEmpty()) }
    var month by rememberSaveable { mutableStateOf(saved?.monthValue?.toString()?.padStart(2, '0').orEmpty()) }
    var year by rememberSaveable { mutableStateOf(saved?.year?.toString().orEmpty()) }
    var showWhy by rememberSaveable { mutableStateOf(false) }
    val dayFocus = remember { FocusRequester() }
    val name = remember { store.firstName }

    val check = checkBirthDate(day, month, year, LocalDate.now())

    fun submit() {
        if (check !is DateCheck.Valid) return
        store.birthDate = check.date
        onContinue()
    }

    LaunchedEffect(Unit) {
        delay(450)
        dayFocus.requestFocus()
    }

    OnboardingStepLayout(
        step = 1,
        onBack = onBack,
        modifier = modifier,
        bottom = {
            PrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = ::submit,
                enabled = check is DateCheck.Valid,
            )
        },
    ) {
        TwoToneHeading(
            lead = if (name.isNotBlank()) stringResource(R.string.birth_greeting, name) else "",
            rest = stringResource(R.string.birth_prompt),
        )
        Spacer(Modifier.height(44.dp))
        BirthDateField(
            day = day,
            month = month,
            year = year,
            onDayChange = { day = it },
            onMonthChange = { month = it },
            onYearChange = { year = it },
            onDone = ::submit,
            dayFocus = dayFocus,
        )
        Spacer(Modifier.height(16.dp))
        DateStatus(check)
        Spacer(Modifier.height(12.dp))
        InfoLink(stringResource(R.string.why_link), onClick = { showWhy = true })
    }

    if (showWhy) WhyWeAskSheet(onDismiss = { showWhy = false })
}

/** One quiet line under the date: the age once valid, or what's wrong with it. */
@Composable
private fun DateStatus(check: DateCheck) {
    val colors = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = check,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        contentKey = { it::class },
        label = "dateStatus",
    ) { state ->
        Box(Modifier.fillMaxWidth().height(24.dp)) {
            when (state) {
                is DateCheck.Valid -> Text(
                    pluralStringResource(R.plurals.age_years, state.age, state.age),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                is DateCheck.Invalid -> Text(
                    if (state.arg != null) stringResource(state.message, state.arg) else stringResource(state.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground,
                )
                DateCheck.Incomplete -> Unit
            }
        }
    }
}

private sealed interface DateCheck {
    data object Incomplete : DateCheck
    data class Valid(val date: LocalDate, val age: Int) : DateCheck
    data class Invalid(@StringRes val message: Int, val arg: Int? = null) : DateCheck
}

/** Youngest age allowed to use the app. */
private const val MinimumAge = 16

private fun checkBirthDate(day: String, month: String, year: String, today: LocalDate): DateCheck {
    if (day.isEmpty() || month.isEmpty() || year.length < 4) return DateCheck.Incomplete
    val date = try {
        LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    } catch (_: DateTimeException) {
        return DateCheck.Invalid(R.string.date_error_invalid)
    }
    val age = Period.between(date, today).years
    return when {
        date.isAfter(today) -> DateCheck.Invalid(R.string.date_error_future)
        date.year < 1900 -> DateCheck.Invalid(R.string.date_error_year)
        age < MinimumAge -> DateCheck.Invalid(R.string.date_error_too_young, MinimumAge)
        else -> DateCheck.Valid(date, age)
    }
}
