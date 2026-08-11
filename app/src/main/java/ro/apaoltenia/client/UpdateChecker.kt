package ro.apaoltenia.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Verifica GitHub Releases pentru o versiune mai noua. Repo-ul este public,
 * deci NU e nevoie de niciun token in aplicatie (spre deosebire de un repo
 * privat, care ar cere un PAT scris in cod — un risc de securitate evitat aici).
 */
object UpdateChecker {

    data class Update(
        val version: String,
        val downloadUrl: String,
        val pageUrl: String,
        /** SHA-256 (hex) al APK-ului, din campul `digest` al asset-ului. Optional. */
        val sha256: String? = null
    )

    /**
     * Rezultatul verificarii: existenta unei versiuni noi, "esti la zi" sau
     * esec de retea. Fara distinctia din urma, o verificare offline ar raporta
     * fals "ai deja cea mai noua versiune".
     */
    sealed class CheckResult {
        data class Available(val update: Update) : CheckResult()
        data object UpToDate : CheckResult()
        data object Failed : CheckResult()
    }

    private const val API =
        "https://api.github.com/repos/CristianCasapu/apaoltenia-app/releases/latest"

    /** Verifica ultimul release publicat si compara cu versiunea curenta. */
    suspend fun check(currentVersion: String): CheckResult = withContext(Dispatchers.IO) {
        val json = fetch(API) ?: return@withContext CheckResult.Failed
        // Parsarea e izolata si prinsa: un raspuns 200 non-JSON (portal captiv,
        // proxy corporate) nu mai propaga o exceptie care ar crapa aplicatia la
        // pornire — exact pe retelele unde e mai probabil sa deschizi aplicatia.
        runCatching { parseLatest(json, currentVersion) }.getOrDefault(CheckResult.Failed)
    }

    /** Pur (fara retea) ca sa fie testabil: interpreteaza raspunsul GitHub. */
    internal fun parseLatest(json: String, currentVersion: String): CheckResult {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").removePrefix("v")
        if (tag.isBlank() || !isNewer(tag, currentVersion)) return CheckResult.UpToDate

        val pageUrl = obj.optString("html_url")
        val assets = obj.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                val url = a.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && isTrustedAssetUrl(url)) {
                    val sha = a.optString("digest")
                        .removePrefix("sha256:").lowercase()
                        .takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
                    return CheckResult.Available(Update(tag, url, pageUrl, sha))
                }
            }
        }
        // Fara asset APK de incredere: deschidem pagina release-ului in browser.
        return CheckResult.Available(Update(tag, pageUrl, pageUrl))
    }

    /** Doar https catre GitHub / storage-ul lui de release assets. */
    internal fun isTrustedAssetUrl(url: String): Boolean {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (!u.scheme.equals("https", ignoreCase = true)) return false
        val host = u.host?.lowercase() ?: return false
        return host == "github.com" ||
            host == "objects.githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }

    private fun fetch(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Compara versiuni de forma "1.2.3". true daca [candidate] > [current]. */
    internal fun isNewer(candidate: String, current: String): Boolean {
        // Pastram doar prefixul numeric al fiecarui segment ("0-beta" -> 0).
        // mapNotNull ar fi ELIMINAT segmentul nenumeric, mutand restul pe
        // pozitii gresite (ex. "1.x.5" ar fi devenit [1, 5] = major.minor).
        fun parse(v: String) = v.split(".")
            .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val a = parse(candidate)
        val b = parse(current)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
