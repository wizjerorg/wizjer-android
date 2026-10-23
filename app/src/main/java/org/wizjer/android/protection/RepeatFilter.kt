package org.wizjer.android.protection

/**
 * Spots the same text arriving twice within [windowMillis], e.g. an SMS followed a moment later
 * by the SMS app's notification of it. Remembers only hashes of recent texts, in memory.
 */
class RepeatFilter(
    private val windowMillis: Long = 2 * 60_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val seen = object : LinkedHashMap<Int, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>?) = size > LIMIT
    }

    /** Whether [text] (spacing aside) was seen within the window; remembers it either way. */
    @Synchronized
    fun isRepeat(text: String): Boolean {
        val time = now()
        val last = seen.put(text.trim().replace(Whitespace, " ").hashCode(), time)
        return last != null && time - last < windowMillis
    }

    private companion object {
        const val LIMIT = 200
        val Whitespace = Regex("\\s+")
    }
}
