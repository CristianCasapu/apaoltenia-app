package ro.apaoltenia.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Teste pentru ajutoarele de injectare JS (jsString + isPortalUrl). */
class JsUtilsTest {

    private val domain = "apaoltenia.ro"

    // ── jsString ───────────────────────────────────────────────────────────
    @Test fun jsString_pune_ghilimele() =
        assertEquals("\"abc\"", jsString("abc"))

    @Test fun jsString_escapeaza_ghilimeaua() =
        assertEquals("\"a\\\"b\"", jsString("a\"b"))

    @Test fun jsString_escapeaza_backslash() =
        assertEquals("\"a\\\\b\"", jsString("a\\b"))

    /** Apostroful nu e periculos intr-un literal cu ghilimele duble. */
    @Test fun jsString_pastreaza_apostroful() =
        assertEquals("\"a'b\"", jsString("a'b"))

    /** Newline-ul e escapat, NU sters (spre deosebire de vechea jsEscape). */
    @Test fun jsString_escapeaza_newline() =
        assertTrue(jsString("a\nb").contains("\\n"))

    // ── isPortalUrl ────────────────────────────────────────────────────────
    @Test fun portal_host_exact() =
        assertTrue(isPortalUrl("https://apaoltenia.ro/x", domain))

    @Test fun portal_subdomeniu() =
        assertTrue(isPortalUrl("https://clienti.apaoltenia.ro/self_utilities/", domain))

    @Test fun portal_login() =
        assertTrue(isPortalUrl("https://clienti.apaoltenia.ro/self_utilities/login.jsp", domain))

    /** Atacul pe substring: domeniul apare doar in query, nu in host. */
    @Test fun portal_fals_pe_query() =
        assertFalse(isPortalUrl("https://evil.com/?x=apaoltenia.ro", domain))

    @Test fun portal_fals_pe_domeniu_apropiat() =
        assertFalse(isPortalUrl("https://apaoltenia.ro.evil.com/", domain))

    @Test fun portal_null() =
        assertFalse(isPortalUrl(null, domain))

    @Test fun portal_url_invalid() =
        assertFalse(isPortalUrl("nu e url", domain))
}
