package fr.theworms.infusion.ui.teas

import fr.theworms.infusion.data.Tea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeaEditRulesTest {
    private val synced = Tea(name = "Sencha", temperature = 80, waterMl = 250, steepSeconds = 90, onMachine = true, machineSlot = 3, synced = true)

    @Test fun `venu du mode manuel à 85 °C, 250 ml, 2 min, les réglages s'affichent quand même`() {
        // Ce sont les valeurs d'un thé neuf : avant, l'écran les prenait pour « pas venu du mode manuel ».
        assertTrue(fieldsShownAtStart(isNew = true, draft = TeaDraft()))
        assertTrue(fieldsShownAtStart(isNew = true, draft = TeaDraft(90, 250, 180)))
        assertFalse(fieldsShownAtStart(isNew = true, draft = null))
        assertTrue(fieldsShownAtStart(isNew = false, draft = null))
    }

    @Test fun `une synchronisation finie pendant la saisie n'est pas défaite par « Enregistrer »`() {
        // Ligne relue à l'enregistrement : position 3, synchronisé ; seules les notes ont changé.
        assertEquals(true to 3, machineSyncAfterEdit(synced, onMachine = true, changedForMachine = false))
    }

    @Test fun `réglages changés, à synchroniser de nouveau, position gardée`() {
        assertEquals(false to 3, machineSyncAfterEdit(synced, onMachine = true, changedForMachine = true))
    }

    @Test fun `retiré de la machine, plus de position`() {
        assertEquals(true to null, machineSyncAfterEdit(synced, onMachine = false, changedForMachine = true))
    }

    @Test fun `photo de la base, son crédit est écrit à la suite s'il y en a un`() {
        assertEquals("Théothèque (IA)", catalogPhotoCredit("Théothèque (IA)"))
        assertEquals("Matt Gibson, CC BY-SA 3.0 (Wikimedia Commons)", catalogPhotoCredit("  Matt Gibson, CC BY-SA 3.0 (Wikimedia Commons) "))
        // Fiche sans crédit : « photo de ta base » seul.
        assertNull(catalogPhotoCredit(""))
        assertNull(catalogPhotoCredit("   "))
        assertNull(catalogPhotoCredit(null))
    }

    @Test fun `nouveau thé, à synchroniser s'il va sur la machine, sans position`() {
        assertEquals(false to null, machineSyncAfterEdit(null, onMachine = true, changedForMachine = true))
        assertEquals(true to null, machineSyncAfterEdit(null, onMachine = false, changedForMachine = true))
    }
}
