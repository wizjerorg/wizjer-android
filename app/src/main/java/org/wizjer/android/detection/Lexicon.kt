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

    /** Asking for codes, passwords or card data. Paying "blikiem" is everyday; sending someone a BLIK code isn't. */
    val credentials = phrases(
        "kod blik", "kodu blik", "kodem blik", "kod z blik", "wyślij mi blik", "wyślij blik", "wyślesz mi blik",
        "wyślesz blik", "podeślij blik", "podeślesz mi blik", "prześlij blik", "daj blik", "pożycz blik",
        "pożyczysz mi blik", "potrzebuję blik", "podaj kod", "kod sms", "kod weryfikacyjny", "przepisz kod",
        "prześlij kod", "prześlij mi kod", "przesłać kod", "przesłać mi kod", "wyślij kod", "wyślij mi kod",
        "podeślij kod", "podeślij mi kod", "kod, który przyszedł", "kod który przyszedł", "kod co przyszedł",
        "hasło", "pin", "cvv", "dane karty", "numer karty", "dane płatności", "dane do płatności", "dane logowania",
        "dane do logowania",
        "password", "verification code", "card details", "card number", "payment details", "login details",
        "send me the code", "the code you received",
        "пароль", "код з смс", "дані картки", "номер картки", "надішли код", "скинь код", "перешли код",
    )

    /** Demanding a fee; with a tiny amount for a parcel or a bill it's the classic parcel scam. */
    val feeRequest = phrases(
        "dopłat", "dopłać", "niedopłat", "opłać", "zapłać", "uregul", "pay the", "pay a", "доплат", "оплатіть", "сплатіть", "несплат",
    )

    /** Asking to log in or verify: a red flag only next to a link ("zaloguj się na Allegro" alone is everyday). */
    val login = phrases(
        "zaloguj się", "zweryfikuj", "potwierdź dane", "weryfikacja konta", "weryfikacji konta",
        "verify your", "log in to", "підтвердіть дані", "увійдіть",
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

    /** Prizes and money said to be waiting (e.g. a tax refund); "darmowa dostawa" is everyday shopping. */
    val prize = phrases(
        "wygrał", "wygrana", "wygraną", "nagrod", "gratulacje", "voucher", "bon o wartości", "nitro za darmo",
        "losowani", "zwrot podatku", "nadpłat", "przysługuje ci zwrot", "przysługuje zwrot", "otrzymasz zwrot",
        "you won", "winner", "prize", "congratulations", "free gift", "free nitro", "tax refund", "eligible for a refund",
        "виграш", "вітаємо", "приз", "безкоштовн", "повернення податку", "повернення коштів",
    )

    /** The "new number" story. Just writing "mamo" or "tato" is everyday, so it doesn't count. */
    val family = phrases(
        "nowy numer", "nowego numeru", "zgubiłem telefon", "zgubiłam telefon", "zepsuł mi się telefon",
        "stary telefon", "miałem wypadek", "miałam wypadek", "to ja, twój", "to ja, twoja",
        "new number", "lost my phone", "my phone broke",
        "новий номер", "нового номера", "загубив телефон", "загубила телефон",
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

    /** Lures of the fake-buyer scam: a seller never has to "collect" the money. */
    val marketplace = phrases(
        "odbierz pieniądze", "odebrać pieniądze", "odbierz środki", "odebrać środki", "odbiór środków",
        "odbioru środków", "potwierdź odbiór", "potwierdzić odbiór", "formularz odbioru", "wypełnij formularz",
        "receive payment", "receive funds", "fill in the form",
        "отримати кошти", "заповніть форму",
    )

    /** Sale wording that marketplaces send too; it only counts next to a link. */
    val marketplaceSale = phrases(
        "kup teraz", "przesyłka opłacona", "kupujący zapłacił", "kupujący opłacił", "buyer has paid", "покупець оплатив",
    )

    val remoteApp = phrases(
        "anydesk", "teamviewer", "quicksupport", "rustdesk", "zainstaluj aplikacj", "pobierz aplikacj",
        "install the app", "download the app", "встановіть додаток", "встановіть застосунок",
    )

    val clickPrompt = phrases(
        "kliknij", "wejdź w link", "wejdź na stronę", "link poniżej", "pod linkiem", "zagłosuj", "oddaj głos",
        "click", "tap the link", "follow the link", "vote for",
        "натисніть", "перейдіть за посиланням", "проголосуй",
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

    /** Domain endings popular with throwaway scam sites (many on CERT Polska's list). */
    val riskyTlds = setOf(
        "top", "xyz", "click", "online", "site", "shop", "live", "icu", "buzz", "cfd", "sbs", "rest", "store", "club",
        "vip", "win", "bid", "cyou", "monster", "lol", "pw", "tk", "ml", "ga", "cf", "gq", "work", "support", "help",
        "life", "today", "fun", "space", "info", "lat", "bond", "mom", "autos", "qpon", "casa", "pics", "skin", "hair",
        "beauty", "makeup", "homes", "yachts", "boats", "motorcycles", "cam", "quest",
    )

    /**
     * Endings accepted for links written without "https://" (to avoid matching "godz.21" etc.). A bare
     * domain with another ending still counts when the downloaded lists know it.
     */
    val knownTlds = riskyTlds + setOf(
        "com", "net", "org", "pl", "eu", "ua", "uk", "de", "io", "app", "me", "co", "gov", "edu", "ru", "by", "cz",
        "sk", "lt", "lv", "ee", "us", "fr", "it", "es", "nl", "tv", "ai", "dev", "page", "ly", "gl", "to", "gg", "cc",
        "ws", "su", "biz", "link", "pro", "digital", "website", "tech", "cloud", "blog", "wiki", "forum", "world",
        "business", "company", "host", "guru", "ink", "best", "network", "email", "news", "baby", "beer", "surf",
        "luxe", "sale", "br", "ch", "ro", "jp", "se", "dk", "fi", "hu", "pt", "gr", "tr", "cn", "ca", "au", "kz", "md",
        "bg", "hr", "rs", "vn",
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

    /** Hosts under a shortener's domain that aren't shorteners (Google Maps share links). */
    val notShorteners = setOf("maps.app.goo.gl")

    /**
     * Genuine services whose links lead to any person: a payment page (paypal.me/anyone) or a chat
     * (wa.me/number). Not fake, but no proof the message is safe either.
     */
    val personalLinkHosts = setOf("paypal.me", "revolut.me", "wa.me", "t.me")

    val brands: List<Brand> = listOf(
        Brand("InPost", BrandCategory.Parcel, listOf("inpost", "paczkomat"), listOf("inpost.pl", "inpost.eu", "inpost.co.uk")),
        Brand("Poczta Polska", BrandCategory.Parcel, listOf("poczta polska", "pocztapolska", "pocztex", "envelo"), listOf("poczta-polska.pl", "pocztapolska.pl", "envelo.pl", "pocztex.pl")),
        Brand("DPD", BrandCategory.Parcel, listOf("dpd"), listOf("dpd.com", "dpd.pl", "dpd.com.pl")),
        Brand("DHL", BrandCategory.Parcel, listOf("dhl"), listOf("dhl.com", "dhl.pl", "dhl.com.pl", "dhl24.com.pl", "dhlparcel.pl")),
        Brand("GLS", BrandCategory.Parcel, listOf("gls"), listOf("gls-group.eu", "gls-group.com", "gls-poland.com", "gls-pl.com")),
        Brand("FedEx", BrandCategory.Parcel, listOf("fedex"), listOf("fedex.com")),
        Brand("Allegro", BrandCategory.Marketplace, listOf("allegro"), listOf("allegro.pl", "allegrolokalnie.pl")),
        Brand("OLX", BrandCategory.Marketplace, listOf("olx"), listOf("olx.pl", "olx.ua")),
        Brand("Vinted", BrandCategory.Marketplace, listOf("vinted"), listOf("vinted.pl", "vinted.com")),
        Brand("Otomoto", BrandCategory.Marketplace, listOf("otomoto"), listOf("otomoto.pl")),
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
        Brand("gov.pl", BrandCategory.Government, listOf("gov", "mandat", "e-toll", "etoll", "epuap", "profil zaufany"), listOf("gov.pl")),
        Brand("PGE", BrandCategory.Utility, listOf("pge"), listOf("pge.pl", "gkpge.pl", "pge-obrot.pl")),
        Brand("Tauron", BrandCategory.Utility, listOf("tauron"), listOf("tauron.pl")),
        Brand("Enea", BrandCategory.Utility, listOf("enea"), listOf("enea.pl")),
        Brand("Energa", BrandCategory.Utility, listOf("energa"), listOf("energa.pl")),
        Brand("PGNiG", BrandCategory.Utility, listOf("pgnig"), listOf("pgnig.pl")),
        Brand("Orlen", BrandCategory.Other, listOf("orlen"), listOf("orlen.pl")),
        Brand("Netflix", BrandCategory.Other, listOf("netflix"), listOf("netflix.com")),
        Brand("Apple", BrandCategory.Other, listOf("apple", "icloud"), listOf("apple.com", "icloud.com")),
        Brand("Microsoft", BrandCategory.Other, listOf("microsoft"), listOf("microsoft.com")),
        Brand("Facebook", BrandCategory.Other, listOf("facebook", "messenger"), listOf("facebook.com", "fb.com", "messenger.com")),
        Brand("PayPal", BrandCategory.Other, listOf("paypal"), listOf("paypal.com")),
        Brand("Steam", BrandCategory.Other, listOf("steam", "steamcommunity"), listOf("steampowered.com", "steamcommunity.com")),
        Brand(
            "Discord",
            BrandCategory.Other,
            listOf("discord"),
            listOf("discord.com", "discord.gg", "discordapp.com", "discordapp.net", "discord.gift", "discord.media"),
        ),
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
