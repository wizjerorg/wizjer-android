package org.wizjer.android.ui.home

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.wizjer.android.R
import java.util.Locale

/**
 * Reads replies aloud with the phone's own text-to-speech, in the app's language. One reply at a
 * time: [speaking] is its key, and toggling it again stops. Nothing leaves the phone for this.
 */
@Stable
class ReadAloud internal constructor(private val context: Context, private val unavailableMessage: String) {
    var speaking: String? by mutableStateOf(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    fun toggle(key: String, text: String, languageTag: String) {
        if (speaking == key) return stop()
        speaking = key
        val speak = { speak(key, text, Locale.forLanguageTag(languageTag)) }
        if (ready) {
            speak()
        } else {
            pending = speak
            if (engine == null) engine = TextToSpeech(context, ::onInit)
        }
    }

    fun stop() {
        pending = null
        engine?.stop()
        speaking = null
    }

    internal fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun onInit(status: Int) = main.post {
        val tts = engine ?: return@post
        if (status != TextToSpeech.SUCCESS) {
            unavailable()
            return@post
        }
        ready = true
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finished(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        })
        pending?.invoke()
        pending = null
    }

    /** The last piece of a reply ends with the reply's own key. */
    private fun finished(utteranceId: String?) {
        main.post { if (utteranceId != null && utteranceId == speaking) speaking = null }
    }

    private fun speak(key: String, text: String, locale: Locale) {
        val tts = engine ?: return
        val support = tts.setLanguage(locale)
        if (support == TextToSpeech.LANG_MISSING_DATA || support == TextToSpeech.LANG_NOT_SUPPORTED) return unavailable()
        val pieces = pieces(plainReply(text, links = false), TextToSpeech.getMaxSpeechInputLength() - 100)
        pieces.forEachIndexed { index, piece ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(piece, mode, null, if (index == pieces.lastIndex) key else "$key#$index")
        }
    }

    private fun unavailable() {
        speaking = null
        pending = null
        Toast.makeText(context, unavailableMessage, Toast.LENGTH_LONG).show()
    }

    /** Splits [text] at paragraph and sentence ends into pieces the engine accepts. */
    private fun pieces(text: String, limit: Int): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        text.split(Regex("(?<=[.!?\\n])\\s+")).forEach { sentence ->
            if (current.length + sentence.length + 1 > limit && current.isNotEmpty()) {
                result += current.toString()
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence.take(limit))
        }
        if (current.isNotBlank()) result += current.toString()
        return result
    }
}

/** A [ReadAloud] that lives as long as the screen and stops talking when it closes. */
@Composable
fun rememberReadAloud(): ReadAloud {
    val context = LocalContext.current
    // Read in the app's language here: the application context may not follow the in-app language.
    val unavailable = stringResource(R.string.ai_read_unavailable)
    val readAloud = remember { ReadAloud(context.applicationContext, unavailable) }
    DisposableEffect(readAloud) { onDispose { readAloud.shutdown() } }
    return readAloud
}
