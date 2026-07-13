package ro.apaoltenia.client

/**
 * Istoricul versiunilor aplicatiei, sursa unica pentru ecranul de changelog.
 * Cea mai recenta versiune este prima in lista.
 */
object Changelog {

    data class Entry(val version: String, val date: String, val changes: List<String>)

    val entries: List<Entry> = listOf(
        Entry(
            "1.5.0", "13.07.2026", listOf(
                "Acces direct in aplicatie cand sesiunea e inca valida (fara login)",
                "Sesiunea de login se pastreaza mult mai mult, chiar dupa inchidere sau in fundal",
                "Verificarea din fundal prelungeste sesiunea, nu doar o citeste"
            )
        ),
        Entry(
            "1.4.0", "13.07.2026", listOf(
                "Navigatie rafinata: fara bordurile groase din meniul lateral",
                "Istoric versiuni (acest ecran) accesibil din Setari",
                "Verificare de actualizare la pornirea aplicatiei"
            )
        ),
        Entry(
            "1.3.0", "13.07.2026", listOf(
                "Meniu lateral restilizat, in tema clara si intunecata",
                "Meniul de accesibilitate mutat in navigatie, dupa \"Stergere cont\"",
                "Stergere cont protejata: cere parola contului si confirmare",
                "Pagina de contact simplificata, cu zona de mesaj curata"
            )
        ),
        Entry(
            "1.2.0", "13.07.2026", listOf(
                "Autentificare automata dupa verificarea de securitate (login dintr-o atingere)",
                "Bara de sus brandata, coerenta cu portalul",
                "Confort tactil imbunatatit pe mobil"
            )
        ),
        Entry(
            "1.1.0", "13.07.2026", listOf(
                "Teme luminoasa/intunecata proprii, cu branding",
                "Actualizare completa in aplicatie: descarcare + instalare, fara redirectionare",
                "Fix: salvarea datelor de conectare si deblocarea biometrica",
                "Grafic de consum lizibil pe mobil"
            )
        ),
        Entry(
            "1.0.2", "12.07.2026", listOf(
                "Stil custom peste portal (culori de brand, carduri rotunjite)",
                "Fix: pull-to-refresh doar cand pagina e derulata sus de tot"
            )
        ),
        Entry(
            "1.0.1", "12.07.2026", listOf(
                "Corectii de build si ajustari minore"
            )
        ),
        Entry(
            "1.0.0", "12.07.2026", listOf(
                "Prima versiune: invelis Android pentru portalul ApaOltenia",
                "Deblocare biometrica si auto-completarea datelor de logare",
                "Notificari pentru facturi noi"
            )
        )
    )
}
