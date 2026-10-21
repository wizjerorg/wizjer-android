package org.wizjer.android.detection

import org.wizjer.android.detection.lists.SiteList

/** Turns the cleaned text and extracted items into evidence ([Reason]s). */
object RuleEngine {

    fun evaluate(normalized: NormalizedText, extraction: Extraction): List<Reason> {
        val text = normalized.folded
        val reasons = mutableListOf<Reason>()
        fun add(signal: Signal, detail: String? = null) {
            if (reasons.none { it.signal == signal }) reasons += Reason(signal, detail)
        }

        // Links
        val links = extraction.links
        if (links.isNotEmpty()) add(Signal.Link)
        links.firstOrNull { it.impersonates != null }?.let { add(Signal.BrandLookalike, it.impersonates!!.name) }
        if (links.any { it.isShortener }) add(Signal.ShortLink)
        if (links.any { it.isIp }) add(Signal.IpHost)
        if (links.any { it.isPunycode }) add(Signal.Punycode)
        if (links.any { it.hasRiskyTld }) add(Signal.RiskyTld)
        if (links.any { it.explicitHttp }) add(Signal.NoHttps)
        // Reassure only when every link is the brand's real site.
        if (links.isNotEmpty() && links.all { it.officialFor != null }) add(Signal.OfficialDomain, links.first().officialFor!!.name)

        // Downloaded threat lists
        if (links.any { SiteList.Cert in it.lists }) add(Signal.CertList)
        if (links.any { SiteList.Gambling in it.lists }) add(Signal.GamblingSite)
        if (links.any { SiteList.Adult in it.lists }) add(Signal.AdultSite)
        if (links.any { SiteList.Piracy in it.lists }) add(Signal.PiracySite)

        // Wording
        val categories = extraction.brands.map { it.category }.toSet()
        if (Lexicon.urgency.matches(text)) add(Signal.Urgency)
        if (Lexicon.payment.matches(text)) add(Signal.Payment)
        if (Lexicon.credentials.matches(text)) add(Signal.Credentials)
        if (Lexicon.threat.matches(text)) add(Signal.Threat)
        if (Lexicon.authority.matches(text) || BrandCategory.Bank in categories || BrandCategory.Government in categories) {
            add(Signal.Authority)
        }
        if (Lexicon.safeAccount.matches(text)) add(Signal.SafeAccount)
        if (Lexicon.prize.matches(text)) add(Signal.Prize)
        if (Lexicon.family.matches(text)) add(Signal.Family)
        if (Lexicon.investment.matches(text)) add(Signal.Investment)
        if (Lexicon.job.matches(text)) add(Signal.Job)
        if (Lexicon.marketplace.matches(text)) add(Signal.Marketplace)
        if (Lexicon.remoteApp.matches(text)) add(Signal.RemoteApp)
        if (Lexicon.clickPrompt.matches(text) && links.isNotEmpty()) add(Signal.ClickPrompt)
        if (Lexicon.parcel.matches(text) || BrandCategory.Parcel in categories) add(Signal.Parcel)
        if (Lexicon.utility.matches(text) || BrandCategory.Utility in categories) add(Signal.Utility)

        // Other details
        if (extraction.amounts.isNotEmpty()) add(Signal.Money)
        if (extraction.bankAccounts.isNotEmpty()) add(Signal.BankAccount)
        if (extraction.phones.any { it.isPremium }) add(Signal.PremiumNumber)
        if (normalized.obfuscated) add(Signal.Obfuscation)

        // Genuine one-time-code messages say "don't share this". A scam may copy the phrase,
        // so it only counts when there's no link to an unofficial site.
        if (Lexicon.safetyWarning.matches(text) && links.none { it.officialFor == null }) add(Signal.SafetyWarning)

        return reasons
    }
}
