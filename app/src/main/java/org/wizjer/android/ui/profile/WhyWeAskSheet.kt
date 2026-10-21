package org.wizjer.android.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.wizjer.android.R
import org.wizjer.android.ui.components.PrimaryButton
import org.wizjer.android.ui.components.BrandMark
import org.wizjer.android.ui.components.rememberWizjerMarkState

/** Explains why we ask for the name and date of birth. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhyWeAskSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val mark = rememberWizjerMarkState()

    LaunchedEffect(Unit) {
        delay(450)
        mark.blink()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.outlineVariant) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            BrandMark(Modifier.width(48.dp), state = mark)
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.why_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.why_body),
                style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton(
                text = stringResource(R.string.why_ok),
                showArrow = false,
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
