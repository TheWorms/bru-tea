package fr.theworms.infusion.ble

import fr.theworms.infusion.R
import fr.theworms.infusion.text.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suivi d'une infusion par une machine qui se tait pendant qu'elle infuse. */
class BrewRulesTest {
    private val t0 = 1_000_000L
    private val sent = BrewSession("Earl Grey", 95, 250, 180, createdAt = t0)
    private val accepted = BrewRules.onAccepted(sent, t0)

    @Test fun `acceptation - décompte local depuis l'acceptation, remplissage`() {
        assertTrue(accepted.accepted)
        assertEquals(t0, accepted.steepStartedAt)
        assertEquals(BrewPhase.FILLING, accepted.phase)
        assertFalse(accepted.finished)
    }

    @Test fun `début réel de l'infusion - le décompte se recale`() {
        val steeping = BrewRules.onStatus(accepted, MachineTexts.BREWING, MachineTexts.SUB_STEEPING, t0 + 40_000)
        assertEquals(BrewPhase.STEEPING, steeping.phase)
        assertEquals(t0 + 40_000, steeping.steepStartedAt)
        assertTrue(steeping.seenBrewing)
    }

    @Test fun `retour en veille après l'infusion - Prêt, sans erreur`() {
        val brewing = BrewRules.onStatus(accepted, MachineTexts.BREWING, 0, t0 + 2_000)
        val done = BrewRules.onStatus(brewing, MachineTexts.STANDBY, 4, t0 + 200_000)
        assertTrue(done.finished)
        assertEquals(BrewPhase.DONE, done.phase)
        assertNull(done.error)
        // Menu ou menu Bluetooth : pareil.
        assertTrue(BrewRules.onStatus(brewing, MachineTexts.MENU, 0, t0 + 5_000).finished)
        assertTrue(BrewRules.onStatus(brewing, MachineTexts.BLUETOOTH_MENU, 0, t0 + 5_000).finished)
    }

    @Test fun `veille juste après l'acceptation - pas encore la fin`() {
        // La relève à +300 ms peut encore montrer la veille : la clôture attend un état 5 ou 10 s.
        assertFalse(BrewRules.onStatus(accepted, MachineTexts.STANDBY, 4, t0 + 300).finished)
        assertTrue(BrewRules.onStatus(accepted, MachineTexts.STANDBY, 4, t0 + 11_000).finished)
    }

    @Test fun `avertissement - la session reste ouverte, erreur - la session s'arrête`() {
        val warn = BrewRules.onStatus(accepted, MachineTexts.WARNING, 124, t0 + 20_000)
        assertFalse(warn.finished)
        assertTrue(warn.message != null)
        val err = BrewRules.onStatus(accepted, MachineTexts.ERROR, 127, t0 + 20_000)
        assertTrue(err.finished)
        assertEquals(BrewProblem.MACHINE_ERROR, err.problem)
    }

    @Test fun `avant l'acceptation, la veille ne clôt rien`() {
        assertFalse(BrewRules.onStatus(sent, MachineTexts.STANDBY, 4, t0 + 60_000).finished)
    }

    @Test fun `sans nouvelles jusqu'à l'échéance - clôture neutre`() {
        val deadline = t0 + (180 + BrewSession.MARGIN_S) * 1000L
        assertFalse(BrewRules.onTick(accepted, deadline).finished)
        val closed = BrewRules.onTick(accepted, deadline + 1)
        assertTrue(closed.finished)
        assertNull(closed.error)
        assertEquals(text(R.string.brew_done_no_news), closed.message)
        // Rien n'était parti : pas d'échéance.
        assertFalse(BrewRules.onTick(sent, deadline + 1).finished)
    }

    @Test fun `liaison perdue - la session reste ouverte et reprend avec la machine`() {
        val down = BrewRules.onLinkLost(accepted)
        assertTrue(down.linkDown)
        assertFalse(down.finished)
        assertNull(down.error)
        // La machine répond qu'elle infuse : on continue.
        val back = BrewRules.onStatus(down, MachineTexts.BREWING, MachineTexts.SUB_STEEPING, t0 + 60_000)
        assertFalse(back.linkDown)
        assertFalse(back.finished)
        // Elle répond qu'elle est en veille : le thé est prêt.
        assertTrue(BrewRules.onStatus(down, MachineTexts.STANDBY, 4, t0 + 300_000).finished)
        // Elle ne revient pas avant l'échéance : « suivi interrompu ».
        val gone = BrewRules.onTick(down, t0 + (180 + BrewSession.MARGIN_S) * 1000L + 1)
        assertTrue(gone.finished)
        assertEquals(text(R.string.brew_tracking_interrupted), gone.message)
        // Avant l'acceptation, la coupure ne change rien (brew() conclut).
        assertFalse(BrewRules.onLinkLost(sent).linkDown)
    }
}
