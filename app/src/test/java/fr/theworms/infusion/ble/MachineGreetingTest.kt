package fr.theworms.infusion.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mise à l'heure et lecture des réglages (42) : à la première connexion du processus et sur un geste, jamais plus. */
class MachineGreetingTest {
    @Test fun `première connexion du processus, heure et réglages`() {
        assertTrue(MachineController.greets(pairing = false, userInitiated = false, greeted = false))
    }

    @Test fun `reconnexion automatique ensuite, rien, même si le 42 est resté sans réponse`() {
        // Retour au premier plan ou reconnecteur (y compris pendant une infusion) : la 0.1.17 renvoyait le 42 jusqu'à
        // 3 fois tant qu'il n'avait pas abouti, sans geste de l'utilisateur.
        assertFalse(MachineController.greets(pairing = false, userInitiated = false, greeted = true))
    }

    @Test fun `geste de l'utilisateur ou association, heure et réglages de nouveau`() {
        assertTrue(MachineController.greets(pairing = false, userInitiated = true, greeted = true))
        assertTrue(MachineController.greets(pairing = true, userInitiated = true, greeted = true))
        assertTrue(MachineController.greets(pairing = true, userInitiated = false, greeted = true))
    }
}
