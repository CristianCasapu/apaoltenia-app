package ro.apaoltenia.client

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
     */
    fun promptAndInstall(activity: Activity, update: UpdateChecker.Update) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(activity.getString(R.string.update_available_message, update.version))
            .setPositiveButton(R.string.update_install) { _, _ ->
                downloadAndInstall(activity, update)
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun downloadAndInstall(activity: Activity, update: UpdateChecker.Update) {
        // Fara asset APK in release (caz teoretic) — deschidem pagina in browser.
        if (!update.downloadUrl.endsWith(".apk", ignoreCase = true)) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.pageUrl)))
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

        (activity as LifecycleOwner).lifecycleScope.launch {
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
        activity: Activity,
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
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
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
            target
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun install(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity, "${BuildConfig.APPLICATION_ID}.fileProvider", apk
        )
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
