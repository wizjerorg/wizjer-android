package org.wizjer.android.detection

import org.wizjer.android.detection.lists.SiteList
import java.util.Locale

/** A link found in a message, with what we could tell about it without going online. */
data class FoundLink(
    val text: String,
    val host: String,
    /** e.g. "inpost.pl" for "https://sledzenie.inpost.pl/…"; for IPs, the IP itself. */
    val registrableDomain: String,
    val explicitHttp: Boolean,
    val isIp: Boolean,
    val isPunycode: Boolean,
    val isShortener: Boolean,
    val hasRiskyTld: Boolean,
    /** Brand the link pretends to be, if any. */
    val impersonates: Brand?,
    /** Brand whose official site this is, if any. */
    val officialFor: Brand?,
    /** Downloaded lists the site is on (filled in by the detector when the lists are available). */
    val lists: Set<SiteList> = emptySet(),
)

data class FoundPhone(val display: String, val isPremium: Boolean, val isForeign: Boolean)

data class Extraction(
    val links: List<FoundLink>,
    val phones: List<FoundPhone>,
    val bankAccounts: List<String>,
    val amounts: List<String>,
    val brands: List<Brand>,
)

/** Pulls links, phone numbers, account numbers, amounts and brand mentions out of a message. */
object EntityExtractor {

    private val schemeUrl = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")
    private val bareDomain = Regex(
        """(?i)(?<![@\p{L}\p{N}.-])((?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?\.)+[\p{L}]{2,24})(?:[/?#][^\s<>"']*)?""",
    )
    private val ipv4 = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")
    private val bankAccount = Regex("""(?i)(?<![\p{L}\d])(?:PL\s?)?\d{2}(?:\s?\d{4}){6}(?!\d)""")
    private val phone = Regex("""(?<![\d+])(?:\+|00)?\d(?:[\s-]?\d){8,14}(?!\d)""")
    private val amount = Regex(
        """(?i)(?<![\p{L}\d])\d{1,3}(?:[  ]\d{3})*(?:[.,]\d{1,2})?\s?(?:zł|zl|pln|eur|€|usd|\$|uah|грн)(?![\p{L}])""",
    )
    private const val TRAILING = ".,;:!?)]}'\"…"

    fun extract(normalized: NormalizedText): Extraction {
        val text = normalized.clean

        // Account numbers first, so their digits aren't mistaken for phone numbers.
        val accounts = bankAccount.findAll(text).map { it.value.trim() }.toList()
        var rest = text
        accounts.forEach { rest = rest.replace(it, " ") }

        return Extraction(
            links = findLinks(text),
            phones = phone.findAll(rest).mapNotNull { toPhone(it.value) }.distinct().toList(),
            bankAccounts = accounts,
            amounts = amount.findAll(text).map { it.value }.distinct().toList(),
            brands = Lexicon.brands.filter { brand -> brandMatchers.getValue(brand).any { it.containsMatchIn(normalized.folded) } },
        )
    }

    private fun findLinks(text: String): List<FoundLink> {
        val links = mutableListOf<FoundLink>()
        val taken = mutableListOf<IntRange>()
        schemeUrl.findAll(text).forEach { match ->
            taken += match.range
            parseLink(match.value.trimEnd { it in TRAILING }, requireKnownTld = false)?.let(links::add)
        }
        bareDomain.findAll(text).forEach { match ->
            if (taken.none { it.first <= match.range.last && match.range.first <= it.last }) {
                parseLink(match.value.trimEnd { it in TRAILING }, requireKnownTld = true)?.let(links::add)
            }
        }
        return links.distinctBy { it.text.lowercase(Locale.ROOT) }
    }

    private fun parseLink(raw: String, requireKnownTld: Boolean): FoundLink? {
        val explicitHttp = raw.startsWith("http://", ignoreCase = true)
        val authority = raw.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase(Locale.ROOT).removePrefix("www.")
        if (host.isBlank() || '.' !in host) return null

        val isIp = ipv4.matches(host)
        val tld = host.substringAfterLast('.')
        if (requireKnownTld && !isIp && tld !in Lexicon.knownTlds) return null

        val registrable = if (isIp) host else registrableDomain(host)
        val official = Lexicon.brands.firstOrNull { brand -> brand.officialDomains.any { host == it || host.endsWith(".$it") } }
        val impersonated = if (official != null || isIp) null else Lexicon.brands.firstOrNull { claimsBrand(host, it) }

        return FoundLink(
            text = raw,
            host = host,
            registrableDomain = registrable,
            explicitHttp = explicitHttp,
            isIp = isIp,
            isPunycode = host.contains("xn--") || host.any { it.code > 127 },
            isShortener = registrable in Lexicon.shorteners || host in Lexicon.shorteners,
            hasRiskyTld = !isIp && tld in Lexicon.riskyTlds,
            impersonates = impersonated,
            officialFor = official,
        )
    }

    /** "sledzenie.inpost.pl" → "inpost.pl"; "pekao.com.pl" → "pekao.com.pl". */
    fun registrableDomain(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in Lexicon.multiPartSuffixes) labels.takeLast(3).joinToString(".") else lastTwo
    }

    /**
     * Does [host] use [brand]'s name? Either as a whole part ("pko-weryfikacja.xyz" → "pko"), or,
     * for longer names, anywhere inside ("inpostdostawa.top" → "inpost").
     */
    private fun claimsBrand(host: String, brand: Brand): Boolean {
        val parts = host.split('.', '-')
        return brand.keywords.map { TextNormalizer.fold(it).replace(" ", "") }.any { keyword ->
            keyword in parts || (keyword.length >= 5 && host.contains(keyword))
        }
    }

    /** Whole-word matchers for each brand's names, built once. */
    private val brandMatchers: Map<Brand, List<Regex>> = Lexicon.brands.associateWith { brand ->
        brand.keywords.map { Regex("(?<![\\p{L}\\p{N}])${Regex.escape(TextNormalizer.fold(it))}(?![\\p{L}\\p{N}])") }
    }

    /** Polish numbers get "+48"; numbers starting 70x are premium-rate; others are foreign. */
    private fun toPhone(raw: String): FoundPhone? {
        var digits = raw.filter { it.isDigit() }
        val international = raw.startsWith("+") || raw.startsWith("00")
        if (raw.startsWith("00")) digits = digits.drop(2)
        if (digits.length !in 9..15) return null

        val polish = !international && digits.length == 9 || international && digits.startsWith("48") && digits.length == 11
        val national = if (polish && digits.length == 11) digits.drop(2) else digits
        val display = if (polish) "+48 ${national.chunked(3).joinToString(" ")}" else "+$digits"
        return FoundPhone(
            display = display,
            isPremium = polish && national.startsWith("70"),
            isForeign = international && !polish,
        )
    }
}
