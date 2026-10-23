package org.wizjer.android.detection.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.wizjer.android.data.model.ModelStore
import org.wizjer.android.detection.DetectorInput
import org.wizjer.android.detection.LocalScamDetector
import org.wizjer.android.detection.SampleMessages

/**
 * The model on a real phone. Android's regex engine (ICU) differs from the JVM's that the unit
 * tests use, so the feature port is checked here against the training code's scores too.
 */
@RunWith(AndroidJUnit4::class)
class ScamModelDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val model = checkNotNull(ModelStore.get(context)) { "the bundled model didn't load" }

    @Test
    fun scoresMatchTheTrainingCode() {
        assertTrue(model.golden.size > 30)
        model.golden.forEach { g ->
            assertEquals(g.text.take(60), g.score, model.score(g.text, g.source, g.sender, g.subject), 1e-9)
        }
    }

    @Test
    fun hasThresholdsForEveryLanguage() {
        listOf("pl", "uk", "en").forEach { assertNotNull(it, model.thresholds(it, "sms")) }
    }

    @Test
    fun everySampleKeepsItsLevel() {
        val detector = LocalScamDetector(model = { model })
        SampleMessages.all.forEach { sample ->
            val analysis = detector.analyzeNow(DetectorInput(sample.text, sender = sample.sender))
            assertEquals(sample.label, sample.expected, analysis.level)
            assertTrue(analysis.modelUsed)
        }
    }
}
