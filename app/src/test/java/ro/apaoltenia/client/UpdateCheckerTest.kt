package ro.apaoltenia.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Teste pentru compararea de versiuni din UpdateChecker.isNewer. */
class UpdateCheckerTest {

    @Test fun patch_mai_nou() =
        assertTrue(UpdateChecker.isNewer("1.6.1", "1.6.0"))

    @Test fun aceeasi_versiune_nu_e_mai_noua() =
        assertFalse(UpdateChecker.isNewer("1.6.0", "1.6.0"))

    @Test fun minor_mai_nou() =
        assertTrue(UpdateChecker.isNewer("1.7.0", "1.6.9"))

    @Test fun major_mai_nou() =
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))

    /** Capcana clasica: compararea ca text ar da "1.10.0" < "1.9.0". */
    @Test fun zece_e_mai_mare_decat_noua() =
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"))

    /** "1.6" si "1.6.0" sunt egale (segmentele lipsa conteaza ca 0). */
    @Test fun lipsa_segment_e_egal() =
        assertFalse(UpdateChecker.isNewer("1.6", "1.6.0"))

    /** Sufixul non-numeric e ignorat: 1.6.0-beta == 1.6.0, deci nu e mai nou. */
    @Test fun sufix_beta_nu_e_mai_nou() =
        assertFalse(UpdateChecker.isNewer("1.6.0-beta", "1.6.0"))

    /** Prefixul numeric al fiecarui segment e pastrat, nu eliminat. */
    @Test fun segment_cu_litere_pastreaza_prefixul_numeric() =
        assertTrue(UpdateChecker.isNewer("1.7.0", "1.6.0"))

    @Test fun mai_vechi_nu_e_mai_nou() =
        assertFalse(UpdateChecker.isNewer("1.5.9", "1.6.0"))
}
