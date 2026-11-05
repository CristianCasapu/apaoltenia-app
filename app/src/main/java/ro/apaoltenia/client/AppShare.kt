package ro.apaoltenia.client

import android.content.Context
import android.content.Intent

/**
 * Trimite linkul catre ultimul release prin orice aplicatie care accepta text
 * (WhatsApp, Messenger, SMS, email...), folosind meniul de share al sistemului.
 * `releases/latest` redirectioneaza mereu la cel mai nou release, deci linkul
 * ramane valabil si dupa ce apar versiuni noi.
 */
object AppShare {

    const val LATEST_RELEASE_URL = "https://github.com/${UpdateChecker.REPO}/releases/latest"

    fun share(ctx: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.share_message, LATEST_RELEASE_URL))
        }
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_chooser_title)))
    }
}
