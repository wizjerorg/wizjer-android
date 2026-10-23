package org.wizjer.android.protection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.wizjer.android.detection.Analysis
import kotlin.random.Random

/** A message background protection raised an alert about; [id] is also the alert's. */
data class FlaggedMessage(val id: Int, val message: IncomingMessage, val analysis: Analysis)

/**
 * The messages that raised an alert, newest first, so the alert can open them in the app. Kept in
 * memory only, like every message: they're gone once the app's process ends.
 */
object AlertLog {
    private const val LIMIT = 20

    private val _flagged = MutableStateFlow<List<FlaggedMessage>>(emptyList())
    val flagged: StateFlow<List<FlaggedMessage>> = _flagged.asStateFlow()

    private val _opened = MutableStateFlow<Int?>(null)

    /** The alert the user just tapped, until the app has shown it. */
    val opened: StateFlow<Int?> = _opened.asStateFlow()

    /** Random ids, so an alert left from before a restart never opens a newer message. */
    fun add(message: IncomingMessage, analysis: Analysis): FlaggedMessage =
        FlaggedMessage(Random.nextInt(1, Int.MAX_VALUE), message, analysis).also { flagged ->
            _flagged.update { (listOf(flagged) + it).take(LIMIT) }
        }

    fun find(id: Int): FlaggedMessage? = _flagged.value.firstOrNull { it.id == id }

    fun open(id: Int) {
        _opened.value = id
    }

    fun shown() {
        _opened.value = null
    }
}
