package org.wizjer.android.data.lists

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.wizjer.android.data.api.ApiClient
import org.wizjer.android.detection.lists.SiteIndex
import org.wizjer.android.detection.lists.SiteList
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Lists downloaded separately: gambling is large and matters least, so it's fetched less often;
 * [RecentCert] is what CERT added since the last [Main] download, fetched every few minutes.
 */
enum class ListSource(internal val path: String, val lists: Set<SiteList>, internal val incremental: Boolean = false) {
    Main("/data/flagged_sites", setOf(SiteList.Cert, SiteList.Shortener, SiteList.Adult, SiteList.Piracy)),
    Gambling("/data/flagged_sites/gambling", setOf(SiteList.Gambling)),
    RecentCert("/data/flagged_sites/cert", setOf(SiteList.Cert), incremental = true),
}

/**
 * The threat lists on this phone. They're downloaded whole and every link is checked
 * against them locally: nothing about your messages is sent anywhere.
 */
class ThreatLists private constructor(context: Context) {

    enum class UpdateResult { Updated, Unchanged, Failed }

    private val dir = File(context.noBackupFilesDir, "threat_lists")
    private val prefs = context.getSharedPreferences("threat_lists", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(
        ListSource.entries.mapNotNull { source -> SiteIndex.open(fileOf(source))?.let { source to it } }.toMap(),
    )

    /** The lists in use, per download; empty until the first one arrives. */
    val loaded: StateFlow<Map<ListSource, SiteIndex>> = current.asStateFlow()

    /** When the server was last asked for [source], successfully (epoch ms; 0 if never). */
    fun checkedAt(source: ListSource): Long = prefs.getLong("${source.name}.checked_at", 0)

    suspend fun update(source: ListSource, api: ApiClient = ApiClient()): UpdateResult = withContext(Dispatchers.IO) {
        val file = fileOf(source)
        val temp = File(dir, "${file.name}.tmp")
        val path = if (source.incremental) {
            // Everything CERT added since the full download, with an hour's overlap so nothing
            // added out of order is missed. Without a full download there's nothing to add to.
            val newest = prefs.getLong(KEY_NEWEST_CERT, 0).takeIf { it > 0 && ListSource.Main in current.value }
                ?: return@withContext UpdateResult.Unchanged
            val since = Instant.ofEpochMilli(newest - TimeUnit.HOURS.toMillis(1)).toString()
            "${source.path}?since=${URLEncoder.encode(since, "UTF-8")}"
        } else {
            source.path
        }
        val etag = prefs.getString("${source.name}.etag", null)?.takeIf { source in current.value && !source.incremental }
        try {
            dir.mkdirs()
            val result = api.stream(path, etag) { response ->
                when {
                    response.status == 304 -> UpdateResult.Unchanged
                    !response.isSuccess || response.body == null -> UpdateResult.Failed
                    else -> {
                        val builder = SiteIndex.Builder()
                        val newestCert = read(response.body, builder)
                        // Never replace a full list with nothing (an empty update is fine).
                        if (builder.isEmpty && !source.incremental) return@stream UpdateResult.Failed
                        builder.writeTo(temp, System.currentTimeMillis())
                        prefs.edit {
                            putString("${source.name}.etag", response.etag)
                            if (source == ListSource.Main && newestCert != null) putLong(KEY_NEWEST_CERT, newestCert)
                        }
                        UpdateResult.Updated
                    }
                }
            }
            if (result == UpdateResult.Updated) {
                check(temp.renameTo(file))
                val index = checkNotNull(SiteIndex.open(file))
                current.update { it + (source to index) }
            }
            if (result != UpdateResult.Failed) prefs.edit { putLong("${source.name}.checked_at", System.currentTimeMillis()) }
            result
        } catch (_: IOException) {
            UpdateResult.Failed
        } catch (_: IllegalStateException) {
            UpdateResult.Failed // malformed response or a failed rename
        } finally {
            temp.delete()
        }
    }

    private fun fileOf(source: ListSource) = File(dir, "${source.name.lowercase()}.idx")

    /**
     * Streams `{ "<list>": [ "domain" | { "domain", "added" }, … ], … }` into [builder]; unknown
     * lists are skipped. Returns when the newest CERT entry was added (epoch ms), if known.
     */
    private fun read(body: InputStream, builder: SiteIndex.Builder): Long? {
        var newestCert: Long? = null
        JsonReader(body.reader(Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) {
                val list = LISTS[reader.nextName()]
                if (list == null) {
                    reader.skipValue()
                    continue
                }
                reader.beginArray()
                while (reader.hasNext()) {
                    when (reader.peek()) {
                        JsonToken.STRING -> builder.add(list, reader.nextString())
                        JsonToken.BEGIN_OBJECT -> {
                            val (domain, added) = readEntry(reader)
                            domain?.let { builder.add(list, it) }
                            if (list == SiteList.Cert && added != null) newestCert = maxOf(newestCert ?: added, added)
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endArray()
            }
            reader.endObject()
        }
        return newestCert
    }

    /** `{ "domain": …, "added": <ISO date> }` → the domain and when it was added (epoch ms). */
    private fun readEntry(reader: JsonReader): Pair<String?, Long?> {
        var domain: String? = null
        var added: Long? = null
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            when {
                reader.peek() != JsonToken.STRING -> reader.skipValue()
                name == "domain" -> domain = reader.nextString()
                name == "added" -> added = runCatching { OffsetDateTime.parse(reader.nextString()).toInstant().toEpochMilli() }.getOrNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return domain to added
    }

    companion object {
        private const val KEY_NEWEST_CERT = "Main.newest_cert"

        private val LISTS = mapOf(
            "cert" to SiteList.Cert,
            "urlshortener" to SiteList.Shortener,
            "gambling" to SiteList.Gambling,
            "nsfw" to SiteList.Adult,
            "piracy" to SiteList.Piracy,
        )

        @Volatile
        private var instance: ThreatLists? = null

        fun get(context: Context): ThreatLists =
            instance ?: synchronized(this) { instance ?: ThreatLists(context.applicationContext).also { instance = it } }
    }
}
