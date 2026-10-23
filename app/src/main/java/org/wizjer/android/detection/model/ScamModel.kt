package org.wizjer.android.detection.model

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * The on-device scam model trained in the wizjer-model repository (scripts/train_baseline.py,
 * packed by scripts/export_model.py): a logistic regression over hashed text features. It runs
 * entirely on the phone; nothing about the message leaves it.
 */
class ScamModel private constructor(
    private val weights: ShortArray,
    private val scale: Double,
    private val bias: Double,
    private val metaWeight: Double,
    private val thresholds: Map<String, Thresholds>,
    /** Test messages with the scores the training code gave them, for checking this port. */
    internal val golden: List<Golden>,
) {

    /** [scam] flags at most 1% of genuine messages in testing; [caution] (softer) at most 5%. */
    data class Thresholds(val scam: Double, val caution: Double)

    internal data class Golden(val lang: String, val source: String, val sender: String, val subject: String, val text: String, val score: Double)

    /**
     * The chance (0–1) that the message is a scam. [source] is "sms", "notification" or "email";
     * [sender] is the SMS sender, chat name or "Name <address>", if known.
     */
    fun score(text: String, source: String, sender: String = "", subject: String = ""): Double {
        val features = ModelFeatures.features(text, source, sender, subject, metaWeight)
        var sum = 0.0
        for ((index, value) in features) sum += weights[index] * scale * value
        val z = bias + sum
        return if (z > -35) 1 / (1 + exp(-z)) else 0.0
    }

    /** The thresholds for a language ("pl", "uk", "en") and source: per channel where there were enough messages. */
    fun thresholds(language: String, source: String): Thresholds? = thresholds["$language/$source"] ?: thresholds[language]

    companion object {
        /** Where the app bundles the model. */
        const val ASSET = "model/wizjer-baseline.bin"

        fun read(input: InputStream): ScamModel {
            val buffer = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { buffer.get(it) }
            require(magic.decodeToString() == "WZJ2") { "not a Wizjer model (format 2)" }
            val dim = buffer.int
            require(dim == ModelFeatures.DIM) { "model has $dim features, this app computes ${ModelFeatures.DIM}" }
            val scale = buffer.double
            val bias = buffer.double
            val metaWeight = buffer.double
            val thresholds = buildMap {
                repeat(buffer.short.toInt() and 0xFFFF) { put(buffer.text(), Thresholds(scam = buffer.double, caution = buffer.double)) }
            }
            val golden = List(buffer.short.toInt() and 0xFFFF) {
                Golden(buffer.text(), buffer.text(), buffer.text(), buffer.text(), buffer.text(), buffer.double)
            }
            val weights = ShortArray(dim).also { buffer.asShortBuffer().get(it) }
            return ScamModel(weights, scale, bias, metaWeight, thresholds, golden)
        }

        private fun ByteBuffer.text(): String = ByteArray(int).also { get(it) }.decodeToString()
    }
}
