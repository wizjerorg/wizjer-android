package org.wizjer.android.detection

/**
 * Example messages for the sandbox and the detector tests: typical scams in Poland
 * and genuine messages that must not raise an alarm. All made up; no real data.
 */
object SampleMessages {

    data class Sample(val label: String, val text: String, val expected: RiskLevel, val sender: String? = null)

    val all = listOf(
        Sample(
            "Paczka",
            "InPost: Twoja przesyłka została wstrzymana z powodu niedopłaty 1,99 zł. Dopłać w ciągu 24h: https://inpost-dostawa.top/pl",
            RiskLevel.Dangerous,
        ),
        Sample(
            "Bank",
            "PKO BP: Wykryliśmy podejrzane logowanie. Twoje konto zostanie zablokowane. Zweryfikuj dane: http://pko-weryfikacja.xyz",
            RiskLevel.Dangerous,
        ),
        Sample(
            "Bezpieczne konto",
            "Dzień dobry, tu dział bezpieczeństwa banku. Pana środki są zagrożone, proszę je pilnie przelać na bezpieczne konto 12 1020 1026 0000 0402 0000 1234.",
            RiskLevel.Dangerous,
        ),
        Sample(
            "Wnuczek",
            "Mamo, to mój nowy numer, zgubiłem telefon. Pilnie potrzebuję 2000 zł na rachunek, oddam jutro. Napisz na WhatsApp.",
            RiskLevel.Dangerous,
        ),
        Sample(
            "OLX",
            "Dzień dobry, kupiłem Pana przedmiot na OLX z dostawą. Aby odebrać pieniądze, proszę wypełnić formularz: https://olx-wyplata.online/odbierz",
            RiskLevel.Dangerous,
        ),
        Sample(
            "Prąd",
            "PGE: Brak wpłaty za energię. Dziś o 21:00 nastąpi odłączenie prądu. Ureguluj należność: https://pge-oplata.site",
            RiskLevel.Dangerous,
        ),
        Sample(
            "Zniekształcony",
            "Twoja p a c z k a czeka. D o p ł a ć 2 zł: іnpost-pl[.]top",
            RiskLevel.Dangerous,
        ),
        Sample(
            "UA: картка",
            "Ваш рахунок заблоковано. Терміново підтвердіть дані картки за посиланням: bit.ly/3xYz",
            RiskLevel.Dangerous,
        ),
        Sample(
            "EN: parcel",
            "Your package could not be delivered. Pay the 1.50 EUR redelivery fee today: http://dhl-redelivery.click",
            RiskLevel.Dangerous,
        ),
        Sample(
            "Prawdziwy BLIK",
            "Twój kod BLIK: 482913. Kod jest ważny 2 minuty. Nie podawaj go nikomu.",
            RiskLevel.Safe,
        ),
        Sample(
            "Prawdziwa paczka",
            "InPost: Twoja paczka czeka w Paczkomacie WAW123. Kod odbioru: 123456. Szczegóły: https://inpost.pl/sledzenie",
            RiskLevel.Safe,
        ),
        Sample(
            "Zwykła wiadomość",
            "Cześć! Będę o 18, kupić coś do obiadu?",
            RiskLevel.Safe,
        ),
    )
}
