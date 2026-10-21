package org.wizjer.android.detection

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.tanh

/** Combines all evidence into the final risk, level, confidence and scam types. */
object RiskFusion {

    /** Without any evidence a message is very likely fine (~5% risk). */
    private const val BIAS = -3.0
    private const val SUSPICIOUS_FROM = 30
    private const val DANGEROUS_FROM = 65
    private const val MIN_TYPE_SCORE = 0.35

    data class Verdict(
        val risk: Int,
        val level: RiskLevel,
        val confidence: Int,
        val primaryType: ScamType?,
        val types: List<TypeScore>,
    )

    fun combine(reasons: List<Reason>, extraction: Extraction, textLength: Int, language: String): Verdict {
        val logit = BIAS + reasons.sumOf { it.contribution }
        val risk = (100 / (1 + exp(-logit))).roundToInt()
        val level = when {
            risk >= DANGEROUS_FROM -> RiskLevel.Dangerous
            risk >= SUSPICIOUS_FROM -> RiskLevel.Suspicious
            else -> RiskLevel.Safe
        }
        val types = scoreTypes(reasons.map { it.signal }.toSet(), extraction.brands.map { it.category }.toSet())
            .filter { it.score >= MIN_TYPE_SCORE }
            .sortedByDescending { it.score }

        return Verdict(
            risk = risk,
            level = level,
            confidence = confidence(logit, textLength, language),
            primaryType = types.firstOrNull()?.type?.takeIf { level != RiskLevel.Safe },
            types = types,
        )
    }

    /**
     * How sure the rules are: the further the evidence is from the fence, the surer.
     * Rules alone are capped at 95%; short texts and languages with fewer phrases lower it.
     */
    private fun confidence(logit: Double, textLength: Int, language: String): Int {
        var value = 0.5 + 0.5 * tanh(abs(logit) / 3)
        if (textLength < 25) value *= 0.8
        if (language != "pl") value *= 0.9
        return (value.coerceAtMost(0.95) * 100).roundToInt()
    }

    /** Each scam type is a recognisable combination of signals and brand kinds. */
    private fun scoreTypes(s: Set<Signal>, brands: Set<BrandCategory>): List<TypeScore> {
        fun has(signal: Signal) = if (signal in s) 1.0 else 0.0
        fun any(vararg signals: Signal) = if (signals.any { it in s }) 1.0 else 0.0
        fun brand(category: BrandCategory) = if (category in brands) 1.0 else 0.0
        val riskyLink = any(Signal.CertList, Signal.BrandLookalike, Signal.RiskyTld, Signal.ShortLink, Signal.IpHost, Signal.Punycode)

        return listOf(
            TypeScore(ScamType.ParcelFee, 0.5 * has(Signal.Parcel) + 0.3 * has(Signal.Payment) + 0.2 * riskyLink),
            TypeScore(
                ScamType.BankImpersonation,
                0.5 * maxOf(brand(BrandCategory.Bank), has(Signal.SafeAccount)) +
                    0.25 * any(Signal.Threat, Signal.Credentials) + 0.25 * maxOf(riskyLink, has(Signal.SafeAccount)),
            ),
            TypeScore(ScamType.CodeTheft, 0.7 * has(Signal.Credentials) + 0.3 * any(Signal.Urgency, Signal.Authority, Signal.Threat)),
            TypeScore(ScamType.FamilyEmergency, 0.7 * has(Signal.Family) + 0.3 * any(Signal.Payment, Signal.Money)),
            TypeScore(
                ScamType.Marketplace,
                0.6 * maxOf(has(Signal.Marketplace), brand(BrandCategory.Marketplace) * 0.6) + 0.4 * maxOf(riskyLink, has(Signal.Payment)),
            ),
            TypeScore(ScamType.Investment, 0.8 * has(Signal.Investment) + 0.2 * any(Signal.Link, Signal.Money)),
            TypeScore(ScamType.JobTask, 0.8 * has(Signal.Job) + 0.2 * any(Signal.Link, Signal.Payment)),
            TypeScore(ScamType.UtilityShutoff, 0.5 * has(Signal.Utility) + 0.3 * has(Signal.Threat) + 0.2 * has(Signal.Payment)),
            TypeScore(
                ScamType.Government,
                0.6 * brand(BrandCategory.Government) + 0.4 * any(Signal.Threat, Signal.Payment, Signal.Link),
            ),
            TypeScore(ScamType.Prize, 0.8 * has(Signal.Prize) + 0.2 * any(Signal.Link, Signal.Credentials)),
            TypeScore(ScamType.RemoteAccess, 0.8 * has(Signal.RemoteApp) + 0.2 * has(Signal.Authority)),
            TypeScore(ScamType.Phishing, 0.5 * has(Signal.Credentials) + 0.5 * riskyLink),
        )
    }
}
