package ro.apaoltenia.client

import org.json.JSONObject
import java.net.URI

/**
 * Ajutoare pure (usor de testat) pentru injectarea de JavaScript in WebView.
 */

/**
 * Serializeaza o valoare ca literal string JavaScript, cu ghilimele cu tot.
 * Foloseste JSONObject.quote, care escapeaza corect ghilimelele, backslash-ul,
 * newline-urile si separatorii de linie U+2028/U+2029 (pe care vechea
 * implementare ii STERGEA, corupand o parola care i-ar contine).
 *
 * Exemplu: jsString("a'b") -> "\"a'b\"" (gata de interpolat direct in JS).
 */
internal fun jsString(value: String): String = JSONObject.quote(value)

/**
 * Adevarat doar daca URL-ul apartine chiar portalului (host exact
 * `apaoltenia.ro` sau un subdomeniu al lui). Verificarea veche pe
 * `url.contains("apaoltenia.ro")` lasa `https://evil.com/?x=apaoltenia.ro` sa
 * primeasca scripturile injectate — inclusiv cel de captura a credentialelor.
 */
internal fun isPortalUrl(url: String?, domain: String): Boolean {
    // java.net.URI (nu android.net.Uri) ca sa ramana pur JVM si testabil unitar.
    val host = url?.let { runCatching { URI(it).host }.getOrNull() }
        ?: return false
    return host.equals(domain, ignoreCase = true) ||
        host.endsWith(".$domain", ignoreCase = true)
}

/**
 * Deduce un nume de fisier pentru un download. Prefera `filename=` din antetul
 * Content-Disposition; altfel ultimul segment din URL; altfel un nume implicit.
 * Pur, ca sa fie testabil.
 */
internal fun downloadFileName(
    url: String,
    contentDisposition: String?,
    default: String = "factura.pdf"
): String {
    contentDisposition?.let { cd ->
        // filename*=UTF-8''nume.pdf  sau  filename="nume.pdf"
        Regex("""filename\*?=(?:UTF-8'')?"?([^\";]+)"?""", RegexOption.IGNORE_CASE)
            .find(cd)?.groupValues?.get(1)?.trim()
            ?.let { name -> if (name.isNotBlank()) return name.substringAfterLast('/') }
    }
    val fromUrl = runCatching { URI(url).path }.getOrNull()
        ?.substringAfterLast('/')?.substringBefore('?')
    return if (!fromUrl.isNullOrBlank()) fromUrl else default
}
