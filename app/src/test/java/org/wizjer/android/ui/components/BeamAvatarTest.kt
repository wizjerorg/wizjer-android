package org.wizjer.android.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wizjer.android.ui.theme.BrandMint
import org.wizjer.android.ui.theme.Ink

class BeamAvatarTest {

    /** Five stand-in colours, so a colour tells which palette index the original picked. */
    private val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta)

    private class Expected(
        val hash: Long, val wrapper: Int, val background: Int, val wrapperTranslate: Pair<Float, Float>, val wrapperRotate: Float,
        val wrapperScale: Float, val isMouthOpen: Boolean, val isCircle: Boolean, val eyeSpread: Float, val mouthSpread: Float,
        val faceRotate: Float, val faceTranslate: Pair<Float, Float>,
    )

    @Test
    fun `matches the original library for the same names`() {
        // Values printed by the original JavaScript (utilities.ts + avatar-beam.tsx) for these names.
        val expected = mapOf(
            "Clara Barton" to Expected(645088871, 1, 4, 5f to 3f, 191f, 1.2f, true, false, 1f, 2f, -1f, 7f to 0f),
            "Jan Kowalski" to Expected(1401178492, 2, 0, 6f to 2f, 172f, 1.1f, true, false, 2f, 1f, -2f, 4f to 0f),
            "Ola Wójcik" to Expected(1968585933, 3, 1, 7f to 7f, 93f, 1f, false, false, 3f, 0f, 3f, 3.5f to 3.5f),
            "Олена Шевченко" to Expected(1902270973, 3, 1, 7f to 7f, 13f, 1.1f, false, false, 3f, 1f, -3f, 3.5f to 3.5f),
            "Wizjer" to Expected(1703521841, 1, 4, 3f to 3f, 41f, 1.2f, true, true, 1f, 2f, 1f, -1f to 0f),
        )
        expected.forEach { (name, e) ->
            val beam = beam(name, colors)
            assertEquals(name, e.hash, nameHash(name))
            assertEquals(name, colors[e.wrapper], beam.wrapper)
            assertEquals(name, colors[e.background], beam.background)
            assertEquals(name, e.wrapperTranslate.first, beam.wrapperTranslateX, 1e-6f)
            assertEquals(name, e.wrapperTranslate.second, beam.wrapperTranslateY, 1e-6f)
            assertEquals(name, e.wrapperRotate, beam.wrapperRotate, 1e-6f)
            assertEquals(name, e.wrapperScale, beam.wrapperScale, 1e-6f)
            assertEquals(name, e.isMouthOpen, beam.isMouthOpen)
            assertEquals(name, e.isCircle, beam.isCircle)
            assertEquals(name, e.eyeSpread, beam.eyeSpread, 1e-6f)
            assertEquals(name, e.mouthSpread, beam.mouthSpread, 1e-6f)
            assertEquals(name, e.faceRotate, beam.faceRotate, 1e-6f)
            assertEquals(name, e.faceTranslate.first, beam.faceTranslateX, 1e-6f)
            assertEquals(name, e.faceTranslate.second, beam.faceTranslateY, 1e-6f)
        }
    }

    @Test
    fun `the face contrasts with the shape behind it`() {
        // "Clara Barton" gets the deep teal, "Jan Kowalski" the brand mint.
        assertEquals(Color(0xFF1F6F5C), beam("Clara Barton").wrapper)
        assertEquals(Color.White, beam("Clara Barton").face)
        assertEquals(BrandMint, beam("Jan Kowalski").wrapper)
        assertEquals(Ink, beam("Jan Kowalski").face)
    }

    @Test
    fun `background and shape never share a colour`() {
        listOf("Ania", "Bartek", "Celina", "Darek", "Ewa", "Franek", "Gosia", "Henryk", "Iza", "Jurek").forEach { name ->
            val beam = beam(name)
            assert(beam.background != beam.wrapper) { name }
        }
    }
}
