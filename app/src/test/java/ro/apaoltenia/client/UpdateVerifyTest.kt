package ro.apaoltenia.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Teste pentru validarea descarcarii si a sursei asset-ului. */
class UpdateVerifyTest {

    private val sha = "a".repeat(64)

    // ── isDownloadValid ────────────────────────────────────────────────────
    @Test fun copiat_zero_invalid() =
        assertFalse(UpdateManager.isDownloadValid(0, 100, sha, null))

    @Test fun trunchiat_invalid() =
        assertFalse(UpdateManager.isDownloadValid(50, 100, sha, null))

    @Test fun complet_fara_digest_valid() =
        assertTrue(UpdateManager.isDownloadValid(100, 100, sha, null))

    @Test fun lungime_necunoscuta_dar_copiat_valid() =
        assertTrue(UpdateManager.isDownloadValid(100, -1, sha, null))

    @Test fun digest_potrivit_valid() =
        assertTrue(UpdateManager.isDownloadValid(100, 100, sha, sha))

    @Test fun digest_potrivit_case_insensitive() =
        assertTrue(UpdateManager.isDownloadValid(100, 100, sha.uppercase(), sha))

    @Test fun digest_gresit_invalid() =
        assertFalse(UpdateManager.isDownloadValid(100, 100, "b".repeat(64), sha))

    // ── isTrustedAssetUrl ──────────────────────────────────────────────────
    @Test fun github_com_ok() =
        assertTrue(UpdateChecker.isTrustedAssetUrl("https://github.com/x/y/releases/download/v1/a.apk"))

    @Test fun githubusercontent_ok() =
        assertTrue(UpdateChecker.isTrustedAssetUrl("https://objects.githubusercontent.com/z/a.apk"))

    @Test fun http_respins() =
        assertFalse(UpdateChecker.isTrustedAssetUrl("http://github.com/x/a.apk"))

    @Test fun host_strain_respins() =
        assertFalse(UpdateChecker.isTrustedAssetUrl("https://evil.com/a.apk"))

    @Test fun url_invalid_respins() =
        assertFalse(UpdateChecker.isTrustedAssetUrl("nu e url"))

    // ── parseLatest ────────────────────────────────────────────────────────
    @Test fun parse_release_cu_apk() {
        val json = """
            {"tag_name":"v1.7.0","html_url":"https://github.com/x/y/releases/tag/v1.7.0",
             "assets":[{"name":"ApaOltenia-v1.7.0.apk",
             "browser_download_url":"https://github.com/x/y/releases/download/v1.7.0/ApaOltenia-v1.7.0.apk",
             "digest":"sha256:${"a".repeat(64)}"}]}
        """.trimIndent()
        val r = UpdateChecker.parseLatest(json, "1.6.0")
        assertTrue(r is UpdateChecker.CheckResult.Available)
        val u = (r as UpdateChecker.CheckResult.Available).update
        assertEquals("1.7.0", u.version)
        assertEquals("a".repeat(64), u.sha256)
    }

    @Test fun parse_release_fara_apk_da_pagina() {
        val json = """
            {"tag_name":"v1.7.0","html_url":"https://github.com/x/y/releases/tag/v1.7.0","assets":[]}
        """.trimIndent()
        val r = UpdateChecker.parseLatest(json, "1.6.0") as UpdateChecker.CheckResult.Available
        assertEquals("https://github.com/x/y/releases/tag/v1.7.0", r.update.downloadUrl)
    }

    @Test fun parse_asset_pe_host_strain_ignorat() {
        val json = """
            {"tag_name":"v1.7.0","html_url":"https://github.com/x/y/releases/tag/v1.7.0",
             "assets":[{"name":"a.apk","browser_download_url":"https://evil.com/a.apk"}]}
        """.trimIndent()
        val r = UpdateChecker.parseLatest(json, "1.6.0") as UpdateChecker.CheckResult.Available
        // Asset-ul strain e ignorat -> cade pe pagina release-ului.
        assertFalse(r.update.downloadUrl.endsWith(".apk"))
    }

    @Test fun parse_versiune_veche_da_uptodate() {
        val json = """{"tag_name":"v1.5.0","html_url":"x","assets":[]}"""
        assertEquals(UpdateChecker.CheckResult.UpToDate, UpdateChecker.parseLatest(json, "1.6.0"))
    }

    @Test fun parse_html_nejson_arunca() {
        // parseLatest arunca pe non-JSON; check() il prinde si da Failed.
        var threw = false
        try { UpdateChecker.parseLatest("<html>login</html>", "1.6.0") } catch (_: Exception) { threw = true }
        assertTrue(threw)
    }
}
