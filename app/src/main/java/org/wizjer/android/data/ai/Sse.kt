package org.wizjer.android.data.ai

/** One Server-Sent Event: the last id the stream gave, its type and its data. */
data class SseEvent(val id: String?, val type: String, val data: String)

/**
 * Reads a `text/event-stream` line by line (the lines without their ending). Comments such as the
 * `: ping` heartbeat, `retry:` and unknown fields are skipped; several `data:` lines are joined with
 * newlines; an event is complete at the blank line after it. Not thread-safe: one parser per stream.
 */
class SseParser {
    private var lastId: String? = null
    private var type: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** Feeds one line; returns the event it completes, if any. */
    fun line(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
        when (field) {
            "id" -> if ('\u0000' !in value) lastId = value
            "event" -> type = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    private fun dispatch(): SseEvent? {
        val event = if (hasData) SseEvent(lastId, type ?: "message", data.toString()) else null
        type = null
        data.setLength(0)
        hasData = false
        return event
    }
}
