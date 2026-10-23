package org.wizjer.android.detection.model

import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The model's features, computed exactly as `features()` in wizjer-model's
 * scripts/train_baseline.py, so the weights trained there apply here. A change to one must be
 * made in the other; ScamModelTest checks the scores against messages scored in Python.
 *
 * Python treats \w, \b, \d and \s as Unicode. Java does too on Android (ICU) but not on the JVM
 * that runs the unit tests, so the patterns below spell those classes out. Python also counts
 * characters as code points, so the n-grams and lengths here do too (emoji are two UTF-16 units).
 */
internal object ModelFeatures {

    const val DIM = 1 shl 20

    private const val WORD_CHARS = "\\p{L}\\p{N}_"
    private const val SPACE_CHARS = "\\t\\n\\x0B\\f\\r\\x1C-\\x1F \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000"
    private const val W = "[$WORD_CHARS]"
    private const val B = "(?:(?<=$W)(?!$W)|(?<!$W)(?=$W))"
    private const val D = "\\p{Nd}"
    private const val S = "[$SPACE_CHARS]"
    private const val NOT_S = "[^$SPACE_CHARS]"

    private val WORD = Regex("$W+")
    private val SPACES = Regex("$S+")
    private val URL = Regex(
        "(?:https?://|www\\.)$NOT_S+|$B[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:pl|com|net|org|info|top|site|online|eu|cc|xyz|me|ly|co|" +
            "link|shop|live|cfd|icu|sbs|buzz|vip|help|biz|in|run|io|app|click|pro|club|life|one|sv|to|tk|ru|de|uk|us|lol|cyou|store)$B$NOT_S*",
    )
    private val URL_PREFIX = Regex("^(?:https?://)?(?:www\\.)?")
    private val SHORTENER = Regex(
        "$B(bit\\.ly|t\\.co|tinyurl|cutt\\.ly|is\\.gd|tiny\\.cc|e\\.vg|urlz\\.fr|v\\.ht|rb\\.gy|shorturl\\.at|bit\\.do|ln\\.run|tt\\.vg|" +
            "vo\\.la|did\\.li|goo\\.su|tiny\\.one|rebrand\\.ly|wa\\.me|t\\.me|tiny\\.pl|s\\.id|cli\\.co|x\\.gd|u\\.to|t\\.ly|short\\.gy|goo\\.gl)$B",
    )
    private val SENDER_ADDRESS = Regex("[$WORD_CHARS.+-]+@([$WORD_CHARS-]+(?:\\.[$WORD_CHARS-]+)+)")
    private val SENDER_NUMBER = Regex("[-+\\p{Nd}$SPACE_CHARS]{7,}")
    private val CYRILLIC = Regex("[\\u0370-\\u03FF\\u0400-\\u04FF]")
    private val LATIN = Regex("[a-ząćęłńóśźż]")
    private val AMOUNT = Regex("$D+(?:[.,]$D{2})?$S?(?:zł|zl|pln|eur|€|usd|\\$)")
    private val CODE = Regex("(?<!$D)$D{4,8}(?!$D)")
    private val PHONE = Regex("(?:\\+?48)?[ -]?$D{3}[ -]?$D{3}[ -]?$D{3}$B")
    private val MOJIBAKE = Regex("[ĹÄĂÅÃâ][\\x80-\\u02FF\\u2010-\\u203A]")

    /**
     * Genuine services whose links lead to any person (paypal.me/anyone, wa.me/number): never for or
     * against a brand, and a brand named only inside them isn't a mention.
     */
    private val PERSONAL_LINK_HOSTS = listOf("paypal.me", "revolut.me", "wa.me", "t.me")

    private val FREEMAIL = setOf(
        "gmail.com", "wp.pl", "o2.pl", "onet.pl", "op.pl", "interia.pl", "interia.eu", "poczta.fm", "gazeta.pl", "tlen.pl",
        "outlook.com", "hotmail.com", "live.com", "yahoo.com", "icloud.com", "ukr.net", "i.ua", "meta.ua",
    )

    /** Brands that scams impersonate, and the domains their genuine links use (same order as in Python). */
    private val BRANDS: List<Pair<Regex, List<String>>> = listOf(
        "inpost|paczkomat" to listOf("inpost.pl"), "${B}olx$B" to listOf("olx.pl"), "allegro" to listOf("allegro.pl"),
        "vinted" to listOf("vinted.pl", "vinted.com"), "otomoto" to listOf("otomoto.pl"), "${B}dpd$B" to listOf("dpd.com.pl", "dpd.pl"),
        "${B}dhl$B" to listOf("dhl.com", "dhl.com.pl", "dhl24.com.pl"),
        "poczta polska|pocztex" to listOf("poczta-polska.pl", "pocztex.pl", "envelo.pl"),
        "${B}pko$B|ipko|${B}iko$B" to listOf("pkobp.pl", "ipko.pl"), "mbank" to listOf("mbank.pl"), "${B}ing$B" to listOf("ing.pl"),
        "pekao" to listOf("pekao.com.pl"), "santander|erste" to listOf("santander.pl", "erste.pl"),
        "millennium" to listOf("bankmillennium.pl"), "alior" to listOf("aliorbank.pl"), "${B}blik$B" to listOf("blik.com"),
        "mobywatel|gov\\.pl|profil zaufany|urzad skarbowy|ministerstw|${B}zus$B|${B}kas$B" to
            listOf("gov.pl", "zus.pl", "podatki.gov.pl", "mobywatel.gov.pl"),
        "${B}pge$B" to listOf("pge.pl", "gkpge.pl"), "tauron" to listOf("tauron.pl"), "energa" to listOf("energa.pl"),
        "${B}enea$B" to listOf("enea.pl"), "orange" to listOf("orange.pl"), "${B}play$B" to listOf("play.pl"),
        "${B}plus$B" to listOf("plus.pl"), "t-mobile" to listOf("t-mobile.pl"), "netflix" to listOf("netflix.com"),
        "facebook|messenger" to listOf("facebook.com", "fb.com"), "whatsapp" to listOf("whatsapp.com"),
        "bnp paribas" to listOf("bnpparibas.pl"), "credit agricole" to listOf("credit-agricole.pl"), "velobank" to listOf("velobank.pl"),
        "revolut" to listOf("revolut.com"), "binance" to listOf("binance.com"), "zondacrypto|zonda" to listOf("zondacrypto.com"),
        "booking" to listOf("booking.com"), "e-?toll" to listOf("etoll.gov.pl"), "${B}nfz$B|pacjent\\.gov" to listOf("nfz.gov.pl", "pacjent.gov.pl"),
        "apple|icloud" to listOf("apple.com", "icloud.com"),
        "microsoft|outlook|office 365" to listOf("microsoft.com", "live.com", "office.com", "outlook.com"),
        "paypal" to listOf("paypal.com"), "${B}ups$B" to listOf("ups.com"), "${B}gls$B" to listOf("gls-group.eu", "gls-pl.com"),
        "fedex" to listOf("fedex.com"), "orlen" to listOf("orlen.pl"), "${B}pzu$B" to listOf("pzu.pl"),
        "steam" to listOf("steampowered.com", "steamcommunity.com"), "privatbank|приватбанк" to listOf("privatbank.ua"),
        "monobank" to listOf("monobank.ua"), "nova poshta|нова пошта" to listOf("novaposhta.ua"),
        "ukrposhta|укрпошта" to listOf("ukrposhta.ua"), "${B}diia$B|(додат|застосун|портал)$W* «?дія" to listOf("diia.gov.ua"),
        "ощадбанк|oschadbank" to listOf("oschadbank.ua"),
    ).map { (pattern, domains) -> Regex(pattern) to domains }

    /** Cues scams combine, by name; their order here doesn't matter (features use them sorted). */
    private val CUES: Map<String, Regex> = mapOf(
        "urgent" to "${B}pilne|natychmiast|niezwlocznie|w ciagu (24|48|12|2) ?(h|godz)|ostatni[ae]? (szans|wezwan|przypomn)|do godziny|" +
            "dzisiaj do|termin (uplywa|mija)|терміново|негайно|протягом (24|48|12|2|однієї|доби)|останн$W* (попереджен|нагадуван|шанс)|сьогодні до",
        "threat" to "zablokowan|zawieszon|zostanie (zablok|zawiesz|usunie|anulow|wylacz|odlacz)|komornik|egzekuc|sad$B|do sadu|policj|" +
            "prokurat|odlaczeni|utraci|заблоков|призупин|буде (заблок|видал|скасов|відключ|закрит)|арешт|виконавч|суд$B|судов|поліці|" +
            "прокурат|кримінальн|відключен|втрат(ите|ити)|штраф",
        "pay" to "doplat|zaplac|oplac|uregul|wplac|przelej|platnos|naleznos|zaleglos|kwot[aey] $D|" +
            "оплат|сплат|доплат|переказ|заборгован|борг|передоплат|внес(іть|ти)|переведіть",
        "credentials" to "zaloguj|zweryfikuj|weryfikac|potwierdz (swoje )?dane|zaktualizuj (swoje )?dane|haslo|dane karty|numer karty|" +
            "kod (blik|sms|weryfik|autoryz)|podaj (mi )?(kod|ten kod|haslo)|przepisz (mi )?(go|kod)|(wyslij|przeslij|podeslij) (mi )?(kod|blik|ten kod)|" +
            "potwierdz kodem|увійд|увійти|підтверд$W* (дані|акаунт|особ|картк|свій)|верифікац|пароль|дані (вашої )?картки|номер картки|cvv|" +
            "термін дії картки|код (з|із) (смс|sms)|код підтвердж|баланс картки|скинь$W* (мені )?(код|цифр)|перешл$W* (мені )?(його|код)|повідом$W* код",
        "elsewhere" to "whatsapp|telegram|napisz do mnie|nowy numer|odpisz|odpowiedz (tak|na ten)|zadzwon (pod|na) |" +
            "телеграм|вайбер|ватсап|вотсап|напиш(іть|и) (мені|у|в) |новий номер|пишу з (номера|чужого)|зателефонуй",
        "prize" to "wygral|wygrana|nagrod|gratul|bonus|darmow|prezent|zwrot (podatku|srodkow|pieniedzy)|odbierz|" +
            "виграл|виграш|розіграш|подарун|безкоштовн|бонус|вітаємо|отримайте|компенсаці|виплат$W* (допомог|компенсац|грош)|грошов$W* допомог",
        "attachment" to "w zalacz|zalacz(ony|ona|one|nik)|pobierz (plik|dokument|fakture)|haslo do (pliku|zalacznika|archiwum|faktury|otwarcia)|" +
            "у вкладенн|вкладенн|завантаж$W* (файл|документ)|пароль до (файлу|архів)|\\.(zip|rar|7z|exe|js|vbs|docm|xlsm|iso|img|hta|scr|jar|apk)$B",
        "callback" to "(jesli|jezeli|gdy) (to )?nie (ty|byl|ja|zlecal|dokonal|autoryzow)|nie (zlecal|autoryzowal|rozpoznaj)|" +
            "zadzwon (natychmiast|pilnie|niezwlocznie|teraz)|oddzwon|${B}ref[:. #]|opiekun$W* sprawy|n(r|umer) sprawy|" +
            "(dzial|wydzial|departament)$W* bezpieczenstwa|podejrzan$W* (aktywnos|transakc|logowa|operac|prob)|" +
            "wykryto (logowanie|probe|podejrz|nowe urzadz)|nie rozlaczaj|konsultant$W* (oddzwoni|zadzwoni|skontaktuje)|bezpieczn$W* (rachun|kont)|" +
            "якщо це (були )?не ви|служб$W* безпеки|підозріл$W* (операц|транзакц|вхід|активн|спроб)|спроб$W* входу|очікуйте (на )?дзвінк|" +
            "зателефонує|співробітник$W* (банку|служби)|безпечн$W* рахун|не кладіть слухавку",
        "exposure" to "(twoje|twoich|twoimi|twojego) (przerobion$W* |prywatne |nagie |intymne )?(zdjec|fotk|fotek|nagran|filmik)|" +
            "przer[oa]bi$W* (zdjec|fotk|twoje)|nagie|intymn|kompromit|nagral$W* (cie|ciebie)|wrzucil$W* (twoje|tw)|" +
            "ваші (інтимн$W* |приватн$W* )?(фото|відео|світлин)|інтимн|компромат",
    ).mapValues { Regex(it.value) }

    /**
     * Feature index → value for one message. [source] is "sms", "notification" or "email";
     * [sender] and [subject] may be empty.
     */
    fun features(text: String, source: String, sender: String, subject: String, metaWeight: Double): Map<Int, Double> {
        val normalized = norm(text)
        val plain = withoutPolishLetters(normalized)
        val feats = Counts()

        // Character n-grams on text without Polish letters (so their absence isn't a signal).
        val padded = (" " + plain.replace(SPACES, " ") + " ").codePoints().toArray()
        for (n in 3..5) {
            for (i in 0..padded.size - n) feats.add("c" + String(padded, i, n))
        }
        val words = WORD.findAll(plain).map { it.value }.toList()
        words.forEach { feats.add("w$it") }
        words.zipWithNext().forEach { (a, b) -> feats.add("b${a}_$b") }
        if (subject.isNotEmpty()) {
            WORD.findAll(withoutPolishLetters(norm(subject))).forEach { feats.add("s" + it.value) }
        }
        val hosts = URL.findAll(normalized).map { hostOf(it.value) }.toList()
        for (host in hosts) {
            feats.add("url")
            feats.add("tld." + host.substringAfterLast('.'))
            feats.add("host.parts." + min(host.count { it == '.' }, 4))
            if ('-' in host) feats.add("host.hyphen")
            if (host.any { it.isDigit() }) feats.add("host.digit")
        }
        feats.add("amount", AMOUNT.findAll(normalized).count())
        feats.add("code", CODE.findAll(normalized).count())
        feats.add("phone", PHONE.findAll(normalized).count())
        feats.add("source.$source")
        feats.add("len." + min(normalized.codePointCount(0, normalized.length) / 40, 10))

        // The strongest tells stay outside the length normalisation so long texts don't drown them.
        val meta = Counts()
        if (sender.isNotEmpty()) {
            feats.add("sender." + if (SENDER_NUMBER.matches(sender)) "num" else "name")
            // What an e-mail address says, never whether there is one.
            val address = SENDER_ADDRESS.find(sender.lowercase())
            if (address != null) {
                val domain = address.groupValues[1]
                feats.add("sender.tld." + domain.substringAfterLast('.'))
                if (domain in FREEMAIL) feats.add("sender.freemail")
                if ('-' in domain) feats.add("sender.hyphen")
                if (domain.any { it.isDigit() }) feats.add("sender.digit")
                val display = withoutPolishLetters(norm(sender.substring(0, min(address.range.first, sender.length))))
                val claimed = brandDomains(display)
                if (claimed.isNotEmpty()) {
                    val own = claimed.any { domain == it || domain.endsWith(".$it") }
                    meta.add(if (own) "sender.brand.own" else "sender.brand.other")
                }
            }
        }
        val official = brandDomains(URL.replace(plain) { if (isPersonal(hostOf(it.value))) " " else it.value })
        val branded = hosts.filterNot(::isPersonal)
        if (official.isNotEmpty() && branded.isNotEmpty()) {
            // A brand named in the text with a link elsewhere is the classic phishing tell.
            val own = branded.all { host -> official.any { host == it || host.endsWith(".$it") } }
            meta.add(if (own) "brand.link.own" else "brand.link.other")
        }
        meta.add("mixed.script", normalized.split(SPACES).count { it.isNotEmpty() && CYRILLIC.containsMatchIn(it) && LATIN.containsMatchIn(it) })
        meta.add("shortener", SHORTENER.findAll(normalized).count())
        val cues = CUES.filterValues { it.containsMatchIn(plain) }.keys.sorted()
        for (name in cues) {
            feats.add("cue.$name")
            if (hosts.isNotEmpty()) feats.add("cue.link.$name")
        }
        for (i in cues.indices) for (j in i + 1 until cues.size) feats.add("cue.${cues[i]}+${cues[j]}")
        feats.add("cue.count." + min(cues.size, 4))

        val out = feats.hashed()
        val scale = 1.0 / sqrt(out.values.sumOf { it * it }.takeIf { it != 0.0 } ?: 1.0)
        out.replaceAll { _, value -> value * scale }
        meta.hashed().forEach { (index, value) -> out[index] = (out[index] ?: 0.0) + metaWeight * value }
        return out
    }

    /** Feature name → index, as Python's int.from_bytes(blake2b(name, digest_size=8), "little") % DIM. */
    fun index(name: String): Int = (Blake2b.first64(name.toByteArray(Charsets.UTF_8)) and (DIM - 1).toLong()).toInt()

    /** Python's unicodedata.normalize("NFKC", fix_mojibake(text)).lower(). */
    fun norm(text: String): String = Normalizer.normalize(fixMojibake(text), Normalizer.Form.NFKC).lowercase()

    private fun withoutPolishLetters(text: String): String = buildString(text.length) {
        for (c in text) append(
            when (c) {
                'ą' -> 'a'; 'ć' -> 'c'; 'ę' -> 'e'; 'ł' -> 'l'; 'ń' -> 'n'; 'ó' -> 'o'; 'ś' -> 's'; 'ź', 'ż' -> 'z'
                else -> c
            },
        )
    }

    private fun hostOf(url: String): String =
        url.replaceFirst(URL_PREFIX, "").substringBefore('/').trimEnd('.', ',', ':', ';', '!', '?', ')')

    private fun isPersonal(host: String): Boolean = PERSONAL_LINK_HOSTS.any { host == it || host.endsWith(".$it") }

    private fun brandDomains(text: String): List<String> = BRANDS.filter { (pattern, _) -> pattern.containsMatchIn(text) }.flatMap { it.second }

    /** Repairs UTF-8 text shown in a single-byte code page ("CzeĹ›Ä‡" → "Cześć"), word by word. */
    private fun fixMojibake(text: String): String {
        if (!MOJIBAKE.containsMatchIn(text)) return text
        return text.split(" ").joinToString(" ") { word -> if (MOJIBAKE.containsMatchIn(word)) recode(word) else word }
    }

    private fun recode(word: String): String {
        for (name in listOf("windows-1250", "windows-1252", "ISO-8859-1")) {
            try {
                val bytes = Charset.forName(name).newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(word))
                return Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(bytes).toString()
            } catch (_: CharacterCodingException) {
                continue
            } catch (_: IllegalArgumentException) {
                continue // charset not available on this device
            }
        }
        return word
    }

    /** Python's Counter, in insertion order (it decides the order of the floating-point sums). */
    private class Counts {
        private val counts = LinkedHashMap<String, Int>()

        fun add(name: String, count: Int = 1) {
            counts[name] = (counts[name] ?: 0) + count
        }

        /** Python's hashed_counts: 1 + ln(count) per name, summed where names share an index. */
        fun hashed(): LinkedHashMap<Int, Double> {
            val out = LinkedHashMap<Int, Double>()
            for ((name, count) in counts) {
                if (count > 0) {
                    val index = index(name)
                    out[index] = (out[index] ?: 0.0) + (1.0 + ln(count.toDouble()))
                }
            }
            return out
        }
    }
}
