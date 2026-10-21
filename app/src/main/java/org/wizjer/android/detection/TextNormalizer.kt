package org.wizjer.android.detection

import java.text.Normalizer
import java.util.Locale

/**
 * @property clean readable text with tricks undone (used for extraction and display)
 * @property folded [clean] in lower case without diacritics (used for matching phrases)
 * @property obfuscated whether the original used tricks to dodge filters
 */
data class NormalizedText(val clean: String, val folded: String, val obfuscated: Boolean)

/** Undoes the tricks scammers use to slip past filters. */
object TextNormalizer {

    private val invisible = Regex("[\\u00AD\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]")

    /** "d o p ł a t a" → "dopłata" (four or more single letters separated by single spaces). */
    private val spacedLetters = Regex("(?<!\\p{L})\\p{L}(?: \\p{L}){3,}(?!\\p{L})")

    /**
     * "inpost[.]pl", "inpost(dot)pl", "inpost kropka pl" → "inpost.pl".
     * Literal brackets and braces are all escaped: Android's regex engine (ICU) rejects a bare "]" or "}".
     */
    private val disguisedDots = Regex(
        "(?<=[\\p{L}\\p{N}])\\s*(?:\\[\\.\\]|\\(\\.\\)|\\{\\.\\}|\\[dot\\]|\\(dot\\)|\\s+kropka\\s+)\\s*(?=[\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE,
    )
    private val disguisedScheme = Regex("\\bhxxp(s?)://", RegexOption.IGNORE_CASE)
    private val word = Regex("[\\p{L}\\p{N}]+")
    private val whitespace = Regex("[\\t\\x0B\\f\\r ]+")
    private val combiningMarks = Regex("\\p{Mn}+")

    /** Cyrillic letters that look like Latin ones, used to disguise words like "іnpost". */
    private val cyrillicLookalikes = mapOf(
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x',
        'і' to 'i', 'ј' to 'j', 'ѕ' to 's', 'к' to 'k', 'м' to 'm', 'т' to 't', 'в' to 'b', 'н' to 'h',
        'А' to 'A', 'Е' to 'E', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'У' to 'Y', 'Х' to 'X',
        'І' to 'I', 'Ј' to 'J', 'Ѕ' to 'S', 'К' to 'K', 'М' to 'M', 'Т' to 'T', 'В' to 'B', 'Н' to 'H',
    )

    fun normalize(text: String): NormalizedText {
        var obfuscated = false

        // Full-width and stylised letters ("ｉｎｐｏｓｔ", "𝐢𝐧𝐩𝐨𝐬𝐭") become plain ones.
        var clean = Normalizer.normalize(text, Normalizer.Form.NFKC)

        if (invisible.containsMatchIn(clean)) {
            obfuscated = true
            clean = invisible.replace(clean, "")
        }
        clean = disguisedScheme.replace(clean) { "http${it.groupValues[1]}://" }
        clean = disguisedDots.replace(clean, ".")
        if (spacedLetters.containsMatchIn(clean)) {
            obfuscated = true
            clean = spacedLetters.replace(clean) { it.value.replace(" ", "") }
        }
        // Words mixing Latin and Cyrillic are disguises; fully Cyrillic words (Ukrainian) are left alone.
        clean = word.replace(clean) { match ->
            val w = match.value
            val mixed = w.any { it in 'a'..'z' || it in 'A'..'Z' } && w.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC }
            if (mixed) {
                obfuscated = true
                w.map { cyrillicLookalikes[it] ?: it }.joinToString("")
            } else {
                w
            }
        }
        clean = whitespace.replace(clean, " ").trim()

        return NormalizedText(clean = clean, folded = fold(clean), obfuscated = obfuscated)
    }

    /** Lower case without diacritics: "Dopłać" → "doplac", "Здоров'я" → "здоров'я". */
    fun fold(text: String): String {
        val lower = text.lowercase(Locale.ROOT).replace('ł', 'l')
        return combiningMarks.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
    }
}
