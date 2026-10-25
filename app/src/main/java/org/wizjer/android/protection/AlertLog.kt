package org.wizjer.android.protection

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.wizjer.android.detection.Analysis
import org.wizjer.android.detection.Reason
import org.wizjer.android.detection.RiskLevel
import org.wizjer.android.detection.ScamType
import org.wizjer.android.detection.Signal
import java.io.File
import kotlin.random.Random

/** A message background protection raised an alert about; [id] is also the alert's. */
data class FlaggedMessage(val id: Int, val message: IncomingMessage, val analysis: Analysis)

/**
 * The last [LIMIT] messages that raised an alert, newest first, so the alert (and the protection
 * screen) can open them again. Kept in the app's private storage on this phone, excluded from
 * backups, and deleted when the user logs out; checked messages that raised no alert are never stored.
 */
object AlertLog {
    const val LIMIT = 5
    private const val FILE = "alerts.json"

    private val _flagged = MutableStateFlow<List<FlaggedMessage>>(emptyList())
    val flagged: StateFlow<List<FlaggedMessage>> = _flagged.asStateFlow()

    private val _opened = MutableStateFlow<Int?>(null)

    /** The alert the user just tapped, until the app has shown it. */
    val opened: StateFlow<Int?> = _opened.asStateFlow()

    @Volatile
    private var restored = false

    /** Reads the saved alerts once per process; cheap to call again. */
    fun restore(context: Context) {
        if (restored) return
        synchronized(this) {
            if (restored) return
            _flagged.value = runCatching { read(fileOf(context).readText()) }.getOrDefault(emptyList())
            restored = true
        }
    }

    /** Random ids, so an alert left from before a restart never opens a newer message. */
    fun add(context: Context, message: IncomingMessage, analysis: Analysis): FlaggedMessage = synchronized(this) {
        restore(context)
        val flagged = FlaggedMessage(Random.nextInt(1, Int.MAX_VALUE), message, analysis)
        _flagged.value = (listOf(flagged) + _flagged.value).take(LIMIT)
        runCatching { save(context, write(_flagged.value)) }
        flagged
    }

    /** Written aside, then swapped in: a phone switched off mid-write never leaves a broken list. */
    private fun save(context: Context, json: String) {
        val file = fileOf(context)
        val temp = File(file.parentFile, "$FILE.tmp")
        temp.writeText(json)
        if (!temp.renameTo(file)) {
            file.writeText(json)
            temp.delete()
        }
    }

    fun find(id: Int): FlaggedMessage? = _flagged.value.firstOrNull { it.id == id }

    /** Forgets every saved alert (when the user logs out). */
    fun clear(context: Context) = synchronized(this) {
        _flagged.value = emptyList()
        fileOf(context).delete()
        restored = true
    }

    fun open(id: Int) {
        _opened.value = id
    }

    fun shown() {
        _opened.value = null
    }

    private fun fileOf(context: Context) = File(context.noBackupFilesDir, FILE)

    /** What the alert screen needs: the message, the verdict and its reasons (not the extracted details). */
    internal fun write(alerts: List<FlaggedMessage>): String = JSONArray(
        alerts.map { (id, message, analysis) ->
            JSONObject()
                .put("id", id)
                .put("source", message.source.name)
                .put("sender", message.sender)
                .put("subject", message.subject)
                .put("text", message.text)
                .put("app", message.appPackage)
                .put("receivedAt", message.receivedAt)
                .put("level", analysis.level.name)
                .put("risk", analysis.risk)
                .put("confidence", analysis.confidence)
                .put("type", analysis.primaryType?.name)
                .put("language", analysis.language)
                .put("detector", analysis.detectorVersion)
                .put("modelUsed", analysis.modelUsed)
                .put("listsUsed", analysis.listsUsed)
                .put("reasons", JSONArray(analysis.reasons.map { JSONObject().put("signal", it.signal.name).put("detail", it.detail) }))
        },
    ).toString()

    /** Alerts saved by an older app version with signals it no longer knows are skipped, not fatal. */
    internal fun read(json: String): List<FlaggedMessage> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            try {
                val o = array.getJSONObject(i)
                val message = IncomingMessage(
                    source = MessageSource.valueOf(o.getString("source")),
                    sender = o.optString("sender").takeIf { o.has("sender") },
                    text = o.getString("text"),
                    appPackage = o.optString("app").takeIf { o.has("app") },
                    receivedAt = o.getLong("receivedAt"),
                    subject = o.optString("subject").takeIf { o.has("subject") },
                )
                val reasons = o.getJSONArray("reasons").let { r ->
                    (0 until r.length()).map { j ->
                        val reason = r.getJSONObject(j)
                        Reason(Signal.valueOf(reason.getString("signal")), reason.optString("detail").takeIf { reason.has("detail") })
                    }
                }
                val analysis = Analysis(
                    risk = o.getInt("risk"),
                    level = RiskLevel.valueOf(o.getString("level")),
                    confidence = o.getInt("confidence"),
                    primaryType = o.optString("type").takeIf { o.has("type") }?.let(ScamType::valueOf),
                    types = emptyList(),
                    reasons = reasons,
                    entities = emptyList(),
                    normalizedText = message.text,
                    language = o.getString("language"),
                    timings = emptyList(),
                    detectorVersion = o.getString("detector"),
                    modelUsed = o.getBoolean("modelUsed"),
                    listsUsed = o.getBoolean("listsUsed"),
                )
                FlaggedMessage(o.getInt("id"), message, analysis)
            } catch (_: JSONException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
