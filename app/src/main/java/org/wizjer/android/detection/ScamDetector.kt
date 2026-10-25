package org.wizjer.android.detection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wizjer.android.detection.lists.SiteIndex
import org.wizjer.android.detection.lists.SiteList
import org.wizjer.android.detection.model.ScamModel
import org.wizjer.android.protection.MessageSource
import kotlin.math.abs
import kotlin.math.roundToInt

/** Decides how likely a message is a scam. Used by background protection, the chat and the sandbox. */
interface ScamDetector {
    suspend fun analyze(input: DetectorInput): Analysis
}

/**
 * On-device detector: text clean-up, extraction, threat lists, rules, the AI model and scoring.
 */
class LocalScamDetector(
    /** The on-device AI model; null leaves the rules on their own (as in most tests). */
    private val model: () -> ScamModel? = { null },
    /** The downloaded threat lists. Read on every check, so a fresh download applies at once. */
    private val sites: () -> Collection<SiteIndex> = { emptyList() },
) : ScamDetector {

    override suspend fun analyze(input: DetectorInput): Analysis = withContext(Dispatchers.Default) { analyzeNow(input) }

    /** Same as [analyze], on the calling thread (for tests and background workers). */
    fun analyzeNow(input: DetectorInput): Analysis {
        val timings = mutableListOf<StageTiming>()
        fun <T> timed(stage: Stage, block: () -> T): T {
            val start = System.nanoTime()
            return block().also { timings += StageTiming(stage, (System.nanoTime() - start) / 1_000) }
        }

        // The sender (e.g. "InPost" as an SMS sender name) is analysed together with the text.
        val fullText = listOfNotNull(input.sender?.takeIf { it.isNotBlank() }, input.subject?.takeIf { it.isNotBlank() }, input.text).joinToString("\n")
        val normalized = timed(Stage.Normalize) { TextNormalizer.normalize(fullText) }
        val indexes = sites()
        val extraction = timed(Stage.Extract) { EntityExtractor.extract(normalized) { host -> indexes.any { it.listsFor(host).isNotEmpty() } } }
            .let { found -> if (indexes.isEmpty()) found else timed(Stage.Lists) { checkLists(found, indexes) } }
        val language = detectLanguage(normalized, extraction)
        val scorer = model()
        val reasons = timed(Stage.Rules) { RuleEngine.evaluate(normalized, extraction) } +
            listOfNotNull(scorer?.let { timed(Stage.Model) { modelReason(it, input, language, extraction) } })
        val verdict = timed(Stage.Fusion) {
            RiskFusion.combine(reasons, extraction, normalized.clean.length, language, actionable(input, extraction, reasons))
        }

        return Analysis(
            risk = verdict.risk,
            level = verdict.level,
            confidence = verdict.confidence,
            primaryType = verdict.primaryType,
            types = verdict.types,
            reasons = reasons.sortedByDescending { abs(it.contribution) },
            entities = entitiesOf(extraction),
            normalizedText = normalized.clean,
            language = language,
            timings = timings,
            detectorVersion = VERSION,
            modelUsed = scorer != null,
            listsUsed = indexes.isNotEmpty(),
        )
    }

    /**
     * The model's verdict as one piece of evidence, against its thresholds for this language and
     * channel. It reassures only about SMS, where it misses almost no scams; chat scams it misses
     * more often, so there it can add a warning but never talk the rules down. It can't see where
     * a short link or a bare IP address leads, so it never vouches for a message that hides one.
     */
    private fun modelReason(model: ScamModel, input: DetectorInput, language: String, extraction: Extraction): Reason? {
        // The model was trained on the message and its sender separately, not joined.
        val source = when (input.source) {
            MessageSource.Notification -> "notification"
            MessageSource.Email -> "email"
            MessageSource.Sms, null -> "sms"
        }
        val score = model.score(input.text, source, input.sender.orEmpty(), input.subject.orEmpty())
        val thresholds = model.thresholds(language, source) ?: return null
        val detail = "${(score * 100).roundToInt()}%"
        return when {
            score >= thresholds.scam -> Reason(Signal.ModelScam, detail)
            score >= thresholds.caution -> Reason(Signal.ModelSuspicious, detail)
            source == "sms" && score < thresholds.caution / 2 && extraction.links.none { it.isShortener || it.isIp } ->
                Reason(Signal.ModelOrdinary, detail)
            else -> null
        }
    }

    /**
     * Whether the message gives its reader something to act on: a link, a number to call or pay
     * (other than the sender's own), a request for a code or password, an app to install, a "safe
     * account", a fake buyer's lure or the "new number" story. A mail app's notification shows only
     * the start of an e-mail, hiding its links and real sender, so there a red flag the rules can
     * name (a tiny parcel fee, disguised text) counts too; without one, an alarming preview is
     * worth a "be careful", not a scam warning.
     */
    private fun actionable(input: DetectorInput, extraction: Extraction, reasons: List<Reason>): Boolean {
        if (extraction.links.isNotEmpty() || extraction.bankAccounts.isNotEmpty()) return true
        val sender = input.sender.orEmpty().filter(Char::isDigit).takeLast(9)
        if (extraction.phones.any { sender.length < 9 || !it.display.filter(Char::isDigit).endsWith(sender) }) return true
        val actions = if (input.source == MessageSource.Email) ACTIONS + PREVIEW_RED_FLAGS else ACTIONS
        return reasons.any { it.signal in actions }
    }

    /** Marks each link with the lists it's on; a listed link shortener counts as a short link. */
    private fun checkLists(extraction: Extraction, indexes: Collection<SiteIndex>): Extraction = extraction.copy(
        links = extraction.links.map { link ->
            val lists = indexes.flatMapTo(mutableSetOf()) { it.listsFor(link.host) }
            link.copy(lists = lists, isShortener = link.isShortener || SiteList.Shortener in lists)
        },
    )

    private fun entitiesOf(extraction: Extraction): List<Entity> = buildList {
        extraction.links.forEach { link ->
            val notes = buildList {
                if (link.impersonates != null) add(EntityNote.Lookalike)
                if (link.officialFor != null) add(EntityNote.Official)
                if (link.isShortener) add(EntityNote.ShortLink)
                if (link.isIp) add(EntityNote.IpHost)
                if (link.isPunycode) add(EntityNote.Punycode)
                if (link.hasRiskyTld) add(EntityNote.RiskyTld)
                if (link.explicitHttp) add(EntityNote.NoHttps)
                if (SiteList.Cert in link.lists) add(EntityNote.CertList)
                if (SiteList.Gambling in link.lists) add(EntityNote.Gambling)
                if (SiteList.Adult in link.lists) add(EntityNote.Adult)
                if (SiteList.Piracy in link.lists) add(EntityNote.Piracy)
            }
            add(Entity(EntityKind.Link, link.text, notes, (link.impersonates ?: link.officialFor)?.name))
        }
        extraction.phones.forEach { phone ->
            val notes = buildList {
                if (phone.isPremium) add(EntityNote.PremiumNumber)
                if (phone.isForeign) add(EntityNote.Foreign)
            }
            add(Entity(EntityKind.Phone, phone.display, notes))
        }
        extraction.bankAccounts.forEach { add(Entity(EntityKind.BankAccount, it)) }
        extraction.amounts.forEach { add(Entity(EntityKind.Amount, it)) }
        extraction.brands.forEach { add(Entity(EntityKind.Brand, it.name)) }
    }

    /**
     * Rough guess from the words outside links: Cyrillic → Ukrainian; Polish letters or common words
     * → Polish; otherwise English. A message that's little more than a link counts as Polish, the
     * language most messages here are in, rather than getting the stricter English thresholds.
     */
    private fun detectLanguage(normalized: NormalizedText, extraction: Extraction): String {
        val folded = extraction.links.fold(normalized.folded) { text, link -> text.replace(TextNormalizer.fold(link.text), " ") }
        val clean = extraction.links.fold(normalized.clean) { text, link -> text.replace(link.text, " ") }
        val letters = folded.count { it.isLetter() }
        val cyrillic = folded.count { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC }
        return when {
            cyrillic * 2 > letters.coerceAtLeast(1) -> "uk"
            letters < MIN_LETTERS || clean.any { it in POLISH_LETTERS } || PolishWords.containsMatchIn(folded) -> "pl"
            else -> "en"
        }
    }

    companion object {
        const val VERSION = "rules-0.2"

        private const val POLISH_LETTERS = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"

        /** Signals that are themselves something to act on (see [actionable]). */
        private val ACTIONS = setOf(
            Signal.Credentials, Signal.SafeAccount, Signal.RemoteApp, Signal.Family, Signal.Marketplace, Signal.PremiumNumber,
        )

        /** In an e-mail preview, where the link can't be seen, these are enough for a warning. */
        private val PREVIEW_RED_FLAGS = setOf(Signal.SmallFee, Signal.Obfuscation)

        /** Fewer letters than this outside links isn't enough to tell the language. */
        private const val MIN_LETTERS = 8

        /** Common Polish words, for texts typed without diacritics (matched on folded text). */
        private val PolishWords = Regex(
            "(?<!\\p{L})(?:jest|nie|sie|twoj|twoja|prosze|dzien|oraz|przez|ktory|zostala|zostal|jak|dla|czy|juz|tak|" +
                "zobacz|patrz|dzieki|jutro|dzisiaj|dzis|bede|mozesz|gdzie|kiedy|czemu|ale|mam|masz|mnie|sobie|tez|" +
                "wiem|chce|moze|teraz|zaraz|potem|bylo|bedzie|siema|czesc|hej)(?!\\p{L})",
        )

        /** Builds the phrase matchers ahead of time, so the first real check is fast too. */
        fun warmUp() {
            LocalScamDetector().analyzeNow(DetectorInput("Rozgrzewka: https://inpost.pl 123 456 789"))
        }
    }
}
