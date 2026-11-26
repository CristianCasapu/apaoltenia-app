package ro.apaoltenia.client

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client minimal pentru API-ul REST intern al portalului (cel folosit de
 * aplicatia OpenUI5 din client area). NU e un API public sau documentat:
 * l-am dedus din codul JavaScript al portalului, deci poate fi schimbat
 * oricand de operator. De aceea orice raspuns neasteptat intoarce
 * [FetchResult.Unavailable], iar InvoiceCheckWorker revine la metoda veche
 * (pagina randata intr-un WebView invizibil).
 *
 * Autentificarea e sesiunea WebView-ului (cookie-uri), exact ca in browser.
 * Nu trimitem credentiale si nu putem trece de Cloudflare Turnstile.
 */
internal object PortalApi {

    private const val BASE = "https://clienti.apaoltenia.ro/self_utilities"

    /**
     * Lista facturilor pentru TOATE contractele contului (varianta "toate
     * facturile" din portal) — nu cere codClient/nrContract, pe care worker-ul
     * din fundal nu le are.
     */
    const val INVOICES_URL = "$BASE/rest/self/facturi/FacturisAll"

    /** O factura din raspunsul API (doar campurile de care avem nevoie). */
    data class Invoice(
        /** Cheie stabila: idFactura sau, daca lipseste, serie+numar. */
        val key: String,
        val value: Double?,
        val remaining: Double?
    )

    sealed class FetchResult {
        data class Ok(val invoices: List<Invoice>) : FetchResult()
        /** Portalul cere din nou login (redirect catre login / OUI_REQ). */
        data object SessionExpired : FetchResult()
        /** Format/raspuns neasteptat (sau Cloudflare): folosim metoda veche. */
        data object Unavailable : FetchResult()
        /** Fara retea / timeout: WorkManager reincearca mai tarziu. */
        data object NetworkError : FetchResult()
    }

    /**
     * Corpul cererii, identic cu ce trimite modelul EMSModel al portalului la
     * incarcarea listei (`LOAD_RECORDS`). `$qd=true` cere si definitia
     * listei in acelasi raspuns, ca cererea sa nu depinda de o incarcare
     * anterioara a ecranului de facturi in sesiune.
     */
    internal fun requestBody(limit: Int = MAX_RECORDS): String =
        listOf(
            "\$qd" to "true",
            "\$action" to "LOAD_RECORDS",
            "\$locale" to "ro",
            "\$ls" to "false",
            "\$to" to limit.toString()
        ).joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * Facturile neachitate (in sold) din toate contractele. Apel blocant:
     * ruleaza-l pe un thread de fundal.
     *
     * @param cookies header-ul Cookie pentru portal (din CookieManager)
     * @param userAgent UA-ul WebView-ului: cookie-ul Cloudflare e legat de el
     * @param onSetCookie primeste fiecare Set-Cookie din raspuns, ca serverul
     * sa poata reimprospata sesiunea (o salvam inapoi in CookieManager)
     */
    fun fetchUnpaidInvoices(
        cookies: String,
        userAgent: String,
        onSetCookie: (String) -> Unit = {}
    ): FetchResult {
        var conn: HttpURLConnection? = null
        return try {
            val body = requestBody().toByteArray(Charsets.UTF_8)
            conn = (URL(INVOICES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                // Redirect-ul catre login e chiar semnalul de sesiune expirata;
                // nu-l urmam ca sa-l putem recunoaste.
                instanceFollowRedirects = false
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Cookie", cookies)
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                // Marcajul obligatoriu adaugat de portal la fiecare cerere API.
                setRequestProperty("OUI_REQ", "true")
                setRequestProperty("unpaid", "true")
                setFixedLengthStreamingMode(body.size)
            }
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            conn.headerFields
                .filterKeys { it != null && it.equals("Set-Cookie", ignoreCase = true) }
                .values.flatten()
                .forEach(onSetCookie)
            if (code in 300..399) {
                val location = conn.getHeaderField("Location").orEmpty()
                return if (isLoginRedirect(location)) FetchResult.SessionExpired
                else FetchResult.Unavailable
            }
            if (code != 200) return FetchResult.Unavailable
            val type = conn.contentType.orEmpty()
            if (!type.contains("json", ignoreCase = true)) return FetchResult.Unavailable

            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseInvoices(text)?.let { FetchResult.Ok(it) } ?: FetchResult.Unavailable
        } catch (_: java.io.IOException) {
            FetchResult.NetworkError
        } catch (_: Exception) {
            FetchResult.Unavailable
        } finally {
            conn?.disconnect()
        }
    }

    /** Redirect catre pagina de login sau marcajul portalului de sesiune pierduta. */
    internal fun isLoginRedirect(location: String): Boolean =
        location.contains("login", ignoreCase = true) ||
            location.contains("reason=OUI_REQ", ignoreCase = true)

    /**
     * Interpreteaza raspunsul `LOAD_RECORDS`:
     * `{ "records": [ { "row": { "idFactura": 1, "factura": "...",
     * "valoareFactura": 12.3, "restDePlata": 12.3, ... } } ] }`.
     *
     * @return null daca formatul nu e cel asteptat (fara `records`), ca
     * apelantul sa revina la metoda veche in loc sa raporteze "nicio factura".
     */
    internal fun parseInvoices(json: String): List<Invoice>? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val errors = root.optJSONArray("errorMessages")
        if (errors != null && errors.length() > 0) return null
        val records = root.optJSONArray("records") ?: return null

        val result = ArrayList<Invoice>(records.length())
        for (i in 0 until records.length()) {
            val row = records.optJSONObject(i)?.optJSONObject("row") ?: continue
            val id = row.opt("idFactura")?.takeUnless { it == JSONObject.NULL }?.toString()
            val number = row.optString("factura", "").trim()
            val key = when {
                !id.isNullOrBlank() -> "id:$id"
                number.isNotEmpty() -> "nr:$number"
                else -> continue
            }
            result += Invoice(key, row.optNumber("valoareFactura"), row.optNumber("restDePlata"))
        }
        return result
    }

    private fun JSONObject.optNumber(name: String): Double? {
        val v = opt(name)
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.replace(',', '.').toDoubleOrNull()
            else -> null
        }
    }

    /**
     * Facturile care nu existau la verificarea anterioara. La prima rulare
     * ([previousKeys] null) nu raportam nimic — doar memoram starea.
     */
    internal fun newInvoices(
        current: List<Invoice>,
        previousKeys: Set<String>?,
        keyOf: (Invoice) -> String = { it.key }
    ): List<Invoice> =
        if (previousKeys == null) emptyList()
        else current.filter { keyOf(it) !in previousKeys }

    private const val TIMEOUT_MS = 15_000
    private const val MAX_RECORDS = 200
}
