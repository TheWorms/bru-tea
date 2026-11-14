package fr.theworms.infusion.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Règles de la recherche et de la vérification des mises à jour. */
class UpdaterRulesTest {
    @Test fun `empreinte publiée obligatoire et bien formée`() {
        val hex = "9b62d2ac5b331066907459f39fbb3c160d3f932cd917031033fdef3ecedb38b9"
        assertEquals(hex, Updater.expectedSha256("$hex  Infusion.0.1.14.apk\n"))
        assertEquals(hex, Updater.expectedSha256(hex.uppercase()))
        assertNull(Updater.expectedSha256(null))
        assertNull(Updater.expectedSha256(""))
        assertNull(Updater.expectedSha256("<html>404</html>"))
        assertNull(Updater.expectedSha256(hex.dropLast(1)))
        assertNull(Updater.expectedSha256(hex.dropLast(1) + "z"))
    }
}
