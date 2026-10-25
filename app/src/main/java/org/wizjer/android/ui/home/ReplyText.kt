package org.wizjer.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.wizjer.android.R
import org.wizjer.android.ui.components.CopyIcon
import org.wizjer.android.ui.components.ExternalIcon
import org.wizjer.android.ui.components.IconCircleButton
import java.net.URI

/** A run of reply text, bold or not; with [url], a link showing [text] as its title; [code]: `inline code`. */
data class Span(val text: String, val bold: Boolean, val url: String? = null, val code: Boolean = false)

/** A block of a reply, as the assistant writes them: a small Markdown subset. */
sealed interface ReplyBlock {
    /** The bold line a reply opens with when the user asked about something ("This is probably a scam."). */
    data class Verdict(val spans: List<Span>) : ReplyBlock
    data class Paragraph(val spans: List<Span>) : ReplyBlock
    data class Bullet(val spans: List<Span>) : ReplyBlock
    data class Numbered(val number: String, val spans: List<Span>) : ReplyBlock

    /** A fenced ``` block, as written; [language] is what follows the opening fence, often nothing. */
    data class Code(val language: String, val text: String) : ReplyBlock
}

/**
 * Splits a reply into blocks: paragraphs (a blank line between them), `-` and `1.` list items, ```
 * code blocks, `**bold**`, `inline code` and `[title](https://…)` links. A bold run or code block still
 * open (the reply is being written) runs to the end; a lone `*` or a half-typed fence at the very end
 * isn't shown. Nothing else becomes a link: addresses and numbers in the text stay plain, as they're
 * often the scam's own.
 */
fun parseReply(text: String): List<ReplyBlock> {
    val source = text.replace("\r\n", "\n")
        .let { if (it.endsWith("*") && !it.endsWith("**")) it.dropLast(1) else it }
        .let { PARTIAL_FENCE.find(it)?.let { partial -> it.removeRange(partial.range) } ?: it }
    val blocks = mutableListOf<ReplyBlock>()
    val current = StringBuilder()
    var kind: String? = null // null (nothing open), "p", "-" or a list number
    var code: MutableList<String>? = null // lines of a ``` block being read
    var language = ""

    fun closeCode() {
        code?.let { lines -> blocks += ReplyBlock.Code(language, lines.joinToString("\n").trimIndent()) }
        code = null
    }

    fun flush() {
        val spans = spansOf(current.toString().trim())
        if (spans.any { it.text.isNotBlank() }) {
            blocks += when (kind) {
                "p" -> ReplyBlock.Paragraph(spans)
                "-" -> ReplyBlock.Bullet(spans)
                else -> ReplyBlock.Numbered(kind!!, spans)
            }
        }
        current.setLength(0)
        kind = null
    }

    for (line in source.split('\n')) {
        val open = code
        if (open != null) {
            if (CLOSING_FENCE.matches(line.trim())) closeCode() else open += line
            continue
        }
        FENCE.matchEntire(line)?.let { fence ->
            flush()
            code = mutableListOf()
            language = fence.groupValues[1]
            continue
        }
        if (line.isBlank()) {
            flush()
            continue
        }
        val bullet = BULLET.matchEntire(line)
        val numbered = NUMBERED.matchEntire(line)
        when {
            bullet != null -> {
                flush()
                kind = "-"
                current.append(bullet.groupValues[1])
            }
            numbered != null -> {
                flush()
                kind = numbered.groupValues[1]
                current.append(numbered.groupValues[2])
            }
            // An indented line goes on with the list item above it.
            kind != null && kind != "p" && line.first().isWhitespace() -> current.append(' ').append(line.trim())
            kind == "p" -> current.append('\n').append(line.trim())
            else -> {
                flush()
                kind = "p"
                current.append(line.trim())
            }
        }
    }
    flush()
    closeCode()
    return withVerdict(blocks)
}

/** A first paragraph that opens in bold: the bold part becomes the verdict, the rest a paragraph. */
private fun withVerdict(blocks: List<ReplyBlock>): List<ReplyBlock> {
    val first = blocks.firstOrNull() as? ReplyBlock.Paragraph ?: return blocks
    val lead = first.spans.takeWhile { it.bold }.toMutableList()
    if (lead.all { it.text.isBlank() }) return blocks
    lead[0] = lead[0].copy(text = lead[0].text.trimStart())
    lead[lead.lastIndex] = lead.last().copy(text = lead.last().text.trimEnd())
    val rest = first.spans.drop(lead.size).toMutableList()
    if (rest.isNotEmpty()) rest[0] = rest[0].copy(text = rest[0].text.trimStart())
    val remainder = rest.filter { it.text.isNotEmpty() }
    return buildList {
        add(ReplyBlock.Verdict(lead.filter { it.text.isNotEmpty() }))
        if (remainder.isNotEmpty()) add(ReplyBlock.Paragraph(remainder))
        addAll(blocks.drop(1))
    }
}

/**
 * Inline code first (nothing inside it is formatted), then bold, which can run across code, then links.
 * A bare "**" (a bold run just opening) gives no spans, so asterisks never flash while streaming.
 */
private fun spansOf(text: String): List<Span> {
    val spans = mutableListOf<Span>()
    var bold = false
    fun words(part: String) = part.split("**").forEachIndexed { index, piece ->
        if (index > 0) bold = !bold
        spans += linked(piece, bold)
    }
    var at = 0
    INLINE_CODE.findAll(text).forEach { match ->
        words(text.substring(at, match.range.first))
        spans += Span(match.groupValues[1], bold, code = true)
        at = match.range.last + 1
    }
    words(text.substring(at))
    return spans
}

/** [part] with its links taken out; a link to anything but a proper https address shows as its title only. */
private fun linked(part: String, bold: Boolean): List<Span> {
    val spans = mutableListOf<Span>()
    var at = 0
    LINK.findAll(part).forEach { match ->
        if (match.range.first > at) spans += Span(part.substring(at, match.range.first), bold)
        spans += Span(match.groupValues[1], bold, match.groupValues[2].takeIf(::isSafeLink))
        at = match.range.last + 1
    }
    if (at < part.length) spans += Span(part.substring(at), bold)
    return spans.filter { it.text.isNotEmpty() }
}

/** The server only sends https links to sites that aren't under discussion; the app checks the first part again. */
internal fun isSafeLink(url: String): Boolean =
    url.startsWith("https://") && runCatching { URI(url).host }.getOrNull()?.isNotBlank() == true

/** Where the links' Markdown sits in [text], so streaming never shows half of one. */
internal fun linkRanges(text: String): List<IntRange> = LINK.findAll(text).map { it.range }.toList()

private val LINK = Regex("""\[([^\]\n]+)]\(([^\s)]+)\)""")
private val INLINE_CODE = Regex("`([^`\n]+)`")
private val FENCE = Regex("""^\s*```\s*([\w+#.-]*)\s*$""")
private val CLOSING_FENCE = Regex("`{3,}")
private val PARTIAL_FENCE = Regex("""(^|\n)\s*`{1,2}$""")
private val BULLET = Regex("""^\s*[-*•]\s+(.*)$""")
private val NUMBERED = Regex("""^\s*(\d{1,3})[.)]\s+(.*)$""")

/**
 * The reply's text, formatted. Links look like small mint tags with an arrow; tapping one calls
 * [onOpenLink] (which asks before leaving the app). Selectable as a whole by the caller's SelectionContainer.
 */
@Composable
fun ReplyText(text: String, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val blocks = remember(text) { parseReply(text) }
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = colors.onSecondaryContainer, fontWeight = FontWeight.Bold),
        pressedStyle = SpanStyle(color = colors.secondary),
    )
    fun styled(spans: List<Span>) = annotated(spans, linkStyle, onOpenLink)

    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                val previous = blocks[index - 1]
                val listPair = previous.isListItem && block.isListItem
                Spacer(Modifier.height(if (previous is ReplyBlock.Verdict) 6.dp else if (listPair) 8.dp else 14.dp))
            }
            when (block) {
                is ReplyBlock.Verdict -> RichText(styled(block.spans), typography.titleMedium)
                is ReplyBlock.Paragraph -> RichText(styled(block.spans), typography.bodyLarge)
                is ReplyBlock.Bullet -> Row {
                    Box(Modifier.width(22.dp).padding(top = 10.dp)) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.secondary),
                        )
                    }
                    RichText(styled(block.spans), typography.bodyLarge)
                }
                is ReplyBlock.Numbered -> Row {
                    Text(
                        "${block.number}.",
                        style = typography.labelLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .widthIn(min = 22.dp)
                            .padding(top = 1.dp, end = 4.dp),
                    )
                    RichText(styled(block.spans), typography.bodyLarge)
                }
                is ReplyBlock.Code -> CodeBlock(block)
            }
        }
    }
}

/** A code block: a soft grey card in a monospaced font that scrolls sideways, with its language and a copy button. */
@Composable
private fun CodeBlock(block: ReplyBlock.Code) {
    val colors = MaterialTheme.colorScheme
    val copy = rememberCopy()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                block.language.ifEmpty { stringResource(R.string.ai_code) },
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconCircleButton(onClick = { copy(block.text) }, contentDescription = stringResource(R.string.ai_code_copy)) {
                CopyIcon(colors.onSurfaceVariant)
            }
        }
        Text(
            block.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp),
            color = colors.onBackground,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        )
    }
}

/**
 * Text whose links get a rounded mint tag drawn behind them, and inline code a grey one (one per
 * line they wrap onto).
 */
@Composable
private fun RichText(text: AnnotatedString, style: TextStyle) {
    val colors = MaterialTheme.colorScheme
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tags = remember(text) { text.getLinkAnnotations(0, text.length).map { it.start until it.end } }
    val codes = remember(text) { text.getStringAnnotations(CODE, 0, text.length).map { it.start until it.end } }
    val tag = colors.secondaryContainer
    val codeTag = colors.surfaceContainer
    val arrow = colors.onSecondaryContainer
    Text(
        text,
        style = style,
        color = colors.onBackground,
        inlineContent = if (tags.isEmpty()) emptyMap() else mapOf(
            ARROW to InlineTextContent(Placeholder(0.8.em, 0.8.em, PlaceholderVerticalAlign.TextCenter)) {
                ExternalIcon(arrow, Modifier.fillMaxSize())
            },
        ),
        onTextLayout = { layout = it },
        modifier = if (tags.isEmpty() && codes.isEmpty()) Modifier else Modifier.drawBehind {
            layout?.let { result ->
                codes.forEach { drawTag(result, it, codeTag) }
                tags.forEach { drawTag(result, it, tag) }
            }
        },
    )
}

private fun DrawScope.drawTag(layout: TextLayoutResult, range: IntRange, color: Color) {
    val length = layout.layoutInput.text.length
    if (range.isEmpty() || range.first >= length) return
    val last = minOf(range.last, length - 1)
    for (line in layout.getLineForOffset(range.first)..layout.getLineForOffset(last)) {
        val start = maxOf(range.first, layout.getLineStart(line))
        val end = minOf(last + 1, layout.getLineEnd(line, visibleEnd = true))
        if (end <= start) continue
        val left = layout.getBoundingBox(start).left
        val right = layout.getBoundingBox(end - 1).right
        val top = layout.getLineTop(line) + 2.dp.toPx()
        val bottom = layout.getLineBottom(line) - 2.dp.toPx()
        drawRoundRect(color, Offset(left, top), Size(right - left, bottom - top), CornerRadius(8.dp.toPx()))
    }
}

private val ReplyBlock.isListItem get() = this is ReplyBlock.Bullet || this is ReplyBlock.Numbered

private const val ARROW = "arrow"
private const val CODE = "code"
private const val NO_BREAK = "\u00A0"

/** No-break spaces pad a link's title inside its tag and keep the arrow with it. */
private fun annotated(spans: List<Span>, linkStyle: TextLinkStyles, onOpenLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val weight = if (span.bold) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle()
        val url = span.url
        if (url != null) {
            withLink(LinkAnnotation.Clickable(tag = url, styles = linkStyle) { onOpenLink(url) }) {
                append(NO_BREAK)
                append(span.text)
                append(NO_BREAK)
                appendInlineContent(ARROW, "↗")
                append(NO_BREAK)
            }
        } else if (span.code) {
            pushStringAnnotation(CODE, "")
            withStyle(weight.merge(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.88.em))) {
                append(NO_BREAK)
                append(span.text)
                append(NO_BREAK)
            }
            pop()
        } else {
            withStyle(weight) { append(span.text) }
        }
    }
}

/**
 * The reply as plain words: no asterisks, list items on their own lines. For copying, a link is its
 * title with the address after it; for reading aloud ([links] false), just the title.
 */
fun plainReply(text: String, links: Boolean = true): String = parseReply(text).joinToString("\n\n") { block ->
    fun words(spans: List<Span>) = spans.joinToString("") { if (links && it.url != null) "${it.text} (${it.url})" else it.text }
    when (block) {
        is ReplyBlock.Verdict -> words(block.spans)
        is ReplyBlock.Paragraph -> words(block.spans)
        is ReplyBlock.Bullet -> "• " + words(block.spans)
        is ReplyBlock.Numbered -> "${block.number}. " + words(block.spans)
        is ReplyBlock.Code -> block.text
    }
}.replace(Regex("\n\n(?=(• |\\d{1,3}\\. ))"), "\n")
