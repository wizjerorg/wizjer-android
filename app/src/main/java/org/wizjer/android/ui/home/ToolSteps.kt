package org.wizjer.android.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wizjer.android.R
import org.wizjer.android.data.ai.ToolStatus
import org.wizjer.android.data.ai.ToolStep
import org.wizjer.android.ui.components.AlertIcon
import org.wizjer.android.ui.components.BookIcon
import org.wizjer.android.ui.components.CheckIcon
import org.wizjer.android.ui.components.ChevronDownIcon
import org.wizjer.android.ui.components.GlobeIcon
import org.wizjer.android.ui.components.PersonIcon
import org.wizjer.android.ui.components.PhoneIcon
import org.wizjer.android.ui.components.SearchIcon

/**
 * What Wizjer checked while writing: a number, a website, the web, one of its guides. Shown as it
 * happens; once the reply is done, more than [OPEN_UP_TO] steps fold into one line to tap open.
 * A finding worth knowing (a number reported as a scam, a site on CERT's list) is in ink, the rest muted.
 */
@Composable
fun ToolSteps(steps: List<ToolStep>, replyDone: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val foldable = replyDone && steps.size > OPEN_UP_TO
    var open by rememberSaveable { mutableStateOf(false) }
    val expanded = !foldable || open

    Column(modifier.animateContentSize()) {
        if (foldable) {
            val turn by animateFloatAsState(if (open) 180f else 0f, label = "stepsChevron")
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { open = !open }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { CheckIcon(colors.secondary, size = 18.dp) }
                Spacer(Modifier.width(10.dp))
                Text(
                    pluralStringResource(R.plurals.ai_steps_count, steps.size, steps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                ChevronDownIcon(colors.onSurfaceVariant, Modifier.rotate(turn))
            }
        }
        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(
                Modifier.padding(top = if (foldable) 4.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                steps.forEach { StepRow(it) }
            }
        }
    }
}

private const val OPEN_UP_TO = 2

@Composable
private fun StepRow(step: ToolStep) {
    val colors = MaterialTheme.colorScheme
    val described = describe(step)
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when {
                step.status == ToolStatus.Running -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = colors.secondary,
                    trackColor = Color.Transparent,
                    strokeWidth = 1.8.dp,
                )
                step.status == ToolStatus.Error || described.alert -> AlertIcon(colors.onBackground)
                else -> described.icon(colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            described.text,
            style = MaterialTheme.typography.bodyMedium.let { if (described.alert) it.copy(fontWeight = FontWeight.Bold) else it },
            color = if (described.alert) colors.onBackground else colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

private class Described(val text: String, val icon: @Composable (Color) -> Unit, val alert: Boolean = false)

/** The step in plain words, in the app's language. Unknown kinds of steps get a neutral line. */
@Composable
private fun describe(step: ToolStep): Described {
    val summary = step.summary
    val running = step.status == ToolStatus.Running
    val failed = step.status == ToolStatus.Error
    return when (step.name) {
        "check_phone_number" -> {
            val number = formatNumber(summary.string("number") ?: step.input.string("number").orEmpty())
            val icon: @Composable (Color) -> Unit = { PhoneIcon(it) }
            when {
                running -> Described(stringResource(R.string.ai_step_phone_running, number), icon)
                failed -> Described(stringResource(R.string.ai_step_phone_failed, number), icon)
                summary?.get("valid") == false -> Described(stringResource(R.string.ai_step_phone_invalid), icon)
                summary?.get("found") != true -> Described(stringResource(R.string.ai_step_phone_unknown, number), icon)
                else -> {
                    val verdict = summary.string("verdict")
                    val label = stringResource(
                        when (verdict) {
                            "scam" -> R.string.ai_phone_verdict_scam
                            "spam" -> R.string.ai_phone_verdict_spam
                            "legit", "safe" -> R.string.ai_phone_verdict_safe
                            else -> R.string.ai_phone_verdict_unclear
                        }
                    )
                    val count = (summary["reportCount"] as? Number)?.toInt() ?: 0
                    val text = if (count > 0) {
                        stringResource(R.string.ai_step_phone_found, number, label, pluralStringResource(R.plurals.ai_phone_reports, count, count))
                    } else {
                        stringResource(R.string.ai_step_phone_found_plain, number, label)
                    }
                    Described(text, icon, alert = verdict == "scam" || verdict == "spam")
                }
            }
        }
        "check_domain" -> {
            val site = summary.string("domain") ?: step.input.string("value").orEmpty()
            val icon: @Composable (Color) -> Unit = { GlobeIcon(it) }
            when {
                running -> Described(stringResource(R.string.ai_step_site_running, site), icon)
                failed -> Described(stringResource(R.string.ai_step_site_failed, site), icon)
                summary?.get("valid") == false -> Described(stringResource(R.string.ai_step_site_invalid), icon)
                summary?.get("flagged") == true -> {
                    val lists = (summary["lists"] as? List<*>).orEmpty().filterIsInstance<String>().distinct()
                    val found = lists.map { stringResource(listLabel(it)) }.ifEmpty { listOf(stringResource(R.string.ai_list_other)) }
                    Described(stringResource(R.string.ai_step_site_flagged, site, found.joinToString(", ")), icon, alert = "cert" in lists)
                }
                else -> Described(stringResource(R.string.ai_step_site_clean, site), icon)
            }
        }
        "web_search" -> {
            val query = step.input.string("query").orEmpty()
            val icon: @Composable (Color) -> Unit = { SearchIcon(it) }
            when {
                running -> Described(stringResource(R.string.ai_step_search_running, query), icon)
                failed || summary?.containsKey("error") == true -> Described(stringResource(R.string.ai_step_search_failed), icon)
                else -> Described(stringResource(R.string.ai_step_search_done, query), icon)
            }
        }
        "load_skill" -> {
            val icon: @Composable (Color) -> Unit = { BookIcon(it) }
            val guide = guideLabel(summary.string("skill") ?: step.input.string("name"))
            when {
                running -> Described(stringResource(R.string.ai_step_guide_running), icon)
                failed -> Described(stringResource(R.string.ai_step_guide_failed), icon)
                guide != null -> Described(stringResource(R.string.ai_step_guide_done, stringResource(guide)), icon)
                else -> Described(stringResource(R.string.ai_step_guide_done_plain), icon)
            }
        }
        "get_account" -> {
            val icon: @Composable (Color) -> Unit = { PersonIcon(it) }
            when {
                running -> Described(stringResource(R.string.ai_step_account_running), icon)
                failed -> Described(stringResource(R.string.ai_step_account_failed), icon)
                else -> Described(stringResource(R.string.ai_step_account_done), icon)
            }
        }
        else -> {
            val icon: @Composable (Color) -> Unit = { CheckIcon(it, size = 16.dp) }
            when {
                running -> Described(stringResource(R.string.ai_step_other_running), icon)
                failed -> Described(stringResource(R.string.ai_step_other_failed), icon)
                else -> Described(stringResource(R.string.ai_step_other_done), icon)
            }
        }
    }
}

private fun listLabel(list: String) = when (list) {
    "cert" -> R.string.ai_list_cert
    "urlshortener" -> R.string.ai_list_shortener
    "gambling" -> R.string.ai_list_gambling
    "piracy" -> R.string.ai_list_piracy
    "nsfw" -> R.string.ai_list_adult
    else -> R.string.ai_list_other
}

private fun guideLabel(name: String?) = when (name) {
    "bank_impersonation" -> R.string.ai_guide_bank
    "fake_payment_messages" -> R.string.ai_guide_payments
    "investment_scams" -> R.string.ai_guide_investments
    "marketplace_scams" -> R.string.ai_guide_marketplace
    "report_scam" -> R.string.ai_guide_report
    else -> null
}

/** "+48605420169" → "+48 605 420 169"; anything else as it came. */
internal fun formatNumber(number: String): String {
    val digits = number.removePrefix("+48")
    return if (number.startsWith("+48") && digits.length == 9 && digits.all(Char::isDigit)) {
        "+48 ${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6)}"
    } else {
        number
    }
}

private fun Map<String, Any?>?.string(key: String): String? = (this?.get(key) as? String)?.takeIf { it.isNotBlank() }
