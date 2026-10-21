package org.wizjer.android.ui.language

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.ui.components.CheckIcon
import org.wizjer.android.ui.components.ChevronDownIcon

/** Small "Polski ⌄" control that opens the language sheet. */
@Composable
fun LanguageChip(language: AppLanguage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(language.nativeName, color = muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(6.dp))
        ChevronDownIcon(muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(current: AppLanguage, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) },
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            AppLanguage.entries.forEachIndexed { index, language ->
                if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable(role = Role.RadioButton) {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                                if (language != current) AppLanguage.apply(language)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(language.nativeName, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    if (language == current) CheckIcon(colors.onBackground)
                }
            }
        }
    }
}
