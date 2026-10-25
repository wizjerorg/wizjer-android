package org.wizjer.android.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photos sent in chats. They never change, so each is downloaded once (with the account's token),
 * kept in the app's cache folder (deleted on sign-out) and decoded into memory at the size shown.
 */
internal class AiImages(private val dir: File, private val api: AiApi) {

    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Keeps the file of a photo just uploaded, so the chat doesn't download it again. */
    fun put(id: String, bytes: ByteArray) {
        if (!ID.matches(id)) return
        runCatching {
            dir.mkdirs()
            File(dir, id).writeBytes(bytes)
        }
    }

    /** The photo if it's already decoded at [maxSide], for showing it without a flash. */
    fun cached(id: String, maxSide: Int): Bitmap? = memory[key(id, maxSide)]

    /** The photo, at most about [maxSide] pixels on its long edge; null if it can't be had. */
    suspend fun load(id: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (!ID.matches(id)) return@withContext null
        memory[key(id, maxSide)]?.let { return@withContext it }
        val file = File(dir, id)
        if (!file.exists()) {
            val bytes = (api.image(id) as? AiResult.Ok)?.value ?: return@withContext null
            put(id, bytes)
        }
        decode(file, maxSide)?.also { memory.put(key(id, maxSide), it) }
    }

    private fun decode(file: File, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: OutOfMemoryError) {
        null
    }

    private fun key(id: String, maxSide: Int) = "$id@$maxSide"

    companion object {
        private const val MEMORY_BYTES = 24 * 1024 * 1024
        private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        fun dir(context: Context) = File(context.cacheDir, "ai-images")

        fun clear(context: Context) {
            dir(context).deleteRecursively()
        }
    }
}
