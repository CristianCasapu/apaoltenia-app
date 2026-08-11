package ro.apaoltenia.client

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Punctul de intrare al aplicatiei: aplica tema salvata inainte de a crea
 * vreo activitate si pregateste canalul de notificari.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = AppPreferences(this)
        AppPreferences.applyTheme(prefs.themeMode)
        createNotificationChannel()
        // Re-armam verificarea de facturi daca e activata dar coada WorkManager
        // s-a pierdut (force-stop, stergere date sistem, restaurare pe telefon
        // nou). enqueueUniquePeriodicWork cu UPDATE e idempotent.
        if (prefs.invoiceNotificationsEnabled) InvoiceCheckScheduler.enable(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            INVOICE_CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notif_channel_desc)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val INVOICE_CHANNEL_ID = "facturi_noi"
    }
}
