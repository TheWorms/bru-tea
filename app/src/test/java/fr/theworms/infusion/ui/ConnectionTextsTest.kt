package fr.theworms.infusion.ui

import fr.theworms.infusion.R
import fr.theworms.infusion.ble.MachineState.Connection
import fr.theworms.infusion.text.raw
import fr.theworms.infusion.text.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTextsTest {
    private val unreachable = text(R.string.problem_unreachable, text(R.string.link_no_answer))
    private val pairingLost = text(R.string.problem_pairing_lost)

    @Test fun `pastille, la cause connue plutôt que « hors de portée » pour tout`() {
        assertEquals(R.string.pill_bluetooth_off, ConnectionTexts.pillLabel(Connection.OFFLINE, bluetoothOn = false, problem = null))
        assertEquals(R.string.pill_bluetooth_off, ConnectionTexts.pillLabel(Connection.OFFLINE, bluetoothOn = false, problem = unreachable))
        assertEquals(R.string.pill_pairing_lost, ConnectionTexts.pillLabel(Connection.OFFLINE, bluetoothOn = true, problem = pairingLost))
        assertEquals(R.string.pill_offline, ConnectionTexts.pillLabel(Connection.OFFLINE, bluetoothOn = true, problem = unreachable))
        assertEquals(R.string.pill_offline, ConnectionTexts.pillLabel(Connection.OFFLINE, bluetoothOn = true, problem = null))
    }

    @Test fun `pastille, sans machine, en connexion, connectée`() {
        assertEquals(R.string.pill_pair, ConnectionTexts.pillLabel(Connection.NONE, bluetoothOn = false, problem = null))
        assertEquals(R.string.pill_connecting, ConnectionTexts.pillLabel(Connection.CONNECTING, bluetoothOn = true, problem = null))
        assertNull(ConnectionTexts.pillLabel(Connection.CONNECTED, bluetoothOn = true, problem = null))
    }

    @Test fun `accueil, ce qu'il faut faire, mais rien pour une machine simplement éteinte`() {
        assertEquals(text(R.string.problem_bluetooth_off), ConnectionTexts.homeHint(Connection.OFFLINE, bluetoothOn = false, problem = null))
        assertEquals(pairingLost, ConnectionTexts.homeHint(Connection.OFFLINE, bluetoothOn = true, problem = pairingLost))
        assertNull(ConnectionTexts.homeHint(Connection.OFFLINE, bluetoothOn = true, problem = unreachable))
        assertNull(ConnectionTexts.homeHint(Connection.OFFLINE, bluetoothOn = true, problem = null))
        // Premier appairage raté : la consigne reste affichée.
        val first = text(R.string.problem_pairing_first)
        assertEquals(first, ConnectionTexts.homeHint(Connection.NONE, bluetoothOn = true, problem = first))
        assertNull(ConnectionTexts.homeHint(Connection.CONNECTING, bluetoothOn = true, problem = first))
        assertNull(ConnectionTexts.homeHint(Connection.CONNECTED, bluetoothOn = true, problem = raw("x")))
    }

    @Test fun `infuser grisé, pas « hors de portée » sans machine, en connexion ou Bluetooth coupé`() {
        assertEquals(text(R.string.machine_pair_hint), ConnectionTexts.whyNoBrew(Connection.NONE, bluetoothOn = true, problem = null))
        assertEquals(text(R.string.machine_connecting_hint), ConnectionTexts.whyNoBrew(Connection.CONNECTING, bluetoothOn = true, problem = null))
        assertEquals(text(R.string.problem_bluetooth_off), ConnectionTexts.whyNoBrew(Connection.OFFLINE, bluetoothOn = false, problem = unreachable))
        assertEquals(pairingLost, ConnectionTexts.whyNoBrew(Connection.OFFLINE, bluetoothOn = true, problem = pairingLost))
        assertEquals(text(R.string.machine_unreachable), ConnectionTexts.whyNoBrew(Connection.OFFLINE, bluetoothOn = true, problem = unreachable))
        assertNull(ConnectionTexts.whyNoBrew(Connection.CONNECTED, bluetoothOn = true, problem = null))
    }

    @Test fun `autorisation, un résultat vide (demande annulée) n'ouvre pas les réglages d'Android`() {
        assertFalse(refusedForGood(emptyMap(), granted = false, rationale = false, elapsedMs = 50))
        // Vrai refus définitif : réponse immédiate, sans justification à proposer.
        val refused = mapOf("android.permission.BLUETOOTH_CONNECT" to false, "android.permission.BLUETOOTH_SCAN" to false)
        assertTrue(refusedForGood(refused, granted = false, rationale = false, elapsedMs = 50))
        // Réponse donnée dans la fenêtre d'Android (plus lente), justification possible, ou autorisation accordée : non.
        assertFalse(refusedForGood(refused, granted = false, rationale = false, elapsedMs = 2_000))
        assertFalse(refusedForGood(refused, granted = false, rationale = true, elapsedMs = 50))
        assertFalse(refusedForGood(refused.mapValues { true }, granted = true, rationale = false, elapsedMs = 50))
    }
}
