package org.wizjer.android.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wizjer.android.BuildConfig
import org.wizjer.android.R
import org.wizjer.android.data.account.ProfileStore
import org.wizjer.android.ui.account.PrivacyUrl
import org.wizjer.android.ui.account.TermsUrl
import org.wizjer.android.ui.components.Avatar
import org.wizjer.android.ui.components.ChevronLeftIcon
import org.wizjer.android.ui.components.ChevronRightIcon
import org.wizjer.android.ui.components.ConfirmSheet
import org.wizjer.android.ui.components.ExternalIcon
import org.wizjer.android.ui.components.IconCircleButton
import org.wizjer.android.ui.components.LogoutIcon
import org.wizjer.android.ui.language.LanguageSheet
import org.wizjer.android.ui.language.currentAppLanguage
import org.wizjer.android.ui.permissions.PermissionItems
import org.wizjer.android.ui.permissions.PermissionRow
import org.wizjer.android.ui.permissions.rememberPermissionsState
import kotlin.math.abs

/** Text sizes offered in settings; applied on top of the system font size. */
val TextScales = listOf(1f, 1.15f, 1.3f)
private val TextScaleLabels = listOf(R.string.text_size_normal, R.string.text_size_large, R.string.text_size_xlarge)

@Composable
fun SettingsScreen(
    profile: ProfileStore,
    textScale: Float,
    onTextScaleChange: (Float) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val permissions = rememberPermissionsState()
    val language = currentAppLanguage()
    val uriHandler = LocalUriHandler.current
    val firstName = profile.firstName
    val lastName = profile.lastName
    val email = profile.email
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(onBack, stringResource(R.string.action_back)) { ChevronLeftIcon(colors.onBackground) }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.surfaceContainer)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(firstName, lastName, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" "),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onBackground,
                    )
                    if (email.isNotBlank()) {
                        Text(
                            email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_protection))
            PermissionItems.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                PermissionRow(
                    item = item,
                    granted = permissions.granted[item.permission] == true,
                    onAllow = { permissions.request(item.permission) },
                )
            }

            SectionLabel(stringResource(R.string.settings_appearance))
            Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.titleSmall, color = colors.onBackground)
            Spacer(Modifier.height(12.dp))
            TextSizeSelector(textScale, onTextScaleChange)
            Spacer(Modifier.height(8.dp))
            SettingsRow(stringResource(R.string.language_title), value = language.nativeName, onClick = { showLanguages = true })

            SectionLabel(stringResource(R.string.settings_about))
            SettingsRow(
                stringResource(R.string.settings_terms),
                onClick = { uriHandler.openUri(TermsUrl) },
                trailing = { ExternalIcon(colors.onSurfaceVariant) },
            )
            SettingsRow(
                stringResource(R.string.settings_privacy),
                onClick = { uriHandler.openUri(PrivacyUrl) },
                trailing = { ExternalIcon(colors.onSurfaceVariant) },
            )
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )

            Spacer(Modifier.height(28.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { confirmLogout = true }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoutIcon(colors.onBackground)
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.settings_logout), style = MaterialTheme.typography.labelLarge, color = colors.onBackground)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showLanguages) LanguageSheet(current = language, onDismiss = { showLanguages = false })
    if (confirmLogout) {
        ConfirmSheet(
            title = stringResource(R.string.logout_title),
            body = stringResource(R.string.logout_body),
            confirmLabel = stringResource(R.string.settings_logout),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = onLogout,
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 32.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    trailing: @Composable () -> Unit = { ChevronRightIcon(MaterialTheme.colorScheme.onSurfaceVariant) },
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onBackground, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        trailing()
    }
}

/** Three "A"s growing in size; the chosen one turns black. Big targets, easy to read. */
@Composable
private fun TextSizeSelector(current: Float, onSelect: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainer)
            .padding(4.dp),
    ) {
        TextScales.forEachIndexed { index, scale ->
            val selected = abs(current - scale) < 0.01f
            val background by animateColorAsState(if (selected) colors.primary else Color.Transparent, tween(200), label = "sizeBg")
            val content by animateColorAsState(if (selected) colors.onPrimary else colors.onBackground, tween(200), label = "sizeFg")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(background)
                    .clickable(role = Role.RadioButton) { onSelect(scale) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Fixed-height box so the labels line up although the "A"s differ in size.
                Box(Modifier.height(32.dp), contentAlignment = Alignment.BottomCenter) {
                    Text("A", fontSize = (16 + index * 5).sp, fontWeight = FontWeight.SemiBold, color = content)
                }
                Text(stringResource(TextScaleLabels[index]), fontSize = 12.sp, color = content, maxLines = 1)
            }
        }
    }
}
