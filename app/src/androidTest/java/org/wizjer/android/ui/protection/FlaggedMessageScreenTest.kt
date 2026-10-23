package org.wizjer.android.ui.protection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.wizjer.android.R
import org.wizjer.android.data.model.ModelStore
import org.wizjer.android.detection.LocalScamDetector
import org.wizjer.android.protection.FlaggedMessage
import org.wizjer.android.protection.IncomingMessage
import org.wizjer.android.protection.MessageSource
import org.wizjer.android.protection.toDetectorInput
import org.wizjer.android.ui.theme.WizjerTheme

/** The screen a scam alert opens shows the message and why it was flagged, and offers an in-depth analysis. */
@RunWith(AndroidJUnit4::class)
class FlaggedMessageScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun flagged(): FlaggedMessage {
        val message = IncomingMessage(MessageSource.Sms, SENDER, SCAM, null, System.currentTimeMillis() - 120_000)
        val analysis = LocalScamDetector(model = { ModelStore.get(context) }).analyzeNow(message.toDetectorInput())
        return FlaggedMessage(1, message, analysis)
    }

    @Test
    fun showsTheMessageAndWhyAndOffersAnAnalysis() {
        val flagged = flagged()
        var asked: FlaggedMessage? = null
        compose.setContent { WizjerTheme { FlaggedMessageScreen(flagged, senior = false, onBack = {}, onAskWizjer = { asked = it }) } }
        compose.onNodeWithText(context.getString(R.string.flagged_headline)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.flagged_body)).assertIsDisplayed()
        compose.onNodeWithText(SENDER).assertIsDisplayed()
        compose.onNodeWithText(SCAM).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sig_brand_lookalike, "InPost")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.flagged_analyze)).assertIsDisplayed().performClick()
        assertEquals(flagged, asked)
    }

    @Test
    fun seniorsGetPlainerReasons() {
        compose.setContent { WizjerTheme { FlaggedMessageScreen(flagged(), senior = true, onBack = {}, onAskWizjer = {}) } }
        compose.onNodeWithText(context.getString(R.string.sig_brand_lookalike_simple, "InPost")).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sig_brand_lookalike, "InPost")).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.sig_model_scam_simple), substring = true).assertDoesNotExist()
    }

    @Test
    fun explainsWhenTheMessageIsGone() {
        compose.setContent { WizjerTheme { FlaggedMessageScreen(null, senior = false, onBack = {}, onAskWizjer = {}) } }
        compose.onNodeWithText(context.getString(R.string.flagged_gone)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.flagged_analyze)).assertDoesNotExist()
    }

    private companion object {
        const val SENDER = "+48 512 345 678"
        const val SCAM = "InPost: Twoja przesyłka została wstrzymana z powodu niedopłaty 1,99 zł. Dopłać w ciągu 24h: https://inpost-dostawa.top/pl"
    }
}
