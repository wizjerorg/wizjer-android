package org.wizjer.android.detection

/**
 * Phrases, brands and domain data the rules use. Phrases are written naturally (with
 * diacritics) and folded once at startup, so "dopłać", "doplac" and "DOPŁAĆ" all match.
 * Each phrase must start at a word boundary; many are stems ("zablokowan" → zablokowane/-y/-a).
 */
internal object Lexicon {

    val urgency = phrases(
        // pl
        "pilne", "pilnie", "natychmiast", "niezwłocznie", "w ciągu 24", "w ciągu 2 godzin", "24h", "24 h",
        "dziś do", "dzisiaj do", "ostatnie ostrzeżenie", "ostateczne wezwanie", "termin mija", "jak najszybciej",
        "do końca dnia", "ostatnia szansa", "tylko dziś",
        // en
        "urgent", "immediately", "within 24", "final notice", "last warning", "as soon as possible", "act now", "today only",
        // uk
        "терміново", "негайно", "протягом 24", "останнє попередження", "лише сьогодні",
    )

    val payment = phrases(
        "dopłat", "dopłać", "zapłać", "opłać", "opłata", "opłaty", "uregul", "wpłać", "wpłaty", "przelew", "przelej",
        "należność", "brak wpłaty", "niedopłat", "koszt dostawy", "wyślij pieniądze", "prześlij pieniądze", "pożycz",
        "potrzebuję pieniędzy", "na konto", "na rachunek", "zwrot kosztów",
        "pay now", "payment", "fee", "outstanding balance", "send money", "transfer the",
        "оплат", "доплат", "сплат", "переказ", "надішли гроші", "позич",
    )

    val credentials = phrases(
        "blik", "podaj kod", "kod sms", "kod weryfikacyjny", "przepisz kod", "hasło", "pin", "cvv", "dane karty",
        "numer karty", "dane logowania", "dane do logowania", "zaloguj się", "zweryfikuj", "potwierdź dane",
        "weryfikacja konta", "weryfikacji konta",
        "password", "verification code", "card details", "card number", "login details", "verify your", "log in to",
        "пароль", "код з смс", "дані картки", "номер картки", "підтвердіть дані", "увійдіть",
    )

    val threat = phrases(
        "zablokowan", "zablokuj", "zawieszon", "wstrzyman", "odłączeni", "odłączenie", "odcięci", "egzekucj",
        "komornik", "konsekwencje prawne", "nakaz zapłaty", "dezaktywowan", "blokada", "usunięcie konta",
        "blocked", "suspended", "locked", "disconnected", "legal action", "will be closed", "deactivated",
        "заблоковано", "заблокован", "призупинен", "відключенн", "блокуванн",
    )

    val authority = phrases(
        "bank", "banku", "policja", "policji", "prokuratur", "urząd skarbowy", "skarbówk", "ministerstw",
        "dział bezpieczeństwa", "departament bezpieczeństwa", "konsultant",
        "police", "tax office", "security department", "government",
        "банк", "поліці", "податков", "служба безпеки",
    )

    val safeAccount = phrases(
        "bezpieczne konto", "bezpiecznego konta", "bezpiecznym koncie", "konto techniczne", "konta technicznego",
        "safe account", "безпечний рахунок", "безпечного рахунку",
    )

    val prize = phrases(
        "wygrał", "wygrana", "wygraną", "nagrod", "gratulacje", "voucher", "bon o wartości", "za darmo", "losowani",
        "you won", "winner", "prize", "congratulations", "free gift",
        "виграш", "вітаємо", "приз", "безкоштовн",
    )

    val family = phrases(
        "mamo", "tato", "babciu", "dziadku", "nowy numer", "mój nowy numer", "zgubiłem telefon", "zgubiłam telefon",
        "zepsuł mi się telefon", "miałem wypadek", "miałam wypadek", "to ja, twój", "to ja, twoja",
        "mum,", "mom,", "dad,", "new number", "lost my phone",
        "мамо", "тато", "новий номер", "загубив телефон", "загубила телефон",
    )

    val investment = phrases(
        "inwestyc", "gwarantowany zysk", "gwarantowany zwrot", "pasywny dochód", "kryptowalut", "bitcoin", "krypto",
        "forex", "platforma inwestycyjna",
        "investment", "guaranteed return", "guaranteed profit", "passive income", "crypto",
        "інвестиц", "гарантований прибуток", "криптовалют",
    )

    val job = phrases(
        "praca zdalna", "dodatkowy zarobek", "zarabiaj", "zarób", "polubienia", "polubieni", "płatne zadania",
        "wynagrodzenie dzienne", "rekruter", "rekrutacja online",
        "work from home", "earn money", "daily pay", "paid tasks",
        "робота з дому", "заробіток", "оплачувані завдання",
    )

    val marketplace = phrases(
        "kup teraz", "odbierz pieniądze", "odebrać pieniądze", "odbierz środki", "odebrać środki",
        "przesyłka opłacona", "kupujący zapłacił", "kupujący opłacił", "formularz odbioru", "wypełnij formularz",
        "buyer has paid", "receive payment", "receive funds", "fill in the form",
        "отримати кошти", "покупець оплатив", "заповніть форму",
    )

    val remoteApp = phrases(
        "anydesk", "teamviewer", "quicksupport", "rustdesk", "zainstaluj aplikacj", "pobierz aplikacj",
        "install the app", "download the app", "встановіть додаток", "встановіть застосунок",
    )

    val clickPrompt = phrases(
        "kliknij", "wejdź w link", "wejdź na stronę", "link poniżej", "pod linkiem",
        "click", "tap the link", "follow the link",
        "натисніть", "перейдіть за посиланням",
    )

    val parcel = phrases(
        "paczk", "przesyłk", "kurier", "paczkomat", "dostaw", "adres dostawy",
        "parcel", "package", "delivery", "courier",
        "посилк", "доставк", "кур'єр",
    )

    val utility = phrases(
        "prąd", "prądu", "energi", "gaz", "gazu", "odczyt licznika", "licznik",
        "electricity", "gas bill", "meter reading",
        "світло", "електроенергі", "газ",
    )

    /** Wording typical of genuine banks and services ("never share this code"). */
    val safetyWarning = phrases(
        "nie podawaj nikomu", "nie podawaj go nikomu", "nie przekazuj nikomu", "nie udostępniaj", "nigdy nie prosimy",
        "bank nigdy nie", "never share", "do not share", "don't share", "we will never ask",
        "нікому не повідомляйте", "не повідомляйте нікому",
    )

    /** Domain endings popular with throwaway scam sites. */
    val riskyTlds = setOf(
        "top", "xyz", "click", "online", "site", "shop", "live", "icu", "buzz", "cfd", "sbs", "rest", "store", "club",
        "vip", "win", "bid", "cyou", "monster", "lol", "pw", "tk", "ml", "ga", "cf", "gq", "work", "support", "help",
        "life", "today", "fun", "space", "info",
    )

    /** Endings accepted for links written without "https://" (to avoid matching "godz.21" etc.). */
    val knownTlds = riskyTlds + setOf(
        "com", "net", "org", "pl", "eu", "ua", "uk", "de", "io", "app", "me", "co", "gov", "edu", "ru", "by", "cz",
        "sk", "lt", "lv", "ee", "us", "fr", "it", "es", "nl", "tv", "ai", "dev", "page", "ly", "gl", "to", "gg", "cc",
        "ws", "su", "biz", "link",
    )

    /** Second-level endings where the registrable domain has three parts (bank.com.pl). */
    val multiPartSuffixes = setOf(
        "com.pl", "net.pl", "org.pl", "gov.pl", "edu.pl", "info.pl", "biz.pl", "waw.pl",
        "co.uk", "org.uk", "gov.uk", "com.ua", "gov.ua", "org.ua",
    )

    val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "cutt.ly", "rb.gy", "t.ly", "ow.ly", "shorturl.at",
        "tiny.pl", "s.id", "rebrand.ly", "bl.ink", "short.io", "v.gd", "qr.ae", "tiny.cc",
    )

    val brands: List<Brand> = listOf(
        Brand("InPost", BrandCategory.Parcel, listOf("inpost", "paczkomat"), listOf("inpost.pl", "inpost.eu", "inpost.co.uk")),
        Brand("Poczta Polska", BrandCategory.Parcel, listOf("poczta polska", "pocztapolska", "pocztex", "envelo"), listOf("poczta-polska.pl", "pocztapolska.pl", "envelo.pl", "pocztex.pl")),
        Brand("DPD", BrandCategory.Parcel, listOf("dpd"), listOf("dpd.com", "dpd.pl")),
        Brand("DHL", BrandCategory.Parcel, listOf("dhl"), listOf("dhl.com", "dhl.pl", "dhlparcel.pl")),
        Brand("GLS", BrandCategory.Parcel, listOf("gls"), listOf("gls-group.eu", "gls-group.com", "gls-poland.com")),
        Brand("FedEx", BrandCategory.Parcel, listOf("fedex"), listOf("fedex.com")),
        Brand("Allegro", BrandCategory.Marketplace, listOf("allegro"), listOf("allegro.pl", "allegrolokalnie.pl")),
        Brand("OLX", BrandCategory.Marketplace, listOf("olx"), listOf("olx.pl", "olx.ua")),
        Brand("Vinted", BrandCategory.Marketplace, listOf("vinted"), listOf("vinted.pl", "vinted.com")),
        Brand("PKO BP", BrandCategory.Bank, listOf("pko", "ipko", "pkobp"), listOf("pkobp.pl", "ipko.pl", "pko.pl")),
        Brand("Pekao", BrandCategory.Bank, listOf("pekao"), listOf("pekao.com.pl")),
        Brand("mBank", BrandCategory.Bank, listOf("mbank"), listOf("mbank.pl")),
        Brand("ING", BrandCategory.Bank, listOf("ing"), listOf("ing.pl")),
        Brand("Santander", BrandCategory.Bank, listOf("santander"), listOf("santander.pl")),
        Brand("Millennium", BrandCategory.Bank, listOf("millennium"), listOf("bankmillennium.pl")),
        Brand("Alior", BrandCategory.Bank, listOf("alior"), listOf("aliorbank.pl")),
        Brand("BNP Paribas", BrandCategory.Bank, listOf("bnp", "bnp paribas"), listOf("bnpparibas.pl")),
        Brand("Credit Agricole", BrandCategory.Bank, listOf("credit agricole", "credit-agricole"), listOf("credit-agricole.pl")),
        Brand("Revolut", BrandCategory.Bank, listOf("revolut"), listOf("revolut.com")),
        Brand("ZUS", BrandCategory.Government, listOf("zus"), listOf("zus.pl")),
        Brand("mObywatel", BrandCategory.Government, listOf("mobywatel"), listOf("gov.pl")),
        Brand("Urząd Skarbowy", BrandCategory.Government, listOf("urząd skarbowy", "e-urząd", "podatki"), listOf("podatki.gov.pl", "gov.pl")),
        Brand("Policja", BrandCategory.Government, listOf("policja", "policji"), listOf("policja.pl", "gov.pl")),
        Brand("PGE", BrandCategory.Utility, listOf("pge"), listOf("pge.pl", "gkpge.pl", "pge-obrot.pl")),
        Brand("Tauron", BrandCategory.Utility, listOf("tauron"), listOf("tauron.pl")),
        Brand("Enea", BrandCategory.Utility, listOf("enea"), listOf("enea.pl")),
        Brand("Energa", BrandCategory.Utility, listOf("energa"), listOf("energa.pl")),
        Brand("PGNiG", BrandCategory.Utility, listOf("pgnig"), listOf("pgnig.pl")),
        Brand("Orlen", BrandCategory.Other, listOf("orlen"), listOf("orlen.pl")),
        Brand("Netflix", BrandCategory.Other, listOf("netflix"), listOf("netflix.com")),
        Brand("Apple", BrandCategory.Other, listOf("apple", "icloud"), listOf("apple.com", "icloud.com")),
        Brand("Microsoft", BrandCategory.Other, listOf("microsoft"), listOf("microsoft.com")),
        Brand("Facebook", BrandCategory.Other, listOf("facebook"), listOf("facebook.com", "fb.com")),
    )

    /**
     * Precompiled "any of these phrases" matcher. Phrases start at a word boundary; short ones
     * (3 letters or fewer, like "pin" or "fee") must also end at one, so "pinezka" doesn't match.
     */
    class PhraseSet(phrases: List<String>) {
        private val regex = Regex(
            "(?<![\\p{L}\\p{N}])(?:" + phrases.joinToString("|") { phrase ->
                val folded = Regex.escape(TextNormalizer.fold(phrase))
                if (phrase.length <= 3) "$folded(?![\\p{L}\\p{N}])" else folded
            } + ")",
        )

        fun matches(folded: String): Boolean = regex.containsMatchIn(folded)
    }

    private fun phrases(vararg values: String) = PhraseSet(values.toList())
}

enum class BrandCategory { Parcel, Marketplace, Bank, Government, Utility, Other }

/** A brand scammers impersonate, the words that name it, and the domains that really are it. */
data class Brand(
    val name: String,
    val category: BrandCategory,
    val keywords: List<String>,
    val officialDomains: List<String>,
)
