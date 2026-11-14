package fr.theworms.infusion.ble

import fr.theworms.infusion.R
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.text.plural
import fr.theworms.infusion.text.raw
import fr.theworms.infusion.text.text
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationGateTest {
    @Test fun `un second envoi pendant le premier est refusé sans rien lancer`() = runTest {
        val gate = OperationGate()
        val release = CompletableDeferred<Unit>()
        val first = async { gate.run(MachineOp.SYNC, "occupé") { release.await(); "synchronisé" } }
        runCurrent()
        // L'écran recréé le lit ici : ses boutons restent inactifs.
        assertEquals(MachineOp.SYNC, gate.current.value)
        var started = false
        assertEquals("occupé", gate.run(MachineOp.READ, "occupé") { started = true; "lu" })
        assertEquals("occupé", gate.run(MachineOp.SYNC, "occupé") { started = true; "synchronisé" })
        assertFalse(started)
        release.complete(Unit)
        assertEquals("synchronisé", first.await())
        assertNull(gate.current.value)
        assertEquals("lu", gate.run(MachineOp.READ, "occupé") { "lu" })
    }

    @Test fun `une panne pendant l'envoi libère la place`() = runTest {
        val gate = OperationGate()
        runCatching { gate.run(MachineOp.SETUP, 0) { error("panne") } }
        assertNull(gate.current.value)
        assertEquals(1, gate.run(MachineOp.SETUP, 0) { 1 })
    }
}

class ReadSummaryTest {
    private val unknown4 = text(R.string.read_unknown, 4)

    @Test fun `arrêt après trois préréglages importés, ils sont comptés, pas « rien n'a été importé »`() {
        assertEquals(
            UiText.Join(listOf(unknown4, UiText.Join(listOf(plural(R.plurals.read_imported, 3, 3), raw(".")), ""))),
            MachineController.readSummary(imported = 3, already = 0, stop = unknown4),
        )
    }

    @Test fun `emplacement suivant muet, « Lecture terminée » et le décompte`() {
        val done = text(R.string.read_done)
        assertEquals(
            UiText.Join(listOf(done, UiText.Join(listOf(plural(R.plurals.read_imported, 2, 2), plural(R.plurals.read_already, 1, 1)), ""))),
            MachineController.readSummary(imported = 2, already = 1, stop = done),
        )
    }

    @Test fun `arrêt au premier préréglage, rien n'a été importé, et c'est vrai`() {
        assertEquals(UiText.Join(listOf(unknown4, text(R.string.read_nothing))), MachineController.readSummary(0, 0, unknown4))
    }

    @Test fun `lecture complète, le décompte seul`() {
        assertEquals(
            UiText.Join(listOf(plural(R.plurals.read_imported, 7, 7), plural(R.plurals.read_already, 3, 3)), ""),
            MachineController.readSummary(imported = 7, already = 3),
        )
    }
}
