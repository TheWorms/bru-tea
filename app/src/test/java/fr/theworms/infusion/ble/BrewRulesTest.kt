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

    @Test fun `écran de fin de l'infusion précédente - pas de clôture tout de suite`() {
        // « Refaire » : la machine montre encore « bonne dégustation » juste après l'acceptation.
        val early = BrewRules.onStatus(accepted, MachineTexts.STANDBY, MachineTexts.SUB_ENJOY, t0 + 300)
        assertFalse(early.finished)
        // Avant l'acceptation non plus.
        assertFalse(BrewRules.onStatus(sent, MachineTexts.STANDBY, MachineTexts.SUB_ENJOY, t0 + 300).finished)
        // Après un état d'infusion, la fin clôt bien.
        val brewing = BrewRules.onStatus(accepted, MachineTexts.BREWING, MachineTexts.SUB_STEEPING, t0 + 20_000)
        assertTrue(BrewRules.onStatus(brewing, MachineTexts.BREWING, MachineTexts.SUB_ENJOY, t0 + 200_000).finished)
    }

    @Test fun `une erreur de la machine n'est pas effacée par une acceptation tardive`() {
        val failed = BrewRules.onStatus(sent, MachineTexts.ERROR, 127, t0 + 100)
        assertTrue(failed.finished)
        assertEquals(BrewProblem.MACHINE_ERROR, failed.problem)
        val late = BrewRules.onAccepted(failed, t0 + 500)
        assertTrue(late.finished)
        assertEquals(BrewProblem.MACHINE_ERROR, late.problem)
        // Une session sans réponse, elle, repart du remplissage.
        val noAnswer = sent.copy(finished = true, problem = BrewProblem.NO_ANSWER, phase = BrewPhase.DONE)
        assertEquals(BrewPhase.FILLING, BrewRules.onAccepted(noAnswer, t0 + 500, brewing = true).phase)
    }

    @Test fun `état 5 d'avant l'acceptation ou « bonne dégustation » - pas de clôture tout de suite`() {
        // Un 5 relevé pendant la connexion (infusion précédente) ne compte pas : seul ce qui suit l'acceptation compte.
        val before = BrewRules.onStatus(sent, MachineTexts.BREWING, MachineTexts.SUB_SERVING, t0 - 500)
        assertFalse(before.seenBrewing)
        val acc = BrewRules.onAccepted(before.copy(seenBrewing = true), t0)
        assertFalse(acc.seenBrewing)
        assertFalse(BrewRules.onStatus(acc, MachineTexts.STANDBY, 4, t0 + 300).finished)
        // « Bonne dégustation » en 5/30 juste après l'acceptation : la trame qui décide ne compte pas elle-même.
        val enjoy = BrewRules.onStatus(accepted, MachineTexts.BREWING, MachineTexts.SUB_ENJOY, t0 + 300)
        assertFalse(enjoy.finished)
        assertFalse(enjoy.seenBrewing)
        assertFalse(BrewRules.onStatus(enjoy, MachineTexts.STANDBY, 4, t0 + 600).finished)
        // Passé 10 s, la même trame clôt (la machine s'est tue pendant l'infusion).
        assertTrue(BrewRules.onStatus(accepted, MachineTexts.BREWING, MachineTexts.SUB_ENJOY, t0 + 11_000).finished)
        // Infusion relue en cours après une absence de réponse (« Réessayer ») : elle compte.
        assertTrue(BrewRules.onAccepted(sent, t0, brewing = true).seenBrewing)
    }

    @Test fun `Arrêter pendant la connexion - la session se clôt sans rien envoyer`() {
        val stopped = BrewRules.onStopBeforeSend(sent)
        assertTrue(stopped.finished)
        assertEquals(BrewProblem.STOPPED, stopped.problem)
        assertNull(stopped.error)
        assertEquals(text(R.string.brew_cancelled), stopped.message)
        // Commande d'infusion déjà partie : il faut l'ordre d'arrêt.
        val sending = sent.copy(commandSent = true)
        assertFalse(BrewRules.onStopBeforeSend(sending).finished)
        assertFalse(BrewRules.onStopBeforeSend(accepted.copy(commandSent = true)).finished)
    }

    @Test fun `Arrêter - ordre parti, la session est close et ne rouvre plus`() {
        val sending = sent.copy(commandSent = true)
        val stopped = BrewRules.onStopSent(sending, sent = true)
        assertTrue(stopped.finished)
        assertEquals(BrewProblem.STOPPED, stopped.problem)
        assertNull(stopped.error)
        assertEquals(text(R.string.brew_stop_sent), stopped.message)
        // La réponse 65 = 0 arrivée après « Arrêter » ne rouvre pas la session (pas de « Thé prêt »).
        val late = BrewRules.onAccepted(stopped, t0 + 400)
        assertTrue(late.finished)
        assertFalse(late.accepted)
        assertEquals(BrewProblem.STOPPED, late.problem)
        // Un refus tardif non plus.
        assertEquals(stopped, BrewRules.onRefused(stopped, text(R.string.brew_not_ready), BrewProblem.NOT_READY))
        // Les trames et l'échéance ne la touchent plus.
        assertEquals(stopped, BrewRules.onStatus(stopped, MachineTexts.STANDBY, 4, t0 + 20_000))
        // Ordre impossible à envoyer : la session continue, avec le message.
        val failed = BrewRules.onStopSent(accepted, sent = false)
        assertFalse(failed.finished)
        assertEquals(text(R.string.brew_stop_failed), failed.message)
        // Un refus ordinaire clôt la session sur l'erreur.
        val refused = BrewRules.onRefused(sending, text(R.string.brew_not_ready), BrewProblem.NOT_READY)
        assertTrue(refused.finished)
        assertEquals(BrewProblem.NOT_READY, refused.problem)
    }

    @Test fun `la session garde son identité d'une copie à l'autre`() {
        assertEquals(sent.id, accepted.id)
        assertEquals(sent.id, BrewRules.onStatus(accepted, MachineTexts.BREWING, MachineTexts.SUB_STEEPING, t0 + 5_000).id)
        assertTrue(BrewSession("Sencha", 80, 200, 120).id != sent.id)
    }

    @Test fun `fin du décompte local - depuis l'acceptation, recalée, plus rien au service`() {
        assertEquals(0L, sent.steepEnd)
        assertEquals(t0 + 180_000, accepted.steepEnd)
        val steeping = BrewRules.onStatus(accepted, MachineTexts.BREWING, MachineTexts.SUB_STEEPING, t0 + 40_000)
        assertEquals(t0 + 40_000 + 180_000, steeping.steepEnd)
        val serving = BrewRules.onStatus(steeping, MachineTexts.BREWING, MachineTexts.SUB_SERVING, t0 + 230_000)
        assertEquals(0L, serving.steepEnd)
    }

    @Test fun `appairage refusé - plus de tentative automatique, sauf pendant une infusion acceptée`() {
        // Sans infusion, ou session pas encore acceptée (« Infuser », « Refaire », « Relancer ») : la 0.1.21 exemptait
        // aussi celle-ci, et le retour au premier plan ou le reconnecteur retentait seul l'abonnement.
        assertTrue(BrewRules.losesPairing(pairing = false, b = null))
        assertTrue(BrewRules.losesPairing(pairing = false, b = sent))
        assertTrue(BrewRules.losesPairing(pairing = false, b = sent.copy(commandSent = true)))
        // Infusion acceptée, liaison perdue : l'échec peut être passager, la reprise continue jusqu'à la fin.
        assertFalse(BrewRules.losesPairing(pairing = false, b = BrewRules.onLinkLost(accepted)))
        // Infusion finie ou close sur une erreur : plus d'exception.
        assertTrue(BrewRules.losesPairing(pairing = false, b = BrewRules.onTick(accepted, accepted.deadline + 1)))
        assertTrue(BrewRules.losesPairing(pairing = false, b = BrewRules.onRefused(sent, text(R.string.brew_no_answer), BrewProblem.NO_ANSWER)))
        // Association : l'échec dit d'associer de nouveau, rien n'est noté.
        assertFalse(BrewRules.losesPairing(pairing = true, b = null))
    }
}
