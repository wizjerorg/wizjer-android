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
        val fullText = listOfNotNull(input.sender?.takeIf { it.isNotBlank() }, input.text).joinToString("\n")
        val normalized = timed(Stage.Normalize) { TextNormalizer.normalize(fullText) }
        val indexes = sites()
        val extraction = timed(Stage.Extract) { EntityExtractor.extract(normalized) }
            .let { found -> if (indexes.isEmpty()) found else timed(Stage.Lists) { checkLists(found, indexes) } }
        val language = detectLanguage(normalized)
        val scorer = model()
        val reasons = timed(Stage.Rules) { RuleEngine.evaluate(normalized, extraction) } +
            listOfNotNull(scorer?.let { timed(Stage.Model) { modelReason(it, input, language) } })
        val verdict = timed(Stage.Fusion) {
            RiskFusion.combine(reasons, extraction, normalized.clean.length, language)
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
     * more often, so there it can add a warning but never talk the rules down.
     */
    private fun modelReason(model: ScamModel, input: DetectorInput, language: String): Reason? {
        // The model was trained on the message and its sender separately, not joined.
        val source = when (input.source) {
            MessageSource.Notification -> "notification"
            MessageSource.Sms, null -> "sms"
        }
        val score = model.score(input.text, source, input.sender.orEmpty())
        val thresholds = model.thresholds(language, source) ?: return null
        val detail = "${(score * 100).roundToInt()}%"
        return when {
            score >= thresholds.scam -> Reason(Signal.ModelScam, detail)
            score >= thresholds.caution -> Reason(Signal.ModelSuspicious, detail)
            source == "sms" && score < thresholds.caution / 2 -> Reason(Signal.ModelOrdinary, detail)
            else -> null
        }
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

    /** Rough guess: Cyrillic → Ukrainian; Polish letters or common words → Polish; otherwise English. */
    private fun detectLanguage(normalized: NormalizedText): String {
        val folded = normalized.folded
        val letters = folded.count { it.isLetter() }.coerceAtLeast(1)
        val cyrillic = folded.count { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC }
        return when {
            cyrillic * 2 > letters -> "uk"
            normalized.clean.any { it in POLISH_LETTERS } || PolishWords.containsMatchIn(folded) -> "pl"
            else -> "en"
        }
    }

    companion object {
        const val VERSION = "rules-0.2"

        private const val POLISH_LETTERS = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"

        /** Common Polish words, for texts typed without diacritics (matched on folded text). */
        private val PolishWords = Regex(
            "(?<!\\p{L})(?:jest|nie|sie|twoj|twoja|prosze|dzien|oraz|przez|ktory|zostala|zostal|jak|dla|czy|juz|tak)(?!\\p{L})",
        )

        /** Builds the phrase matchers ahead of time, so the first real check is fast too. */
        fun warmUp() {
            LocalScamDetector().analyzeNow(DetectorInput("Rozgrzewka: https://inpost.pl 123 456 789"))
        }
    }
}
