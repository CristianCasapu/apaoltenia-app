package ro.apaoltenia.client

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Verifica in fundal daca in portal a aparut o factura noua.
 *
 * IMPORTANT — de ce e "best-effort":
 * Portalul ApaOltenia protejeaza autentificarea cu Cloudflare Turnstile
 * (anti-bot), deci NU putem re-loga automat in fundal fara interventia
 * utilizatorului. In schimb, reutilizam sesiunea (cookie-urile) pastrata de
 * WebView dupa ce te-ai autentificat in aplicatie. Cat timp sesiunea e activa,
 * incarcam pagina de facturi intr-un WebView invizibil, extragem o "amprenta"
 * (hash al sumelor/facturilor vizibile) si o comparam cu ultima observata.
 * Daca difera -> notificam. Daca sesiunea a expirat (suntem redirectionati la
 * login) -> renuntam silentios pana la urmatoarea logare manuala.
 */
class InvoiceCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        if (!prefs.invoiceNotificationsEnabled) return Result.success()

        val pageText = withContext(Dispatchers.Main) {
            withTimeoutOrNull(PAGE_TIMEOUT_MS) { loadPortalText() }
        } ?: return Result.retry()

        // Sesiune expirata: pagina ne-a trimis la login. Nu putem trece de
        // Turnstile in fundal, deci asteptam urmatoarea logare manuala.
        if (pageText.contains("login.jsp", ignoreCase = true) ||
            pageText.contains("Turnstile", ignoreCase = true)
        ) {
            return Result.success()
        }

        val signature = signatureOf(pageText)
        val previous = prefs.lastInvoiceSignature
        prefs.lastInvoiceSignature = signature

        // La prima rulare doar memoram amprenta, fara sa alarmam.
        if (previous != null && previous != signature) {
            NotificationHelper.showNewInvoice(applicationContext)
        }
        return Result.success()
    }

    /** Incarca pagina de facturi intr-un WebView invizibil si returneaza textul. */
    private suspend fun loadPortalText(): String? = suspendCoroutine { cont ->
        val web = WebView(applicationContext)
        var resumed = false
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean = false

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(EXTRACT_JS) { raw ->
                    if (resumed) return@evaluateJavascript
                    resumed = true
                    val text = decodeJsString(raw)
                    // Serverul poate reimprospata cookie-ul de sesiune la fiecare
                    // cerere; il scriem pe disc ca verificarea din fundal sa
                    // prelungeasca sesiunea, nu doar sa o citeasca.
                    CookieManager.getInstance().flush()
                    view.destroy()
                    cont.resume(text)
                }
            }
        }
        web.loadUrl(INVOICE_URL)
    }

    private fun signatureOf(text: String): String {
        // Pastram doar semnalele relevante (sume in lei / cuvantul "factura"),
        // ca amprenta sa nu se schimbe de la elemente dinamice fara legatura.
        val relevant = Regex("(?i)(factura|sold|\\d[\\d.,]*\\s*(lei|ron))")
            .findAll(text)
            .joinToString("|") { it.value.trim().lowercase() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(relevant.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return ""
        // evaluateJavascript returneaza un literal JSON ("...."); il curatam simplu.
        return raw.trim('"')
            .replace("\\n", "\n")
            .replace("\\t", " ")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    companion object {
        private const val INVOICE_URL = "https://clienti.apaoltenia.ro/self_utilities/"
        private const val PAGE_TIMEOUT_MS = 20_000L
        private const val EXTRACT_JS = "(function(){return document.body?document.body.innerText:'';})();"
    }
}
