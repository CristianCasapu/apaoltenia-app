package ro.apaoltenia.client

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

/** Construieste si afiseaza notificarea de "factura noua". */
object NotificationHelper {

    private const val INVOICE_NOTIFICATION_ID = 1001

    /**
     * @param count cate facturi noi (0 = necunoscut, ex. metoda de rezerva)
     * @param total valoarea lor insumata in lei, daca e cunoscuta
     */
    fun showNewInvoice(context: Context, count: Int = 0, total: Double? = null) {
        if (!hasPermission(context)) return

        val amount = total?.let { String.format(Locale.forLanguageTag("ro-RO"), "%,.2f", it) }
        val body = when {
            count == 1 && amount != null ->
                context.getString(R.string.notif_invoice_body_one, amount)
            count > 1 && amount != null ->
                context.getString(R.string.notif_invoice_body_many, count, amount)
            else -> context.getString(R.string.notif_invoice_body)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, App.INVOICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_invoice_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(INVOICE_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permisiunea a fost revocata intre verificarea de mai sus si
            // trimitere; notificarea se pierde silentios, fara crash.
        }
    }

    fun hasPermission(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
