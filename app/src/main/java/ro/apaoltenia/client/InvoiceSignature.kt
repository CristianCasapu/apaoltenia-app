package ro.apaoltenia.client

import org.json.JSONTokener
import java.security.MessageDigest

/**
 * Logica pura (fara Android) care transforma textul vizibil al paginii de
 * facturi intr-o "amprenta" stabila. Extrasa din InvoiceCheckWorker ca sa poata
 * fi testata unitar cu JUnit — e codul care decide daca utilizatorul primeste
 * notificari false sau lipsa de notificari.
 */
internal object InvoiceSignature {

    // ă = "a" cu caciula; tinut ca escape ca sursa sa ramana ASCII.
    private val SIGNAL_REGEX =
        Regex("(?i)(factur[aă]|sold|restant|\\d[\\d.,]*\\s*(lei|ron))")

    /**
     * Pastreaza doar semnalele relevante (sume in lei / cuvintele factura,
     * sold, restant — cu sau fara diacritice), ca amprenta sa nu se schimbe
     * de la elemente dinamice fara legatura.
     */
    fun relevantOf(text: String): String =
        SIGNAL_REGEX.findAll(text)
            .joinToString("|") { it.value.trim().lowercase() }

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * evaluateJavascript returneaza un literal JSON ("...cu \n, \" si \uXXXX").
     * Il decodam cu parserul JSON, care trateaza corect toate escape-urile —
     * inclusiv \uXXXX, forma in care sosesc diacriticele romanesti.
     */
    fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return ""
        return runCatching { JSONTokener(raw).nextValue() as? String }
            .getOrNull() ?: ""
    }
}
