package ro.apaoltenia.client

import android.content.Context

/**
 * Sterge complet datele locale legate de cont, dintr-un singur loc. Fara
 * aceasta functie, "Sterge datele salvate" din ecranul de deblocare si cel din
 * Setari faceau lucruri diferite (unul lasa notificarile pornite si job-ul din
 * fundal programat), ajungand intr-o stare pe care Setarile o interzic.
 */
internal fun wipeLocalData(context: Context) {
    val prefs = AppPreferences(context)
    CredentialStore(context).clear()
    prefs.invoiceNotificationsEnabled = false
    InvoiceCheckScheduler.disable(context)
    prefs.autoLoginFailures = 0
    prefs.skippedUpdateVersion = null
}
