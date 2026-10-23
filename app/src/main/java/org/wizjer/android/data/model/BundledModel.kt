package org.wizjer.android.data.model

import android.content.Context
import org.wizjer.android.detection.model.ScamModel

/** The AI model bundled with the app (about 2 MB), read from its assets once, on first use. */
object BundledModel {

    @Volatile
    private var model: ScamModel? = null

    @Volatile
    private var failed = false

    /** The model, or null if it can't be read; the detector then relies on its rules alone. */
    fun get(context: Context): ScamModel? {
        model?.let { return it }
        if (failed) return null
        return synchronized(this) {
            model ?: runCatching { context.applicationContext.assets.open(ScamModel.ASSET).use { ScamModel.read(it) } }
                .onFailure { failed = true }
                .getOrNull()
                ?.also { model = it }
        }
    }
}
