package ro.apaoltenia.client

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
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
 * WebView dupa ce te-ai autentificat in aplicatie.
 *
 * Doua metode, in ordine:
 * 1. API-ul intern al portalului ([PortalApi]): o singura cerere JSON cu
 *    facturile neachitate. Rapid, ieftin la baterie si exact — stim care
 *    factura e noua si valoarea ei.
 * 2. Rezerva, daca API-ul raspunde neasteptat (s-a schimbat, Cloudflare etc.):
 *    pagina portalului intr-un WebView invizibil, din care extragem o
 *    "amprenta" (hash al sumelor/facturilor vizibile).
 * Daca sesiunea a expirat (redirect la login) -> renuntam silentios pana la
 * urmatoarea logare manuala.
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

        when (val api = fetchViaApi()) {
            is PortalApi.FetchResult.Ok -> {
                handleApiInvoices(prefs, api.invoices)
                return Result.success()
            }
            PortalApi.FetchResult.SessionExpired -> {
                recordOutcome(prefs, Outcome.SESSION_EXPIRED)
                return Result.success()
            }
            PortalApi.FetchResult.NetworkError -> {
                recordOutcome(prefs, Outcome.ERROR)
                return Result.retry()
            }
            PortalApi.FetchResult.Unavailable -> Unit // rezerva: WebView, mai jos
        }
        return checkViaWebView(prefs)
    }

    /** Cererea API cu sesiunea WebView-ului; cookie-urile primite inapoi sunt salvate. */
    private suspend fun fetchViaApi(): PortalApi.FetchResult {
        // CookieManager / WebSettings: pe thread-ul principal, ca in restul aplicatiei.
        val (cookies, userAgent) = withContext(Dispatchers.Main) {
            CookieManager.getInstance().getCookie(PortalApi.INVOICES_URL) to
                runCatching { WebSettings.getDefaultUserAgent(applicationContext) }.getOrDefault("")
        }
        // Niciun cookie = nicio sesiune (n-a fost login sau datele au fost sterse).
        if (cookies.isNullOrBlank()) return PortalApi.FetchResult.SessionExpired

        val setCookies = mutableListOf<String>()
        val result = withContext(Dispatchers.IO) {
            PortalApi.fetchUnpaidInvoices(cookies, userAgent) { setCookies += it }
        }
        // Serverul poate reimprospata cookie-urile de sesiune: le salvam pe disc,
        // ca verificarea din fundal sa prelungeasca sesiunea, nu doar sa o citeasca.
        withContext(Dispatchers.Main) {
            val cm = CookieManager.getInstance()
            setCookies.forEach { cm.setCookie(PortalApi.INVOICES_URL, it) }
            cm.flush()
        }
        return result
    }

    /**
     * Compara facturile neachitate cu cele vazute data trecuta (ca hash-uri)
     * si notifica pentru cele noi, cu valoarea lor.
     */
    private fun handleApiInvoices(prefs: AppPreferences, invoices: List<PortalApi.Invoice>) {
        val hashOf = { inv: PortalApi.Invoice -> InvoiceSignature.sha256(inv.key) }
        val previous = prefs.lastInvoiceKeys
        val fresh = PortalApi.newInvoices(invoices, previous, hashOf)
        prefs.lastInvoiceKeys = invoices.map(hashOf).toSet()
        // Amprenta metodei de rezerva ar fi acum veche: o resetam, ca o
        // eventuala revenire la WebView sa porneasca de la zero, fara alarme false.
        prefs.lastInvoiceSignature = null

        if (fresh.isNotEmpty()) {
            val total = fresh.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.sum()
            NotificationHelper.showNewInvoice(applicationContext, fresh.size, total)
        }
        recordOutcome(prefs, if (fresh.isNotEmpty()) Outcome.NEW_INVOICE else Outcome.OK)
    }

    /** Metoda de rezerva: pagina portalului intr-un WebView invizibil. */
    private suspend fun checkViaWebView(prefs: AppPreferences): Result {
        val page = withContext(Dispatchers.Main) {
            withTimeoutOrNull(PAGE_TIMEOUT_MS) { loadPortalPage() }
        } ?: run {
            recordOutcome(prefs, Outcome.ERROR)
            return Result.retry()
        }

        // Sesiune expirata: portalul ne-a redirectionat la login. Nu putem
        // trece de Turnstile in fundal, deci asteptam urmatoarea logare
        // manuala. Verificam URL-ul (semnalul sigur), plus textul ca plasa
        // de siguranta.
        if (page.url.contains("login.jsp", ignoreCase = true) ||
            page.text.contains("Turnstile", ignoreCase = true)
        ) {
            recordOutcome(prefs, Outcome.SESSION_EXPIRED)
            return Result.success()
        }

        // Fara semnale de factura in pagina (aplicatia OpenUI5 nu a apucat sa
        // se randeze, ecran gol etc.): nu suprascriem amprenta buna anterioara
        // si nu notificam pe baza unei pagini goale.
        val relevant = InvoiceSignature.relevantOf(page.text)
        if (relevant.isEmpty()) {
            recordOutcome(prefs, Outcome.NO_DATA)
            return Result.success()
        }

        val signature = InvoiceSignature.sha256(relevant)
        val previous = prefs.lastInvoiceSignature
        prefs.lastInvoiceSignature = signature

        // La prima rulare doar memoram amprenta, fara sa alarmam.
        val changed = previous != null && previous != signature
        if (changed) {
            NotificationHelper.showNewInvoice(applicationContext)
        }
        recordOutcome(prefs, if (changed) Outcome.NEW_INVOICE else Outcome.OK)
        return Result.success()
    }

    /** Rezultatul ultimei verificari, pentru afisare in Setari / verificare manuala. */
    enum class Outcome { OK, NEW_INVOICE, SESSION_EXPIRED, NO_DATA, ERROR }

    private fun recordOutcome(prefs: AppPreferences, outcome: Outcome) {
        prefs.lastInvoiceCheckOutcome = outcome.name
        prefs.lastInvoiceCheckAt = System.currentTimeMillis()
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
