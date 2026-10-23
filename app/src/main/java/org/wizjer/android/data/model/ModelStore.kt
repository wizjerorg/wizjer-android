package org.wizjer.android.data.model

import android.content.Context
import org.wizjer.android.detection.model.ScamModel
import java.io.File

/**
 * The AI model in use (about 2 MB): the one bundled with the app, or a newer one downloaded from
 * the wizjer-model releases on GitHub ([ModelUpdates]). Read once, on first use; [install] swaps
 * in a newer one, which the detectors use from their next check.
 */
object ModelStore {

    /** The release the bundled model came from, e.g. "model-2026.10.23". */
    private const val VERSION_ASSET = "model/version.txt"

    private class Loaded(val model: ScamModel, val version: String?)

    @Volatile
    private var current: Loaded? = null

    @Volatile
    private var failed = false

    /** The model, or null if none can be read; the detector then relies on its rules alone. */
    fun get(context: Context): ScamModel? = loaded(context)?.model

    /** The release of the model in use, if known. */
    fun version(context: Context): String? = loaded(context)?.version

    fun bundledVersion(context: Context): String? =
        runCatching { context.assets.open(VERSION_ASSET).bufferedReader().use { it.readText().trim() } }.getOrNull()

    /** Switches to a newer model that has passed [ModelCheck]. */
    internal fun install(model: ScamModel, version: String) = synchronized(this) {
        current = Loaded(model, version)
        failed = false
    }

    private fun loaded(context: Context): Loaded? {
        current?.let { return it }
        if (failed) return null
        return synchronized(this) {
            current ?: load(context.applicationContext).also { if (it == null) failed = true else current = it }
        }
    }

    /** A downloaded model wins only if it's newer than the bundled one (an app update may bring a newer one). */
    private fun load(context: Context): Loaded? {
        val bundled = bundledVersion(context)
        val downloaded = ModelUpdates.downloaded(context)
            ?.takeIf { (version, _) -> ModelCheck.isNewer(version, than = bundled) }
            ?.let { (version, file) -> read(file)?.let { Loaded(it, version) } }
        return downloaded
            ?: runCatching { context.assets.open(ScamModel.ASSET).use { ScamModel.read(it) } }.getOrNull()?.let { Loaded(it, bundled) }
    }

    private fun read(file: File): ScamModel? =
        runCatching { file.inputStream().use { ScamModel.read(it) } }.getOrNull()?.takeIf(ModelCheck::passes)
}
