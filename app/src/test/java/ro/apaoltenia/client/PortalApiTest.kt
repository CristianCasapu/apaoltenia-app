package ro.apaoltenia.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste pentru clientul API al portalului: corpul cererii, interpretarea
 * raspunsului LOAD_RECORDS si detectarea facturilor noi. Datele de test au
 * forma raspunsului real al portalului, cu valori inventate.
 */
class PortalApiTest {

    private fun row(fields: String) = """{"position":0,"row":{$fields},"extraProperties":{}}"""

    private fun response(vararg rows: String) =
        """{"from":0,"to":100,"controlKey":"k","modeRemote":true,"errorMessages":null,
           "records":[${rows.joinToString(",")}]}"""

    // ── requestBody ────────────────────────────────────────────────────────
    @Test fun body_are_actiunea_load_records_codificata() {
        val body = PortalApi.requestBody(50)
        assertTrue(body.contains("%24action=LOAD_RECORDS"))
        assertTrue(body.contains("%24qd=true"))
        assertTrue(body.contains("%24to=50"))
        assertFalse("\$ trebuie codificat", body.contains("\$"))
    }

    // ── parseInvoices ──────────────────────────────────────────────────────
    @Test fun parse_raspuns_real() {
        val json = response(
            row(""""client":"123456","factura":"FAC 00000001","dataEmitere":{},
                "restDePlata":45.5,"valoareFactura":45.5,"idFactura":987,"serieDoc":"FAC""""),
            row(""""factura":"FAC 00000002","restDePlata":10,"valoareFactura":20.25,"idFactura":988""")
        )
        val list = PortalApi.parseInvoices(json)!!
        assertEquals(2, list.size)
        assertEquals("id:987", list[0].key)
        assertEquals(45.5, list[0].value!!, 0.0001)
        assertEquals(20.25, list[1].value!!, 0.0001)
        assertEquals(10.0, list[1].remaining!!, 0.0001)
    }

    @Test fun parse_lista_goala_e_valida() =
        assertEquals(0, PortalApi.parseInvoices(response())!!.size)

    /** Fara idFactura folosim seria+numarul ca cheie. */
    @Test fun parse_fallback_pe_numarul_facturii() {
        val list = PortalApi.parseInvoices(response(row(""""factura":"FAC 7","idFactura":null""")))!!
        assertEquals("nr:FAC 7", list.single().key)
    }

    /** Rand fara niciun identificator: ignorat, nu inventam o cheie. */
    @Test fun parse_rand_fara_identificator_ignorat() =
        assertEquals(0, PortalApi.parseInvoices(response(row(""""valoareFactura":1""")))!!.size)

    @Test fun parse_suma_ca_text_cu_virgula() {
        val inv = PortalApi.parseInvoices(response(row(""""idFactura":1,"valoareFactura":"12,30"""")))!!
        assertEquals(12.30, inv.single().value!!, 0.0001)
    }

    /** Format necunoscut -> null, ca worker-ul sa revina la metoda veche. */
    @Test fun parse_fara_records_da_null() =
        assertNull(PortalApi.parseInvoices("""{"queryDefinition":{}}"""))

    @Test fun parse_html_da_null() =
        assertNull(PortalApi.parseInvoices("<html><body>Just a moment...</body></html>"))

    @Test fun parse_cu_erori_de_la_server_da_null() =
        assertNull(PortalApi.parseInvoices("""{"errorMessages":["x"],"records":[]}"""))

    // ── isLoginRedirect ────────────────────────────────────────────────────
    @Test fun redirect_login() {
        assertTrue(PortalApi.isLoginRedirect("https://clienti.apaoltenia.ro/self_utilities/login"))
        assertTrue(PortalApi.isLoginRedirect("/self_utilities/login.jsp?reason=OUI_REQ"))
        assertFalse(PortalApi.isLoginRedirect("https://clienti.apaoltenia.ro/cdn-cgi/challenge-platform/x"))
        assertFalse(PortalApi.isLoginRedirect(""))
    }

    // ── newInvoices ────────────────────────────────────────────────────────
    private val a = PortalApi.Invoice("id:1", 10.0, 10.0)
    private val b = PortalApi.Invoice("id:2", 20.0, 20.0)

    @Test fun prima_rulare_nu_raporteaza_nimic() =
        assertTrue(PortalApi.newInvoices(listOf(a, b), null).isEmpty())

    @Test fun detecteaza_doar_factura_noua() =
        assertEquals(listOf(b), PortalApi.newInvoices(listOf(a, b), setOf("id:1")))

    @Test fun factura_platita_disparuta_nu_e_noua() =
        assertTrue(PortalApi.newInvoices(listOf(a), setOf("id:1", "id:2")).isEmpty())

    @Test fun compara_dupa_hash_cand_se_cere() {
        val hash = { inv: PortalApi.Invoice -> InvoiceSignature.sha256(inv.key) }
        val prev = setOf(InvoiceSignature.sha256("id:1"))
        assertEquals(listOf(b), PortalApi.newInvoices(listOf(a, b), prev, hash))
    }
}
