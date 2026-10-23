package org.wizjer.android.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wizjer.android.detection.model.ScamModel
import java.io.File

class ModelCheckTest {

    private val bytes = File("src/main/assets/${ScamModel.ASSET}").readBytes()

    @Test
    fun `the bundled model passes`() {
        assertTrue(ModelCheck.passes(ScamModel.read(bytes.inputStream())))
    }

    @Test
    fun `a model whose weights don't match its test scores is refused`() {
        // The weights fill the end of the file; zeroing them breaks every score the file promises.
        val broken = bytes.copyOf().also { it.fill(0, it.size - 2 * 1_048_576, it.size) }
        assertFalse(ModelCheck.passes(ScamModel.read(broken.inputStream())))
    }

    @Test
    fun `newer releases by date`() {
        assertTrue(ModelCheck.isNewer("model-2026.11.02", than = "model-2026.10.23"))
        assertTrue(ModelCheck.isNewer("model-2026.10.23.2", than = "model-2026.10.23"))
        assertTrue(ModelCheck.isNewer("model-2027.1.1", than = "model-2026.12.31"))
        assertTrue(ModelCheck.isNewer("model-2026.10.23", than = null))
        assertFalse(ModelCheck.isNewer("model-2026.10.23", than = "model-2026.10.23"))
        assertFalse(ModelCheck.isNewer("model-2026.10.22", than = "model-2026.10.23"))
    }

    @Test
    fun `unknown tags are never newer`() {
        assertFalse(ModelCheck.isNewer("v2", than = "model-2026.10.23"))
        assertFalse(ModelCheck.isNewer("model-latest", than = null))
        assertFalse(ModelCheck.isNewer("", than = null))
    }
}
