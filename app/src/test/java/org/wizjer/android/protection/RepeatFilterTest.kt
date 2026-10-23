package org.wizjer.android.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatFilterTest {

    private var now = 0L
    private val filter = RepeatFilter(windowMillis = 1_000, now = { now })

    @Test
    fun `the same text soon after is a repeat, whatever the spacing`() {
        assertFalse(filter.isRepeat("Twoja paczka czeka na dopłatę"))
        now = 500
        assertTrue(filter.isRepeat("  Twoja paczka\nczeka na dopłatę "))
    }

    @Test
    fun `other texts and later copies are checked again`() {
        assertFalse(filter.isRepeat("Twoja paczka czeka na dopłatę"))
        assertFalse(filter.isRepeat("Będę o 18"))
        now = 1_000
        assertFalse(filter.isRepeat("Twoja paczka czeka na dopłatę"))
    }
}
