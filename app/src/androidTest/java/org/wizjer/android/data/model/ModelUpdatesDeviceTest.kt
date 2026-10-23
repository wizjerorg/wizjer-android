package org.wizjer.android.data.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Reading GitHub's release info (android.util.JsonReader, so on a device) and the bundled version. */
@RunWith(AndroidJUnit4::class)
class ModelUpdatesDeviceTest {

    @Test
    fun readsTheModelFromARelease() {
        val json = """
            {"url":"https://api.github.com/repos/wizjerorg/wizjer-model/releases/1","tag_name":"model-2026.11.02","draft":false,
             "author":{"login":"someone","id":1},
             "assets":[
               {"name":"model.json","size":18472662,"browser_download_url":"https://github.com/wizjerorg/wizjer-model/releases/download/model-2026.11.02/model.json"},
               {"name":"wizjer-baseline.bin","size":2106809,"uploader":{"login":"someone"},"browser_download_url":"https://github.com/wizjerorg/wizjer-model/releases/download/model-2026.11.02/wizjer-baseline.bin"}
             ],
             "body":"Notes"}
        """.trimIndent()
        val release = ModelUpdates.parseRelease(json.byteInputStream())
        assertEquals("model-2026.11.02", release.tag)
        assertEquals("https://github.com/wizjerorg/wizjer-model/releases/download/model-2026.11.02/wizjer-baseline.bin", release.modelUrl)
        assertEquals(2106809L, release.modelSize)
    }

    @Test
    fun aReleaseWithoutTheModelHasNoUrl() {
        val release = ModelUpdates.parseRelease("""{"tag_name":"model-2026.11.02","assets":[]}""".byteInputStream())
        assertNull(release.modelUrl)
    }

    @Test
    fun theBundledModelKnowsItsRelease() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("model-2026.10.23", ModelStore.bundledVersion(context))
    }
}
