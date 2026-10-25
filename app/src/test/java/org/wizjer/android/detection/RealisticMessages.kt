package org.wizjer.android.detection

import org.wizjer.android.protection.MessageSource
import org.wizjer.android.protection.MessageSource.Notification
import org.wizjer.android.protection.MessageSource.Sms

/**
 * Everyday messages and scams the way they reach a phone: chat notifications (title = who wrote)
 * and SMS. Written for testing; none are real people's messages.
 */
object RealisticMessages {

    /**
     * What protection should do: no notification at all ([Quiet]), at most a gentle "be careful" note
     * ([Careful]), at least such a note ([Note]: alarming, but the rules can't be sure), or a warning ([Alert]).
     */
    enum class Expect { Quiet, Careful, Note, Alert }

    data class Case(val text: String, val source: MessageSource, val sender: String?, val expect: Expect, val subject: String? = null)

    private fun chat(sender: String, text: String, expect: Expect = Expect.Quiet) = Case(text, Notification, sender, expect)
    private fun sms(sender: String, text: String, expect: Expect = Expect.Quiet) = Case(text, Sms, sender, expect)
    /** A mail app's notification: "subject\npreview" is split as the listener does. */
    private fun email(sender: String, text: String, expect: Expect = Expect.Quiet) =
        Case(text.substringAfter('\n'), MessageSource.Email, sender, expect, subject = text.substringBefore('\n'))

    /** Genuine messages: none of these may raise an alert. */
    val genuine = listOf(
        // Friends sharing links and settling up
        chat("Kuba", "https://paypal.me/kubanowak/25", Expect.Careful),
        chat("Kuba", "oddaj mi za pizze paypal.me/kubanowak/25", Expect.Careful),
        chat("Ola", "Zrzutka na prezent dla Ani: https://paypal.me/olawis", Expect.Careful),
        chat("Jimi", "paypal.me/jimi", Expect.Careful),
        chat("Marek", "https://revolut.me/marek12", Expect.Careful),
        chat("Kuba", "przelej mi 20 zł za bilety jak możesz"),
        chat("Ola", "Zapłaciłam za pizzę, oddasz mi 30 zł blikiem?"),
        chat("Tomek", "zobacz to xd https://youtu.be/dQw4w9WgXcQ"),
        chat("Tomek", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        chat("Ania", "https://www.tiktok.com/@kotek/video/7301234567890"),
        chat("Ania", "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"),
        chat("Marek", "tu jesteśmy https://maps.app.goo.gl/abc123XYZ"),
        chat("Marek", "dołącz na serwer https://discord.gg/abcd1234"),
        chat("Ola", "patrz jaki fajny rower https://www.olx.pl/d/oferta/rower-gorski-CID767-ID12345.html"),
        chat("Ola", "https://allegro.pl/oferta/sluchawki-sony-123456789"),
        chat("Ola", "kupiłam to https://www.vinted.pl/items/123456-kurtka-zimowa"),
        chat("Kuba", "https://x.com/someone/status/1234567890"),
        chat("Kuba", "https://www.instagram.com/p/C1a2b3c4d5/"),
        chat("Tomek", "https://www.reddit.com/r/Polska/comments/abc123/co_sadzicie/"),
        chat("Tomek", "https://github.com/wizjerorg/wizjer-android"),
        chat("Ania", "link do meeta https://meet.google.com/abc-defg-hij"),
        chat("Ania", "https://docs.google.com/document/d/1AbCdEf/edit"),
        chat("Marek", "https://pl.wikipedia.org/wiki/Wis%C5%82a"),
        chat("Kuba", "kup sobie bilety tu https://www.eventim.pl/event/koncert-123"),
        chat("Ola", "https://www.booking.com/hotel/pl/zakopane.html"),
        chat("Ola", "zamówiłam z https://www.zalando.pl, przyjdzie jutro"),
        // Family and plain chat
        chat("Mama", "Kupisz chleb jak będziesz wracać?"),
        chat("Syn", "mamo, będę później, trening się przedłużył"),
        chat("Córka", "tato możesz mnie odebrać o 18?"),
        chat("Syn", "Mamo, przelejesz mi 50 zł na bilet miesięczny?", Expect.Careful),
        chat("Kuba", "ej gramy dziś w cs?"),
        chat("Ola", "pilne!! oddzwoń jak możesz"),
        chat("Ania", "Gratulacje!! Wygrałaś ten konkurs na najlepszy projekt 🎉"),
        chat("Marek", "zablokowali mi konto na insta bo za dużo lajkowałem xd"),
        chat("Kasia", "zaloguj się na librusa i zobacz oceny"),
        chat("Tomek", "masz hasło do wifi?"),
        chat("Babcia", "Zadzwoń do mnie jak znajdziesz chwilę"),
        chat("Ola", "jutro o 9 przyjdzie kurier z paczką, odbierzesz?"),
        chat("Mama", "Привіт, будеш сьогодні ввечері вдома?"),
        chat("Оля", "Скинь 200 грн на картку, поверну завтра", Expect.Careful),
        chat("Сашко", "дивись https://www.youtube.com/watch?v=abc123"),
        chat("Mike", "lol check this out https://www.reddit.com/r/poland/comments/xyz/"),
        chat("Mike", "can you send me the presentation before 5?"),
        // Genuine service SMS and app notifications
        sms("mBank", "mBank: Przelew przychodzący 120,00 PLN od JAN KOWALSKI. Saldo: 1 234,56 PLN"),
        sms("PKO BP", "Twój kod BLIK: 123 456. Nie podawaj go nikomu. PKO BP"),
        sms("mBank", "mBank: kod SMS do autoryzacji operacji: 482913. Nie przekazuj nikomu."),
        sms("InPost", "InPost: Twoja paczka czeka w Paczkomacie WAW123M. Kod odbioru: 123456. Śledź: https://inpost.pl/sledzenie-przesylek"),
        sms("DPD Polska", "DPD: Kurier dostarczy paczkę 12345678901234 dzisiaj w godz. 10-13. Szczegóły: https://www.dpd.com.pl"),
        sms("Allegro", "Allegro: Twoje zamówienie zostało wysłane. Szczegóły w aplikacji Allegro."),
        sms("Orange", "Orange: faktura na kwotę 59,99 zł jest dostępna w Mój Orange. Termin płatności: 15.11."),
        sms("PGE", "PGE: przypominamy o terminie płatności faktury 123,45 zł do 30.10. Zapłać w eBOK: https://ebok.gkpge.pl"),
        sms("ZUS", "ZUS: na Twoim koncie PUE ZUS jest nowa wiadomość. Zaloguj się na https://www.zus.pl"),
        sms("ALERT RCB", "ALERT RCB: Uwaga! Dziś silne burze z gradem. Nie wychodź z domu bez potrzeby. Zabezpiecz rzeczy na balkonie."),
        sms("Orlen Pay", "Dziękujemy za tankowanie! Twoje punkty VITAY: 1250. Sprawdź nagrody w aplikacji ORLEN VITAY."),
        sms("Biedronka", "Biedronka: -40% na kawę tylko dziś! Szczegóły w aplikacji Moja Biedronka."),
        sms("Rossmann", "Rossmann: Twój kupon -49% czeka w aplikacji do 31.10."),
        sms("+48 600 100 200", "Dzień dobry, przypominam o wizycie w gabinecie stomatologicznym jutro o 10:30. Prosimy o potwierdzenie SMS TAK."),
        sms("Revolut", "Revolut: your verification code is 482913. Never share it with anyone."),
        sms("Google", "G-123456 is your Google verification code."),
        sms("Nova Poshta", "Нова пошта: посилка 20450012345678 прибула у відділення №12. Отримайте до 25.10."),
        sms("mBank", "mBank: dodano nowe urządzenie do aplikacji mobilnej. Jeśli to nie Ty, zadzwoń na 801 300 800."),
        // More chat and app notifications that only look alarming
        chat("Ola", "Wysłałam Ci przelew na 150 zł za bilety, sprawdź czy doszło"),
        chat("Tomek", "kod do domofonu to 1234, wejdź i dzwoń pod 12"),
        chat("Ania", "pilnie zadzwoń do babci, nie odbiera ode mnie"),
        chat("Kuba", "wygrałem w lotto 20 zł xD"),
        chat("Marek", "zaloguj się na teamsa o 10, link jest w mailu"),
        chat("Ola", "dostałam mandat za parkowanie, 100 zł..."),
        chat("Kuba", "@everyone stream o 20:00 https://www.twitch.tv/kanal"),
        chat("Allegro", "Kupujący zapłacił za Twój przedmiot. Wyślij go do 25.10."),
        chat("OLX", "Masz nową wiadomość od kupującego: Czy rower jest jeszcze dostępny?"),
        chat("Vinted", "Ktoś polubił Twój przedmiot: Kurtka zimowa"),
        chat("InPost", "Paczka w drodze! Przesyłka 680012345678901234567 została nadana."),
        chat("mBank", "Potwierdź w aplikacji płatność kartą 49,99 PLN w ALLEGRO.PL"),
        chat("Monobank", "Поповнення картки на 500 грн"),
        chat("Оля", "Дякую за переказ! Все дійшло"),
        chat("Uber", "Your driver is arriving now. Look for a grey Toyota Corolla."),
        chat("Amazon", "Your order has shipped. Track it at https://www.amazon.pl/gp/your-orders"),
        // Genuine marketing: coupons, deadlines and "gratulacje" are everyday shopping, at most "be careful"
        sms("Allegro", "Allegro: Masz kupon -15 zł na zakupy w kategorii Supermarket. Aktywuj go w aplikacji do 25.10."),
        sms("Zabka", "Żabka: Twój kupon na darmową kawę czeka w aplikacji Żappka. Ważny do niedzieli."),
        sms("Orange", "Orange: Odbierz 20 GB internetu na weekend w aplikacji Mój Orange. Oferta ważna tylko dziś!"),
        sms("PKO BP", "PKO BP: Załóż konto i odbierz do 300 zł premii. Szczegóły na pkobp.pl/promocje"),
        chat("Allegro", "Masz kupon -15 zł 🎁 Odbierz go w aplikacji, ważny tylko do niedzieli", Expect.Careful),
        chat("Temu", "🎉 Gratulacje! Twój pakiet kuponów o wartości 300 zł czeka. Odbierz przed północą!", Expect.Careful),
        chat("Żappka", "Zgarnij darmowe hot-dogi tylko dziś! 🌭"),
        chat("Revolut", "Zaproś znajomych i odbierz 200 zł za każdą osobę 💸", Expect.Careful),
        email("Allegro", "Twój jesienny kupon na zakupy wciąż czeka na odbiór\nOdbierz swój kupon już teraz » \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C \u200C", Expect.Careful),
        email("Allegro", "Twój kupon rabatowy czeka! Odbierz 15 zł na zakupy\nZaloguj się na Allegro i aktywuj kupon w zakładce Moje Allegro.", Expect.Careful),
        email("Allegro", "Kupon -20 zł z Allegro Pay\nZapłać z Allegro Pay za zakupy powyżej 100 zł, a kupon trafi na Twoje konto w ciągu 24 h.", Expect.Careful),
        email("Allegro", "Gratulacje! Masz kupon na 30 zł 🎉\nWykorzystaj go do końca dnia na zakupy z kategorii Elektronika. Kliknij, aby odebrać.", Expect.Careful),
        email("Allegro", "Prezent dla Ciebie 🎁 Darmowa dostawa z Allegro Smart!\nAktywuj Smart! na 30 dni za darmo i odbieraj paczki bez kosztów dostawy.", Expect.Careful),
        email("Allegro", "Allegro Days: rabaty do -50% tylko dziś!\nNie przegap okazji, to ostatnia szansa na najniższe ceny w tym roku.", Expect.Careful),
        email("Rossmann", "Twój kupon -49% czeka w aplikacji\nOdbierz go do 31.10, tylko dla członków Rossmann Club."),
        email("Zalando", "-20% na nową kolekcję tylko dziś\nUżyj kodu JESIEN20 przy zamówieniu. Darmowa dostawa i zwrot.", Expect.Careful),
        email("Netflix", "Nowości w tym tygodniu\nZobacz, co dodaliśmy do Netflixa: nowe seriale i filmy czekają na Ciebie."),
        email("LinkedIn", "Masz 3 nowe zaproszenia do sieci kontaktów\nZobacz, kto chce się z Tobą połączyć."),
        email("mBank", "Wyciąg z rachunku za wrzesień\nTwój wyciąg jest dostępny w serwisie transakcyjnym mBanku."),
        email("Allegro", "Twoja przesyłka jest w drodze\nŚledź paczkę w aplikacji Allegro. Przewidywana dostawa: jutro."),
    )

    /** Scams: every one should raise an alert. */
    val scams = listOf(
        sms("InPost", "InPost: Twoja przesyłka została wstrzymana z powodu niedopłaty 1,99 zł. Dopłać w ciągu 24h: https://inpost-dostawa.top/pl", Expect.Alert),
        sms("+48 512 345 678", "Twoja paczka nie mogła zostać doręczona. Zaktualizuj adres i opłać 2,49 zł: https://poczta-polska.dostawa-pl.xyz", Expect.Alert),
        sms("+48 512 345 678", "PKO BP: Wykryliśmy podejrzane logowanie. Twoje konto zostanie zablokowane. Zweryfikuj dane: http://pko-weryfikacja.xyz", Expect.Alert),
        sms("+48 512 345 678", "Wykryto logowanie z Wietnamu do Twojego konta. Jesli to nie Ty, zadzwon natychmiast pod +48 22 410 18 52 REF: VN7701", Expect.Alert),
        sms("+48 512 345 678", "Cześć, to ja, zgubiłam telefon i piszę z nowego numeru. Możesz mi pilnie przelać 800 zł? Oddam jutro", Expect.Alert),
        sms("+48 512 345 678", "Mamo to mój nowy numer, stary się zepsuł. Napisz do mnie na WhatsApp, potrzebuję pilnie pomocy", Expect.Alert),
        sms("+48 512 345 678", "PGE: Z powodu braku płatności dziś o 21:00 nastąpi odłączenie prądu. Ureguluj należność: https://pge-oplata.xyz", Expect.Alert),
        sms("+48 512 345 678", "Masz nieopłacony mandat. Sprawdź szczegóły i zapłać, aby uniknąć egzekucji: https://mandat-gov.top", Expect.Alert),
        sms("+48 512 345 678", "Zwrot podatku jest dostępny: tiny.pl/abc12", Expect.Alert),
        sms("+48 512 345 678", "Zarabiaj 5000 zł tygodniowo inwestując w kryptowaluty z ORLEN! Gwarantowany zysk: https://orlen-invest.site", Expect.Alert),
        sms("+48 512 345 678", "Dzień dobry, dział bezpieczeństwa banku. Proszę zainstalować aplikację AnyDesk, aby zabezpieczyć środki.", Expect.Alert),
        sms("+48 512 345 678", "Twoje środki są zagrożone. Przelej je natychmiast na bezpieczne konto: 12 1020 1026 0000 0402 0000 1234", Expect.Alert),
        sms("+48 512 345 678", "Twoje konto Allegro zostanie zawieszone. Zweryfikuj dane w ciągu 24h: https://allegro-weryfikacja.online", Expect.Alert),
        chat("Kupujący", "OLX: Kupujący opłacił Twój przedmiot. Odbierz środki: https://olx-wyplata.top/123", Expect.Alert),
        chat("Anna", "Hej, kupiłam Twój produkt, aby otrzymać pieniądze wypełnij formularz: https://vinted-pl.shop/odbior", Expect.Alert),
        chat("Marek", "Hej, dostałem darmowe nitro, odbierz tu zanim wygaśnie: https://discord-nitro.gift-free.ru/claim", Expect.Alert),
        chat("Tomek", "Siema, zagłosujesz na mnie w konkursie? https://steamcommunlty.com/vote/123", Expect.Alert),
        chat("Kasia", "Hej, możesz mi przesłać kod, który przyszedł ci SMS-em? Pomyłkowo podałam twój numer", Expect.Alert),
        // A friend's hacked account asking for a BLIK code: the most common chat scam in Poland.
        chat("Kasia", "wyślij mi blika na 50 zł, oddam jutro", Expect.Alert),
        chat("Ola", "Praca zdalna! Płatne zadania, polubienia na YouTube, 300 zł dziennie. Napisz na WhatsApp: wa.me/48123456789", Expect.Alert),
        sms("PayPal", "PayPal: Your account has been limited. Verify your identity within 24 hours: http://paypal-verify-account.xyz/login", Expect.Alert),
        sms("Netflix", "Your Netflix subscription has been suspended. Update your payment details: https://netflix-billing.click", Expect.Alert),
        sms("+380 50 123 4567", "Нова пошта: ваша посилка затримана через несплату 15 грн. Оплатіть протягом 24 годин: https://novaposhta-oplata.top/ua", Expect.Alert),
        sms("+380 50 123 4567", "ПриватБанк: ваша картка заблокована. Підтвердіть дані картки: http://privatbank-ua.top", Expect.Alert),
        chat("Мама", "Привіт, це мій новий номер. Терміново скинь 3000 грн на цю картку 4149 6090 1234 5678", Expect.Alert),
        chat("Kasia", "Hej, sorry że tak nagle, możesz mi pilnie pożyczyć 300 zł? Wyślij kod BLIK, oddam wieczorem", Expect.Alert),
        chat("Jan Kowalski", "Dzień dobry, kupiłem Pana przedmiot przez OLX dostawa. Proszę potwierdzić odbiór środków: https://olx.pl-dostawa.com/odbior", Expect.Alert),
        chat("Instagram Support", "Twoje konto narusza prawa autorskie i zostanie usunięte w ciągu 24h. Odwołaj się: https://instagram-copyright-help.com", Expect.Alert),
        chat("Anna HR", "Dzień dobry, jestem rekruterem. Oferujemy pracę zdalną, płatne zadania 200-800 zł dziennie. Napisz na Telegram: t.me/praca_hr", Expect.Alert),
        chat("Marek", "Zainwestowałem 1000 zł i po tygodniu mam 8000! Platforma inwestycyjna tutaj: https://bit.ly/zysk-teraz", Expect.Alert),
        chat("Promo", "Вітаємо! Ви виграли iPhone 15. Отримайте приз: https://bit.ly/prize-ua", Expect.Alert),
        sms("+44 7700 900123", "Hi mum, this is my new number, my phone broke. Can you send me £200, it's urgent", Expect.Alert),
        sms("+48 512 345 678", "Twoja przesyłka DHL czeka na potwierdzenie adresu: https://dhl-pl.delivery-check.top", Expect.Alert),
        sms("+48 512 345 678", "Orange: faktura nie została opłacona, usługa zostanie zawieszona. Zapłać: https://orange-platnosci.site", Expect.Alert),
        sms("+48 512 345 678", "Niedopłata za energię 4,99 zł. Opłać, aby uniknąć odłączenia: https://e-ebok.pl-energia.xyz", Expect.Alert),
        // Scams dressed as coupons, vouchers and lotteries
        sms("+48 512 345 678", "Allegro: Twój kupon 500 zł wygasa dziś! Odbierz go: https://allegro-kupony.shop/odbierz", Expect.Alert),
        sms("+48 512 345 678", "Gratulacje! Zostałeś wybrany do odbioru nagrody od Biedronki. Odbierz voucher 1000 zł: https://bit.ly/biedronka-voucher", Expect.Alert),
        chat("Lidl Polska", "🎁 Lidl rozdaje bony po 500 zł z okazji rocznicy! Wypełnij ankietę: https://lidl-bon.top/ankieta", Expect.Alert),
        chat("Temu Nagrody", "Gratulacje! Wygrałeś iPhone 15 w loterii Temu 🎉 Odbierz nagrodę w ciągu 24h: https://temu-nagrody.top", Expect.Alert),
        // E-mail previews hide the link and the real sender: a warning needs a red flag the rules can
        // name (the tiny parcel fee); an alarming preview without one still gets a "be careful" note.
        email("Allegro", "Twoje konto zostało zawieszone\nZ powodu podejrzanej aktywności Twoje konto zostanie usunięte w ciągu 24h. Zaloguj się, aby potwierdzić dane.", Expect.Note),
        email("InPost", "Twoja paczka czeka na dopłatę 1,99 zł\nPrzesyłka zostanie zwrócona do nadawcy, jeśli nie opłacisz jej w ciągu 48h.", Expect.Alert),
        email("PKO BP", "Pilne: blokada konta\nWykryliśmy nieautoryzowane logowanie. Twoje konto zostanie zablokowane, jeśli nie potwierdzisz danych w ciągu 24 godzin.", Expect.Note),
    )
}
