package org.wizjer.android.detection

import androidx.annotation.StringRes
import org.wizjer.android.R
import org.wizjer.android.protection.MessageSource

/** What to check: the message text and, if known, where it came from and who sent it. */
data class DetectorInput(
    val text: String,
    val source: MessageSource? = null,
    val sender: String? = null,
)

enum class RiskLevel(@StringRes val label: Int) {
    Safe(R.string.verdict_safe),
    Suspicious(R.string.verdict_suspicious),
    Dangerous(R.string.verdict_dangerous),
}

/** Kinds of scam common in Poland; a message can match several. */
enum class ScamType(@StringRes val label: Int) {
    ParcelFee(R.string.type_parcel_fee),
    BankImpersonation(R.string.type_bank),
    CodeTheft(R.string.type_code_theft),
    FamilyEmergency(R.string.type_family),
    Marketplace(R.string.type_marketplace),
    Investment(R.string.type_investment),
    JobTask(R.string.type_job),
    UtilityShutoff(R.string.type_utility),
    Government(R.string.type_government),
    Prize(R.string.type_prize),
    RemoteAccess(R.string.type_remote_access),
    Phishing(R.string.type_phishing),
}

/**
 * One piece of evidence. [weight] is its contribution to the risk (log-odds): positive
 * makes a scam more likely, negative less. Labels with %1$s take a detail, e.g. a brand.
 */
enum class Signal(val weight: Double, @StringRes val label: Int) {
    // Links
    Link(0.6, R.string.sig_link),
    ClickPrompt(0.8, R.string.sig_click_prompt),
    ShortLink(1.5, R.string.sig_short_link),
    IpHost(2.0, R.string.sig_ip_host),
    Punycode(2.0, R.string.sig_punycode),
    RiskyTld(1.6, R.string.sig_risky_tld),
    BrandLookalike(3.0, R.string.sig_brand_lookalike),
    NoHttps(0.6, R.string.sig_no_https),
    OfficialDomain(-2.5, R.string.sig_official_domain),

    // Downloaded threat lists
    CertList(4.5, R.string.sig_cert_list),
    GamblingSite(1.8, R.string.sig_gambling_site),
    AdultSite(1.8, R.string.sig_adult_site),
    PiracySite(1.2, R.string.sig_piracy_site),

    // Wording and tactics
    Urgency(1.2, R.string.sig_urgency),
    Payment(1.4, R.string.sig_payment),
    Credentials(1.8, R.string.sig_credentials),
    Threat(1.4, R.string.sig_threat),
    Authority(0.8, R.string.sig_authority),
    SafeAccount(3.0, R.string.sig_safe_account),
    Prize(1.6, R.string.sig_prize),
    Family(2.6, R.string.sig_family),
    Investment(1.6, R.string.sig_investment),
    Job(1.4, R.string.sig_job),
    Marketplace(1.2, R.string.sig_marketplace),
    RemoteApp(2.2, R.string.sig_remote_app),
    Parcel(0.4, R.string.sig_parcel),
    Utility(0.4, R.string.sig_utility),

    // Other details
    Money(0.5, R.string.sig_money),
    BankAccount(0.8, R.string.sig_bank_account),
    PremiumNumber(1.5, R.string.sig_premium_number),
    Obfuscation(2.0, R.string.sig_obfuscation),

    // Reassuring: typical of genuine messages
    SafetyWarning(-2.0, R.string.sig_safety_warning),

    // The on-device AI model; the detail is its score. A scam verdict alone is enough for
    // "dangerous", a "be careful" one for "suspicious".
    ModelScam(4.0, R.string.sig_model_scam),
    ModelSuspicious(2.2, R.string.sig_model_suspicious),
    ModelOrdinary(-0.8, R.string.sig_model_ordinary),
}

/** A signal found in a message, with an optional detail for its label (e.g. "InPost"). */
data class Reason(val signal: Signal, val detail: String? = null) {
    val contribution: Double get() = signal.weight
}

enum class EntityKind(@StringRes val label: Int) {
    Link(R.string.entity_link),
    Phone(R.string.entity_phone),
    BankAccount(R.string.entity_account),
    Amount(R.string.entity_amount),
    Brand(R.string.entity_brand),
}

/** Notes attached to an extracted item; labels with %1$s take [Entity.noteDetail]. */
enum class EntityNote(@StringRes val label: Int) {
    ShortLink(R.string.note_short_link),
    IpHost(R.string.note_ip),
    Punycode(R.string.note_punycode),
    RiskyTld(R.string.note_risky_tld),
    Lookalike(R.string.note_lookalike),
    Official(R.string.note_official),
    NoHttps(R.string.note_http),
    CertList(R.string.note_cert_list),
    Gambling(R.string.note_gambling),
    Adult(R.string.note_adult),
    Piracy(R.string.note_piracy),
    PremiumNumber(R.string.note_premium),
    Foreign(R.string.note_foreign),
}

data class Entity(
    val kind: EntityKind,
    val value: String,
    val notes: List<EntityNote> = emptyList(),
    val noteDetail: String? = null,
)

data class TypeScore(val type: ScamType, val score: Double)

enum class Stage(@StringRes val label: Int) {
    Normalize(R.string.stage_normalize),
    Extract(R.string.stage_extract),
    Lists(R.string.stage_lists),
    Rules(R.string.stage_rules),
    Model(R.string.stage_model),
    Fusion(R.string.stage_fusion),
}

data class StageTiming(val stage: Stage, val micros: Long)

/** The full result for one message. */
data class Analysis(
    /** 0–100. */
    val risk: Int,
    val level: RiskLevel,
    /** 0–100: how sure the detector is about [level]. */
    val confidence: Int,
    val primaryType: ScamType?,
    /** Scam types with any evidence, strongest first. */
    val types: List<TypeScore>,
    /** Strongest evidence first (by absolute contribution). */
    val reasons: List<Reason>,
    val entities: List<Entity>,
    val normalizedText: String,
    /** "pl", "uk" or "en" (best guess). */
    val language: String,
    val timings: List<StageTiming>,
    val detectorVersion: String,
    /** Whether the on-device AI model took part. */
    val modelUsed: Boolean = false,
    /** Whether the links were checked against the downloaded threat lists. */
    val listsUsed: Boolean = false,
)
