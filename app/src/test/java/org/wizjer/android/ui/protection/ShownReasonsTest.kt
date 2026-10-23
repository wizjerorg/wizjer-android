package org.wizjer.android.ui.protection

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wizjer.android.detection.Reason
import org.wizjer.android.detection.Signal

class ShownReasonsTest {

    private val reasons = listOf(
        Reason(Signal.CertList), Reason(Signal.BrandLookalike, "InPost"), Reason(Signal.ModelScam, "99%"),
        Reason(Signal.Payment), Reason(Signal.Urgency), Reason(Signal.Link), Reason(Signal.OfficialDomain, "InPost"),
    )

    @Test
    fun `everyone else sees every warning sign`() {
        assertEquals(6, shownReasons(reasons, senior = false).size)
    }

    @Test
    fun `seniors see the three clearest, without the AI model's opinion`() {
        assertEquals(
            listOf(Signal.CertList, Signal.BrandLookalike, Signal.Payment),
            shownReasons(reasons, senior = true).map { it.signal },
        )
    }

    @Test
    fun `the AI model's opinion stays when it's the only sign`() {
        assertEquals(listOf(Signal.ModelScam), shownReasons(listOf(Reason(Signal.ModelScam, "92%")), senior = true).map { it.signal })
    }
}
