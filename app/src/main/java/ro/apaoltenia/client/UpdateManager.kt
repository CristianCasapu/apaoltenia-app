package ro.apaoltenia.client

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * Descarca APK-ul unei versiuni noi si porneste instalarea, totul din
 * aplicatie — fara redirectionare catre GitHub in browser. Fisierul este
 * descarcat in cache-ul aplicatiei si servit instalatorului de pachete
 * prin FileProvider.
 */
object UpdateManager {

    /**
     * Dialogul standard "Actualizare disponibila": la confirmare descarca
     * APK-ul cu bara de progres si porneste instalatorul Android.
     * [onLater] e apelat cand utilizatorul alege "Mai tarziu" — pornirea
     * automata il foloseste ca sa nu mai repete dialogul pentru aceeasi
     * versiune la fiecare deschidere.
     */
    fun promptAndInstall(
        activity: ComponentActivity,
        update: UpdateChecker.Update,
        onLater: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(activity.getString(R.string.update_available_message, update.version))
            .setPositiveButton(R.string.update_install) { _, _ ->
                downloadAndInstall(activity, update)
            }
            .setNegativeButton(R.string.update_later) { _, _ -> onLater?.invoke() }
            .show()
    }

    private fun downloadAndInstall(activity: ComponentActivity, update: UpdateChecker.Update) {
        // Fara asset APK in release (caz teoretic) — deschidem pagina in browser.
        if (!update.downloadUrl.endsWith(".apk", ignoreCase = true)) {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl)))
            }
            return
        }

        // Fara permisiunea "instaleaza aplicatii necunoscute", descarcarea ar
        // duce la un zid opac al sistemului. Il rugam intai sa o acorde.
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(R.string.update_needs_install_permission)
                .setPositiveButton(R.string.update_open_settings) { _, _ ->
                    runCatching {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${activity.packageName}")
                            )
                        )
                    }
                }
                .setNegativeButton(R.string.save_no, null)
                .show()
            return
        }

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_progress, null)
        val progressBar = view.findViewById<ProgressBar>(R.id.downloadProgress)
        val label = view.findViewById<TextView>(R.id.downloadLabel)
        label.text = activity.getString(R.string.update_downloading)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setView(view)
            .setCancelable(false)
            .show()

        activity.lifecycleScope.launch {
            val apk = download(activity, update) { percent ->
                if (percent < 0) {
                    progressBar.isIndeterminate = true
                } else {
                    progressBar.isIndeterminate = false
                    progressBar.progress = percent
                }
            }
            dialog.dismiss()
            if (apk == null) {
                AlertDialog.Builder(activity)
                    .setMessage(R.string.update_download_failed)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } else {
                install(activity, apk)
            }
        }
    }

    private suspend fun download(
        activity: ComponentActivity,
        update: UpdateChecker.Update,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
            // Curatam APK-urile vechi ca sa nu se adune in cache.
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "ApaOltenia-v${update.version}.apk")

            conn = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            if (conn.responseCode != 200) return@withContext null

            val total = conn.contentLengthLong
            var copied = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(conn.inputStream, digest).use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val percent = if (total > 0) ((copied * 100) / total).toInt() else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            // Fisier trunchiat sau cu alta amprenta decat cea anuntata de GitHub
            // nu ajunge la instalator (ar esua cu "problema la parsarea pachetului"
            // sau ar fi un APK strain). Lenient cand release-ul nu are digest.
            if (!isDownloadValid(copied, total, actualSha, update.sha256)) {
                target.delete()
                return@withContext null
            }
            target
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Pur (testabil): descarcarea e valida daca s-a copiat ceva, lungimea
     * coincide cu cea anuntata (cand e cunoscuta) si — daca release-ul a dat un
     * digest — amprenta SHA-256 se potriveste.
     */
    internal fun isDownloadValid(
        copied: Long, total: Long, actualSha: String?, expectedSha: String?
    ): Boolean {
        if (copied == 0L) return false
        if (total > 0 && copied != total) return false
        if (expectedSha != null && !expectedSha.equals(actualSha, ignoreCase = true)) return false
        return true
    }

    private fun install(activity: ComponentActivity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity, "${BuildConfig.APPLICATION_ID}.fileProvider", apk
        )
        // runCatching: fara instalator disponibil (profil restrictionat etc.)
        // un ActivityNotFoundException ar prabusi aplicatia.
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure {
            AlertDialog.Builder(activity)
                .setMessage(R.string.update_download_failed)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
}
