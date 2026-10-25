package org.wizjer.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.wizjer.android.BuildConfig
import org.wizjer.android.data.account.AccountSession
import org.wizjer.android.data.account.RefreshResult
import org.wizjer.android.data.ai.Assistant
import org.wizjer.android.data.lists.ThreatListsWorker
import org.wizjer.android.data.model.ModelUpdateWorker
import org.wizjer.android.protection.AlertLog
import org.wizjer.android.ui.account.AccountScreen
import org.wizjer.android.ui.account.EmailCodeScreen
import org.wizjer.android.ui.account.EmailSignupScreen
import org.wizjer.android.ui.account.LoginScreen
import org.wizjer.android.ui.call.CallScreen
import org.wizjer.android.ui.components.EmphasizedEasing
import org.wizjer.android.ui.home.HomeScreen
import org.wizjer.android.ui.language.currentAppLanguage
import org.wizjer.android.ui.onboarding.WelcomeScreen
import org.wizjer.android.ui.permissions.PermissionsScreen
import org.wizjer.android.ui.profile.BirthDateScreen
import org.wizjer.android.ui.protection.FlaggedMessageScreen
import org.wizjer.android.ui.protection.ProtectionScreen
import org.wizjer.android.ui.profile.NameScreen
import org.wizjer.android.ui.sandbox.SandboxScreen
import org.wizjer.android.ui.settings.SettingsScreen

/**
 * Onboarding steps in order, ending in [Home]. New users go Welcome → Name → BirthDate →
 * Permissions → Account → EmailSignup → EmailCode; returning users go Welcome → Login →
 * (EmailCode if never confirmed) → Permissions.
 */
private enum class Step { Welcome, Login, Name, BirthDate, Permissions, Account, EmailSignup, EmailCode, Home }

/** Root of the UI: onboarding until the account is confirmed, then the main app. */
@Composable
fun WizjerApp(textScale: Float, onTextScaleChange: (Float) -> Unit) {
    val context = LocalContext.current
    val session = remember { AccountSession(context) }
    val profile = session.profile
    var step by rememberSaveable { mutableStateOf(if (profile.onboardingComplete) Step.Home else Step.Welcome) }
    // True when signing in to an existing account ("Mam już konto") rather than creating one.
    var returning by rememberSaveable { mutableStateOf(false) }
    val screens = rememberSaveableStateHolder()

    fun finishOnboarding() {
        profile.onboardingComplete = true
        step = Step.Home
    }

    /**
     * Back to the start. The saved warnings belong to the phone's protection, not the account, so only
     * logging out on purpose ([forgetAlerts]) deletes them; an expired session keeps them.
     */
    fun signOut(forgetAlerts: Boolean) {
        session.signOut()
        if (forgetAlerts) AlertLog.clear(context)
        Assistant.signOut(context)
        returning = false
        step = Step.Welcome
    }

    val back = {
        step = when (step) {
            Step.Login, Step.Name -> Step.Welcome
            Step.BirthDate -> Step.Name
            Step.Permissions -> if (returning) Step.Login else Step.BirthDate
            Step.Account -> Step.Permissions
            Step.EmailSignup -> Step.Account
            Step.EmailCode -> if (returning) Step.Login else Step.EmailSignup
            Step.Welcome, Step.Home -> step
        }
    }

    // On every launch, refresh the profile. If the session is no longer valid, start over;
    // when offline, keep using the saved profile.
    LaunchedEffect(Unit) {
        if (!profile.onboardingComplete) return@LaunchedEffect
        if (session.refresh() == RefreshResult.SignedOut) signOut(forgetAlerts = false)
    }

    BackHandler(enabled = step != Step.Welcome && step != Step.Home, onBack = back)

    AnimatedContent(
        targetState = step,
        transitionSpec = { slideBetween(forward = targetState > initialState, distanceDivisor = 6) },
        label = "onboarding",
    ) { current ->
        screens.SaveableStateProvider(current.name) {
            when (current) {
                Step.Welcome -> WelcomeScreen(
                    onGetStarted = {
                        returning = false
                        step = Step.Name
                    },
                    onLogin = {
                        returning = true
                        step = Step.Login
                    },
                )
                Step.Login -> LoginScreen(
                    session,
                    onBack = back,
                    onLoggedIn = { confirmed -> step = if (confirmed) Step.Permissions else Step.EmailCode },
                )
                Step.Name -> NameScreen(profile, onBack = back, onContinue = { step = Step.BirthDate })
                Step.BirthDate -> BirthDateScreen(profile, onBack = back, onContinue = { step = Step.Permissions })
                Step.Permissions -> PermissionsScreen(
                    onBack = back,
                    onContinue = { if (returning) finishOnboarding() else step = Step.Account },
                    onSkip = { if (returning) finishOnboarding() else step = Step.Account },
                )
                Step.Account -> AccountScreen(profile, onBack = back, onEmail = { step = Step.EmailSignup })
                Step.EmailSignup -> EmailSignupScreen(session, onBack = back, onRegistered = { step = Step.EmailCode })
                Step.EmailCode -> EmailCodeScreen(
                    session,
                    onBack = back,
                    onConfirmed = { if (returning) step = Step.Permissions else finishOnboarding() },
                )
                Step.Home -> MainShell(
                    session, textScale, onTextScaleChange,
                    onLogOut = { signOut(forgetAlerts = true) },
                    onSessionExpired = { signOut(forgetAlerts = false) },
                )
            }
        }
    }
}

/** Whether debug builds list the detector sandbox in the menu. */
private const val SHOW_SANDBOX = false

private enum class MainScreen { Chat, Call, Protection, Flagged, Sandbox, Settings }

/**
 * The app after onboarding: the chat, a voice call over it, protection and the messages it flagged,
 * settings and (debug) the sandbox.
 */
@Composable
private fun MainShell(
    session: AccountSession,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    onLogOut: () -> Unit,
    onSessionExpired: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(MainScreen.Chat) }
    // The flagged message on show, and where Back from it leads.
    var flaggedId by rememberSaveable { mutableIntStateOf(0) }
    var flaggedFrom by rememberSaveable { mutableStateOf(MainScreen.Chat) }
    val screens = rememberSaveableStateHolder()
    val context = LocalContext.current
    val assistant = remember { Assistant.get(context) }
    val language = currentAppLanguage().tag

    // Threat lists start downloading once onboarding is done, then refresh in the background;
    // newer AI models come from the wizjer-model releases.
    LaunchedEffect(Unit) {
        ThreatListsWorker.schedule(context)
        ModelUpdateWorker.schedule(context)
    }

    fun openFlagged(id: Int, from: MainScreen) {
        flaggedId = id
        flaggedFrom = from
        screen = MainScreen.Flagged
    }

    // A tapped scam alert opens its message, wherever the user was.
    val tappedAlert by AlertLog.opened.collectAsStateWithLifecycle()
    LaunchedEffect(tappedAlert) {
        tappedAlert?.let { id ->
            openFlagged(id, from = MainScreen.Chat)
            AlertLog.shown()
        }
    }

    BackHandler(enabled = screen != MainScreen.Chat) {
        screen = if (screen == MainScreen.Flagged) flaggedFrom else MainScreen.Chat
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState == MainScreen.Call || initialState == MainScreen.Call) {
                // The call rises out of the chat and sinks back into it.
                (fadeIn(tween(300)) + scaleIn(tween(400, easing = EmphasizedEasing), initialScale = 0.94f))
                    .togetherWith(fadeOut(tween(250)) + scaleOut(tween(300), targetScale = 0.97f))
            } else {
                slideBetween(forward = targetState > initialState, distanceDivisor = 5)
            }
        },
        label = "main",
    ) { current ->
        screens.SaveableStateProvider(current.name) {
            when (current) {
                MainScreen.Chat -> HomeScreen(
                    profile = session.profile,
                    assistant = assistant,
                    onOpenSettings = { screen = MainScreen.Settings },
                    onOpenProtection = { screen = MainScreen.Protection },
                    onStartCall = { screen = MainScreen.Call },
                    // An expired session or an unconfirmed e-mail: start over at sign-in.
                    onSignIn = onSessionExpired,
                    // A developer tool for testing the detector, hidden from the menu for now; set
                    // SHOW_SANDBOX to bring it back (debug builds only).
                    onOpenSandbox = if (BuildConfig.DEBUG && SHOW_SANDBOX) ({ screen = MainScreen.Sandbox }) else null,
                )
                MainScreen.Call -> CallScreen(onEnd = { screen = MainScreen.Chat })
                MainScreen.Protection -> ProtectionScreen(
                    senior = session.profile.isSenior,
                    onBack = { screen = MainScreen.Chat },
                    onOpenAlert = { openFlagged(it, from = MainScreen.Protection) },
                )
                MainScreen.Flagged -> FlaggedMessageScreen(
                    AlertLog.find(flaggedId),
                    senior = session.profile.isSenior,
                    onBack = { screen = flaggedFrom },
                    // A new chat that sends the message for an in-depth analysis, in the app's language.
                    onAskWizjer = { prompt ->
                        assistant.ask(prompt, language)
                        screen = MainScreen.Chat
                    },
                )
                MainScreen.Sandbox -> SandboxScreen(onBack = { screen = MainScreen.Chat })
                MainScreen.Settings -> SettingsScreen(
                    profile = session.profile,
                    textScale = textScale,
                    onTextScaleChange = onTextScaleChange,
                    onBack = { screen = MainScreen.Chat },
                    onLogout = onLogOut,
                )
            }
        }
    }
}

/** Short horizontal slide with a fade: forwards moves left, backwards moves right. */
private fun slideBetween(forward: Boolean, distanceDivisor: Int): ContentTransform {
    val enter = slideInHorizontally(tween(500, easing = EmphasizedEasing)) { w ->
        if (forward) w / distanceDivisor else -w / distanceDivisor
    } + fadeIn(tween(350, delayMillis = 100))
    val exit = slideOutHorizontally(tween(500, easing = EmphasizedEasing)) { w ->
        if (forward) -w / distanceDivisor else w / distanceDivisor
    } + fadeOut(tween(200))
    return enter togetherWith exit
}
