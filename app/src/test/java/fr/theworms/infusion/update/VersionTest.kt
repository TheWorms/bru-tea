package fr.theworms.infusion.update

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun `lecture et ordre des versions`() {
        assertEquals(Version(0, 1, 2), Version.parse("0.1.2"))
        assertNull(Version.parse("0.1"))
        assertNull(Version.parse("0.1.x"))
        assertTrue(Version(0, 2, 0) > Version(0, 1, 9))
        assertTrue(Version(1, 0, 0) > Version(0, 9, 9))
    }

    private fun among(published: Set<String>): suspend (List<Version>) -> List<Version> = { c -> c.filter { it.toString() in published } }

    @Test fun `la dernière version publiée est trouvée, même si des versions ont été retirées`() = runTest {
        // Cas réel du 14/11 : seule la 0.1.4 est en ligne, l'app installée est en 0.1.2.
        assertEquals(Version(0, 1, 4), findLatest(Version(0, 1, 2), existing = among(setOf("0.1.4"))))
        assertEquals(Version(0, 2, 1), findLatest(Version(0, 1, 1), existing = among(setOf("0.1.3", "0.2.0", "0.2.1"))))
        assertEquals(Version(1, 0, 0), findLatest(Version(0, 9, 4), existing = among(setOf("1.0.0"))))
        // Rien de plus récent : on reste sur la version actuelle.
        assertEquals(Version(0, 2, 1), findLatest(Version(0, 2, 1), existing = among(emptySet())))
    }
}
