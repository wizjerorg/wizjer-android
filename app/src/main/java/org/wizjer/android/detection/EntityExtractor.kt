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
    /** A genuine service's link to any person (paypal.me/anyone, wa.me/number). */
    val isPersonal: Boolean = false,
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

    // "www." starts a link only on its own: in "mgmntss-www.com" it's part of the name.
    private val schemeUrl = Regex("""(?i)(?:\bhttps?://|(?<![\p{L}\p{N}.@-])www\.)[^\s<>"']+""")
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

    /**
     * [isListed] says whether the downloaded lists know a domain: a bare one ("inpost-pl.bond") with
     * an ending we don't recognise still counts as a link then.
     */
    fun extract(normalized: NormalizedText, isListed: (String) -> Boolean = { false }): Extraction {
        val text = normalized.clean

        // Account numbers first, so their digits aren't mistaken for phone numbers.
        val accounts = bankAccount.findAll(text).map { it.value.trim() }.toList()
        var rest = text
        accounts.forEach { rest = rest.replace(it, " ") }
        val links = findLinks(text, isListed)
        // "revolut.me/marek" names a person, not the bank.
        val brandText = links.filter { it.isPersonal }.fold(normalized.folded) { folded, link -> folded.replace(TextNormalizer.fold(link.text), " ") }

        return Extraction(
            links = links,
            phones = phone.findAll(rest).mapNotNull { toPhone(it.value) }.distinct().toList(),
            bankAccounts = accounts,
            amounts = amount.findAll(text).map { it.value }.distinct().toList(),
            brands = Lexicon.brands.filter { brand -> brandMatchers.getValue(brand).any { it.containsMatchIn(brandText) } },
        )
    }

    private fun findLinks(text: String, isListed: (String) -> Boolean): List<FoundLink> {
        val links = mutableListOf<FoundLink>()
        val taken = mutableListOf<IntRange>()
        schemeUrl.findAll(text).forEach { match ->
            taken += match.range
            parseLink(match.value.trimEnd { it in TRAILING }, requireKnownTld = false)?.let(links::add)
        }
        bareDomain.findAll(text).forEach { match ->
            if (taken.none { it.first <= match.range.last && match.range.first <= it.last }) {
                parseLink(match.value.trimEnd { it in TRAILING }, requireKnownTld = true, isListed)?.let(links::add)
            }
        }
        return links.distinctBy { it.text.lowercase(Locale.ROOT) }
    }

    private fun parseLink(raw: String, requireKnownTld: Boolean, isListed: (String) -> Boolean = { false }): FoundLink? {
        val explicitHttp = raw.startsWith("http://", ignoreCase = true)
        val authority = raw.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase(Locale.ROOT).removePrefix("www.")
        if (host.isBlank() || '.' !in host) return null

        val isIp = ipv4.matches(host)
        val tld = host.substringAfterLast('.')
        if (requireKnownTld && !isIp && tld !in Lexicon.knownTlds && !isListed(host)) return null

        val registrable = if (isIp) host else registrableDomain(host)
        // paypal.me/anyone is really PayPal, but the money goes to whoever made the link.
        val personal = registrable in Lexicon.personalLinkHosts
        val official = if (personal) null else Lexicon.brands.firstOrNull { brand -> brand.officialDomains.any { host == it || host.endsWith(".$it") } }
        val impersonated = if (official != null || personal || isIp) null else Lexicon.brands.firstOrNull { claimsBrand(host, it) }

        return FoundLink(
            text = raw,
            host = host,
            registrableDomain = registrable,
            explicitHttp = explicitHttp,
            isIp = isIp,
            isPunycode = host.contains("xn--") || host.any { it.code > 127 },
            isShortener = (registrable in Lexicon.shorteners || host in Lexicon.shorteners) && host !in Lexicon.notShorteners,
            hasRiskyTld = !isIp && tld in Lexicon.riskyTlds,
            impersonates = impersonated,
            officialFor = official,
            isPersonal = personal,
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
