package org.wizjer.android.data.model

import org.wizjer.android.detection.RiskLevel
import org.wizjer.android.detection.SampleMessages
import org.wizjer.android.detection.model.ScamModel
import kotlin.math.abs

/** What a downloaded model must pass before the app uses it, and which release is newer. */
object ModelCheck {

    /**
     * The model reproduces the scores its training code gave its own test messages, so this app
     * computes its features the same way (a model needing newer app code fails here), and it
     * passes a small exam: no genuine sample looks like a scam to it, and at least three in four
     * sample scams look suspicious (the rules catch the rest, e.g. disguised text the model sees
     * raw). Guards against a broken file or a botched release, not against a deliberately crafted one.
     */
    fun passes(model: ScamModel): Boolean {
        if (model.golden.isEmpty()) return false
        if (model.golden.any { abs(model.score(it.text, it.source, it.sender, it.subject) - it.score) > 1e-6 }) return false
        val thresholds = model.thresholds("pl", "sms") ?: return false
        val (scams, genuine) = SampleMessages.all.partition { it.expected == RiskLevel.Dangerous }
        fun score(sample: SampleMessages.Sample) = model.score(sample.text, "sms", sample.sender.orEmpty())
        return genuine.all { score(it) < thresholds.scam } && scams.count { score(it) >= thresholds.caution } * 4 >= scams.size * 3
    }

    /** Whether release [version] ("model-2026.10.23") is newer than [than]; an unknown format never is. */
    fun isNewer(version: String, than: String?): Boolean {
        val candidate = parts(version) ?: return false
        val current = than?.let(::parts) ?: return true
        for (i in 0 until maxOf(candidate.size, current.size)) {
            val a = candidate.getOrElse(i) { 0 }
            val b = current.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private val VERSION = Regex("""model-(\d{1,9}(?:\.\d{1,9})*)""")

    private fun parts(version: String): List<Int>? = VERSION.matchEntire(version.trim())?.groupValues?.get(1)?.split('.')?.map(String::toInt)
}
