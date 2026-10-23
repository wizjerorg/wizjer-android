package org.wizjer.android.data.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SeniorTest {

    private val today = LocalDate.of(2026, 10, 23)

    @Test
    fun `60 from the birthday on`() {
        assertFalse(isSenior(LocalDate.of(1966, 10, 24), today))
        assertTrue(isSenior(LocalDate.of(1966, 10, 23), today))
        assertTrue(isSenior(LocalDate.of(1948, 3, 1), today))
        assertFalse(isSenior(LocalDate.of(1990, 1, 1), today))
    }

    @Test
    fun `an unknown birth date isn't 60+`() {
        assertFalse(isSenior(null, today))
    }
}
