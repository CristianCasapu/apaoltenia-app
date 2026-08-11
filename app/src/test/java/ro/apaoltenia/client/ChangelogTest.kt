package ro.apaoltenia.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Invariante ale changelog-ului din aplicatie. Le verificam la nivel de PR
 * (in CI), nu la momentul tag-ului dupa build-ul semnat — cum face release.yml.
 */
class ChangelogTest {

    private val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.US).apply { isLenient = false }

    @Test fun exista_intrari() =
        assertTrue(Changelog.entries.isNotEmpty())

    /** Cea mai noua intrare trebuie sa coincida cu versiunea aplicatiei. */
    @Test fun prima_intrare_coincide_cu_versiunea() =
        assertEquals(BuildConfig.VERSION_NAME, Changelog.entries.first().version)

    /** Cate versiuni in istoric, atatea in versionCode (o intrare per release). */
    @Test fun numarul_de_intrari_egal_cu_versionCode() =
        assertEquals(BuildConfig.VERSION_CODE, Changelog.entries.size)

    @Test fun versiuni_unice() {
        val versions = Changelog.entries.map { it.version }
        assertEquals(versions.size, versions.toSet().size)
    }

    /** Lista e strict descrescatoare (cea mai noua prima), dupa isNewer. */
    @Test fun versiuni_strict_descrescatoare() {
        Changelog.entries.zipWithNext().forEach { (nou, vechi) ->
            assertTrue(
                "${nou.version} trebuie sa fie mai nou decat ${vechi.version}",
                UpdateChecker.isNewer(nou.version, vechi.version)
            )
        }
    }

    @Test fun datele_sunt_valide_si_neinaintate() {
        val dates = Changelog.entries.map { fmt.parse(it.date) }
        dates.zipWithNext().forEach { (nou, vechi) ->
            assertTrue("datele trebuie sa fie neinaintate in jos", !nou.before(vechi))
        }
    }

    @Test fun fiecare_intrare_are_noutati() {
        Changelog.entries.forEach { entry ->
            assertTrue("${entry.version} nu are noutati", entry.changes.isNotEmpty())
            entry.changes.forEach { line ->
                assertFalse("bullet gol in ${entry.version}", line.isBlank())
                assertEquals("bullet cu spatii la capete", line.trim(), line)
            }
        }
    }
}
