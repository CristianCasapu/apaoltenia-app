package ro.apaoltenia.client

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

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

    /** URL-ul final si textul vizibil al paginii incarcate in fundal. */
    private data class PageResult(val url: String, val text: String)

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        if (!prefs.invoiceNotificationsEnabled) return Result.success()

        val page = withContext(Dispatchers.Main) {
            withTimeoutOrNull(PAGE_TIMEOUT_MS) { loadPortalPage() }
        } ?: return Result.retry()

        // Sesiune expirata: portalul ne-a redirectionat la login. Nu putem
        // trece de Turnstile in fundal, deci asteptam urmatoarea logare
        // manuala. Verificam URL-ul (semnalul sigur), plus textul ca plasa
        // de siguranta.
        if (page.url.contains("login.jsp", ignoreCase = true) ||
            page.text.contains("Turnstile", ignoreCase = true)
        ) {
            return Result.success()
        }

        // Fara semnale de factura in pagina (aplicatia OpenUI5 nu a apucat sa
        // se randeze, ecran gol etc.): nu suprascriem amprenta buna anterioara
        // si nu notificam pe baza unei pagini goale.
        val relevant = InvoiceSignature.relevantOf(page.text)
        if (relevant.isEmpty()) return Result.success()

        val signature = InvoiceSignature.sha256(relevant)
        val previous = prefs.lastInvoiceSignature
        prefs.lastInvoiceSignature = signature

        // La prima rulare doar memoram amprenta, fara sa alarmam.
        if (previous != null && previous != signature) {
            NotificationHelper.showNewInvoice(applicationContext)
        }
        return Result.success()
    }

    /**
     * Incarca pagina de facturi intr-un WebView invizibil si returneaza
     * URL-ul final + textul vizibil. Portalul e o aplicatie OpenUI5 randata pe
     * client, asa ca dupa onPageFinished mai asteptam (polling) pana apar
     * semnalele de factura sau expira incercarile.
     *
     * Continuarea e anulabila: la timeout, invokeOnCancellation distruge
     * WebView-ul, altfel ar ramane in memorie pana la finalul procesului.
     */
    private suspend fun loadPortalPage(): PageResult? =
        suspendCancellableCoroutine { cont ->
            val web = WebView(applicationContext)
            var resumed = false

            fun finish(result: PageResult?) {
                if (resumed) return
                resumed = true
                // Serverul poate reimprospata cookie-ul de sesiune la fiecare
                // cerere; il scriem pe disc ca verificarea din fundal sa
                // prelungeasca sesiunea, nu doar sa o citeasca.
                CookieManager.getInstance().flush()
                web.stopLoading()
                // destroy() amanat: nu e sigur sa distrugi WebView-ul chiar
                // din callback-ul propriului evaluateJavascript.
                web.post { web.destroy() }
                cont.resume(result)
            }

            cont.invokeOnCancellation {
                web.post {
                    if (!resumed) {
                        resumed = true
                        web.stopLoading()
                        web.destroy()
                    }
                }
            }

            with(web.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
            }

            web.webViewClient = object : WebViewClient() {
                private var polling = false

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean = false

                override fun onPageFinished(view: WebView, url: String) {
                    if (resumed) return
                    if (url.contains("login.jsp", ignoreCase = true)) {
                        finish(PageResult(url, ""))
                        return
                    }
                    if (polling) return
                    polling = true

                    var attempts = 0
                    lateinit var poll: Runnable
                    poll = Runnable {
                        if (resumed) return@Runnable
                        attempts++
                        view.evaluateJavascript(EXTRACT_JS) { raw ->
                            if (resumed) return@evaluateJavascript
                            val text = InvoiceSignature.decodeJsString(raw)
                            if (InvoiceSignature.relevantOf(text).isNotEmpty() ||
                                attempts >= MAX_RENDER_POLLS
                            ) {
                                finish(PageResult(view.url ?: url, text))
                            } else {
                                view.postDelayed(poll, RENDER_POLL_MS)
                            }
                        }
                    }
                    poll.run()
                }

                override fun onReceivedError(
                    view: WebView, request: WebResourceRequest, error: WebResourceError
                ) {
                    // Offline / DNS / TLS pe documentul principal: renuntam si
                    // lasam WorkManager sa reincerce mai tarziu.
                    if (request.isForMainFrame) finish(null)
                }
            }
            web.loadUrl(MainActivity.APP_URL)
        }

    companion object {
        private const val PAGE_TIMEOUT_MS = 35_000L
        private const val RENDER_POLL_MS = 1_500L
        private const val MAX_RENDER_POLLS = 10
        private const val EXTRACT_JS =
            "(function(){return document.body?document.body.innerText:'';})();"
    }
}
