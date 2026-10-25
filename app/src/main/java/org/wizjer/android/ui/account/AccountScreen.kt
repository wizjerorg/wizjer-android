package org.wizjer.android.ui.account

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.wizjer.android.R
import org.wizjer.android.ui.components.Avatar
import org.wizjer.android.data.account.ProfileStore
import org.wizjer.android.ui.components.MailIcon
import org.wizjer.android.ui.components.OnboardingStepLayout
import org.wizjer.android.ui.components.TwoToneHeading

/** Last onboarding step: create an account (email for now; phone comes later). */
@Composable
fun AccountScreen(
    store: ProfileStore,
    onBack: () -> Unit,
    onEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val firstName = store.firstName
    val lastName = store.lastName

    OnboardingStepLayout(
        step = 3,
        onBack = onBack,
        modifier = modifier,
        bottom = {
            AuthButton(
                text = stringResource(R.string.account_email),
                onClick = onEmail,
                filled = true,
                icon = { MailIcon(colors.onPrimary) },
            )
            // Phone-number sign-up will be added here once it exists.
            Spacer(Modifier.height(16.dp))
            LegalNote()
        },
    ) {
        TwoToneHeading(
            lead = stringResource(R.string.account_title_lead),
            rest = stringResource(R.string.account_title_rest),
        )
        Spacer(Modifier.height(32.dp))
        if (firstName.isNotBlank()) AccountPreview(firstName, lastName)
    }
}

/** A glimpse of the account being created, built from the name entered earlier. */
@Composable
private fun AccountPreview(firstName: String, lastName: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The same face the account will have everywhere else in the app.
        Avatar(firstName, lastName, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.account_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** Pill with the icon pinned left and the label centred. */
@Composable
private fun AuthButton(
    text: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    filled: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "authScale",
    )
    val content = if (filled) colors.onPrimary else colors.onBackground

    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(colors.primary)
                else Modifier.border(1.dp, colors.onBackground.copy(alpha = 0.15f), CircleShape)
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = content),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.align(Alignment.CenterStart).size(20.dp), contentAlignment = Alignment.Center) { icon() }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

internal const val TermsUrl = "https://wizjer.org/regulamin"
internal const val PrivacyUrl = "https://wizjer.org/prywatnosc"

/** "By continuing, you accept the Terms and Privacy Policy"; the links open in the browser. */
@Composable
private fun LegalNote() {
    val colors = MaterialTheme.colorScheme
    val terms = stringResource(R.string.account_terms)
    val privacy = stringResource(R.string.account_privacy)
    val template = stringResource(R.string.account_legal, "\u0000", "\u0001")
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))

    val text = buildAnnotatedString {
        template.forEach { char ->
            when (char) {
                '\u0000' -> withLink(LinkAnnotation.Url(TermsUrl, linkStyle)) { append(terms) }
                '\u0001' -> withLink(LinkAnnotation.Url(PrivacyUrl, linkStyle)) { append(privacy) }
                else -> append(char)
            }
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}
