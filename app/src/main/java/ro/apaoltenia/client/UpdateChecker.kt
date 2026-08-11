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

    data class Update(val version: String, val downloadUrl: String, val pageUrl: String)

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
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").removePrefix("v")
        if (tag.isBlank() || !isNewer(tag, currentVersion)) {
            return@withContext CheckResult.UpToDate
        }

        val pageUrl = obj.optString("html_url")
        val assets = obj.optJSONArray("assets")
        var apk = pageUrl
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url"); break
                }
            }
        }
        CheckResult.Available(Update(tag, apk, pageUrl))
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
