package ro.apaoltenia.client

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Setari ale aplicatiei (tema, notificari) pastrate local in SharedPreferences.
 * Nu contine date personale — doar preferinte de interfata si comportament.
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    /** Modul de tema ales de utilizator: sistem / luminos / intunecat. */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    /** Notificari la aparitia unei facturi noi in portal. */
    var invoiceNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY, value).apply()

    /**
     * Ultima "amprenta" a facturilor observata in portal (hash), folosita ca
     * sa detectam schimbari fara a stoca continut sensibil. Doar un hash.
     */
    var lastInvoiceSignature: String?
        get() = prefs.getString(KEY_INVOICE_SIG, null)
        set(value) = prefs.edit().putString(KEY_INVOICE_SIG, value).apply()

    companion object {
        private const val PREF_FILE = "apaoltenia_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_NOTIFY = "invoice_notifications"
        private const val KEY_INVOICE_SIG = "invoice_signature"

        /** Aplica modul de tema salvat pe intreaga aplicatie. */
        fun applyTheme(mode: Int) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
