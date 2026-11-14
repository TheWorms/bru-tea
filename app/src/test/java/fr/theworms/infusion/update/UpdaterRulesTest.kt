package fr.theworms.infusion.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun `en attente de la fin du thé, le bouton est occupé`() {
        assertFalse(UpdateStatus(available = "0.1.21").busy)
        assertTrue(UpdateStatus(available = "0.1.21", waitingBrew = true).busy)
        assertTrue(UpdateStatus(available = "0.1.21", progress = 40).busy)
        assertTrue(UpdateStatus(available = "0.1.21", verifying = true).busy)
        assertTrue(UpdateStatus(available = "0.1.21", installing = true).busy)
    }

    @Test fun `seul le retour de la session en cours compte`() {
        val aborted = PackageInstaller.STATUS_FAILURE_ABORTED
        // Ancienne session abandonnée au nouvel appui : son « annulée » est ignoré.
        assertFalse(Updater.concernsSession(aborted, statusSessionId = 12, current = 13))
        // Sessions du lancement précédent, abandonnées au démarrage : rien n'est en cours.
        assertFalse(Updater.concernsSession(aborted, statusSessionId = 12, current = null))
        assertFalse(Updater.concernsSession(PackageInstaller.STATUS_PENDING_USER_ACTION, statusSessionId = 12, current = 13))
        assertTrue(Updater.concernsSession(aborted, statusSessionId = 13, current = 13))
        assertTrue(Updater.concernsSession(PackageInstaller.STATUS_SUCCESS, statusSessionId = 12, current = null))
        assertTrue(Updater.concernsSession(aborted, statusSessionId = Updater.NO_SESSION, current = 13))
    }
}
