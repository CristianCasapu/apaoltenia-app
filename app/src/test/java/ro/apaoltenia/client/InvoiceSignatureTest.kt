package ro.apaoltenia.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Teste pentru amprenta facturilor (decodare + extragere semnale + hash). */
class InvoiceSignatureTest {

    // ── decodeJsString ─────────────────────────────────────────────────────
    @Test fun decode_null() =
        assertEquals("", InvoiceSignature.decodeJsString(null))

    @Test fun decode_literal_null() =
        assertEquals("", InvoiceSignature.decodeJsString("null"))

    @Test fun decode_text_simplu() =
        assertEquals("Sold: 12,34 lei", InvoiceSignature.decodeJsString("\"Sold: 12,34 lei\""))

    /** \uXXXX e forma in care evaluateJavascript intoarce diacriticele. */
    @Test fun decode_diacritice_uxxxx() =
        assertEquals("Factură", InvoiceSignature.decodeJsString("\"Factur\\u0103\""))

    @Test fun decode_newline_si_ghilimele() =
        assertEquals("a\n\"b\"", InvoiceSignature.decodeJsString("\"a\\n\\\"b\\\"\""))

    /** Un numar JSON nu e string -> gol (nu aruncam exceptie). */
    @Test fun decode_numar_da_gol() =
        assertEquals("", InvoiceSignature.decodeJsString("123"))

    /** O valoare JSON care nu e string (array) -> gol. */
    @Test fun decode_array_da_gol() =
        assertEquals("", InvoiceSignature.decodeJsString("[1,2]"))

    // ── relevantOf ─────────────────────────────────────────────────────────
    @Test fun relevant_pagina_goala() =
        assertEquals("", InvoiceSignature.relevantOf(""))

    @Test fun relevant_fara_semnale() =
        assertEquals("", InvoiceSignature.relevantOf("Bine ai venit in portal"))

    @Test fun relevant_prinde_suma_lei() =
        assertTrue(InvoiceSignature.relevantOf("Total 1.234,56 lei").contains("lei"))

    @Test fun relevant_prinde_ron() =
        assertTrue(InvoiceSignature.relevantOf("1234 RON").contains("ron"))

    @Test fun relevant_prinde_factura_cu_diacritice() =
        assertTrue(InvoiceSignature.relevantOf("Factură noua").isNotEmpty())

    @Test fun relevant_prinde_sold_si_restant() {
        assertTrue(InvoiceSignature.relevantOf("Sold curent").isNotEmpty())
        assertTrue(InvoiceSignature.relevantOf("Sold restant").isNotEmpty())
    }

    // ── sha256 ─────────────────────────────────────────────────────────────
    @Test fun sha256_vector_gol() =
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            InvoiceSignature.sha256("")
        )

    @Test fun sha256_stabil() =
        assertEquals(InvoiceSignature.sha256("factura|lei"), InvoiceSignature.sha256("factura|lei"))
}
