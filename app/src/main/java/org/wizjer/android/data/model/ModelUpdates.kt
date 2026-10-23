package org.wizjer.android.data.model

import android.content.Context
import android.util.JsonReader
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wizjer.android.detection.model.ScamModel
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * New models from the open wizjer-model repository. Once a day the app asks GitHub for its latest
 * release; when that's newer than the model in use, it downloads the model file, checks it
 * ([ModelCheck]) and switches to it. Only the release and the model are fetched: nothing about
 * the user or their messages is sent.
 */
object ModelUpdates {

    enum class Result { Updated, UpToDate, Failed }

    /** A release: its tag and, if it has one, the model file in the app's format. */
    data class Release(val tag: String, val modelUrl: String?, val modelSize: Long)

    private const val LATEST_RELEASE = "https://api.github.com/repos/wizjerorg/wizjer-model/releases/latest"

    /** Model files are taken only from the repository's own releases. */
    private const val DOWNLOADS = "https://github.com/wizjerorg/wizjer-model/releases/download/"
    const val MODEL_ASSET = "wizjer-baseline.bin"
    private const val MAX_BYTES = 8L * 1024 * 1024

    private const val KEY_VERSION = "version"
    private const val KEY_ETAG = "etag"

    /** The downloaded model and its release, if there is one. */
    fun downloaded(context: Context): Pair<String, File>? {
        val version = prefs(context).getString(KEY_VERSION, null) ?: return null
        return fileOf(context).takeIf { it.isFile }?.let { version to it }
    }

    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        val prefs = prefs(context)
        val connection = (URL(LATEST_RELEASE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Wizjer-Android")
            prefs.getString(KEY_ETAG, null)?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            val release = when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> return@withContext Result.UpToDate
                HttpURLConnection.HTTP_NOT_FOUND -> return@withContext Result.UpToDate // nothing released yet
                HttpURLConnection.HTTP_OK -> connection.inputStream.use(::parseRelease)
                else -> return@withContext Result.Failed
            }
            val result = if (ModelCheck.isNewer(release.tag, than = ModelStore.version(context))) install(context, release) else Result.UpToDate
            // Remember the answer only once it's been acted on, so a failed download is retried.
            if (result != Result.Failed) prefs.edit { putString(KEY_ETAG, connection.getHeaderField("ETag")) }
            result
        } catch (_: IOException) {
            Result.Failed
        } catch (_: IllegalStateException) {
            Result.Failed // malformed release info
        } finally {
            connection.disconnect()
        }
    }

    private fun install(context: Context, release: Release): Result {
        val url = release.modelUrl?.takeIf { it.startsWith(DOWNLOADS) && release.modelSize in 1..MAX_BYTES } ?: return Result.Failed
        val file = fileOf(context)
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            file.parentFile?.mkdirs()
            download(url, temp)
            val model = runCatching { temp.inputStream().use { ScamModel.read(it) } }.getOrNull()
            if (model == null || !ModelCheck.passes(model) || !temp.renameTo(file)) return Result.Failed
            prefs(context).edit { putString(KEY_VERSION, release.tag) }
            ModelStore.install(model, release.tag)
            return Result.Updated
        } finally {
            temp.delete()
        }
    }

    private fun download(url: String, into: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Wizjer-Android")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.use { input ->
                into.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw IOException("model file too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** GitHub's `{ "tag_name": …, "assets": [ { "name", "browser_download_url", "size" }, … ] }`. */
    internal fun parseRelease(body: InputStream): Release {
        var tag: String? = null
        var url: String? = null
        var size = 0L
        JsonReader(body.reader(Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "tag_name" -> tag = reader.nextString()
                    "assets" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            var name: String? = null
                            var assetUrl: String? = null
                            var assetSize = 0L
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "name" -> name = reader.nextString()
                                    "browser_download_url" -> assetUrl = reader.nextString()
                                    "size" -> assetSize = reader.nextLong()
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                            if (name == MODEL_ASSET) {
                                url = assetUrl
                                size = assetSize
                            }
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }
        return Release(checkNotNull(tag) { "release without a tag" }, url, size)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("model", Context.MODE_PRIVATE)

    private fun fileOf(context: Context) = File(File(context.noBackupFilesDir, "model"), MODEL_ASSET)
}

/** Checks for a newer model once a day. */
class ModelUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (ModelUpdates.check(applicationContext)) {
        ModelUpdates.Result.Failed -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        else -> Result.success()
    }

    companion object {
        private const val MAX_ATTEMPTS = 3

        /** Safe to call on every launch. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ModelUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("model_updates", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
