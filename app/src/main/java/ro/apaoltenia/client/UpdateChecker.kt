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

    private const val API =
        "https://api.github.com/repos/CristianCasapu/apaoltenia-app/releases/latest"

    /** Returneaza un [Update] daca exista o versiune mai noua, altfel null. */
    suspend fun check(currentVersion: String): Update? = withContext(Dispatchers.IO) {
        val json = fetch(API) ?: return@withContext null
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").removePrefix("v")
        if (tag.isBlank() || !isNewer(tag, currentVersion)) return@withContext null

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
        Update(tag, apk, pageUrl)
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
    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").mapNotNull { it.toIntOrNull() }
        val b = current.split(".").mapNotNull { it.toIntOrNull() }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
