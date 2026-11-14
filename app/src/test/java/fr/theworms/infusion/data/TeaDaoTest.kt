package fr.theworms.infusion.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Requêtes de la base (SQLite d'Android sous Robolectric) : ordres des listes et résultat de la synchronisation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TeaDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val dao = db.teas()

    @After fun close() = db.close()

    private fun tea(name: String, slot: Int? = null, onMachine: Boolean = slot != null) =
        Tea(name = name, temperature = 90, waterMl = 250, steepSeconds = 180, onMachine = onMachine, machineSlot = slot)

    @Test fun `un thé ajouté à la machine passe après ceux qui ont une position`() = runBlocking {
        dao.insert(tea("Assam", 2)); dao.insert(tea("Bancha", 1)); dao.insert(tea("Ceylan", 3))
        dao.insert(tea("Darjeeling", onMachine = true))
        dao.insert(tea("Earl Grey"))
        assertEquals(listOf("Bancha", "Assam", "Ceylan", "Darjeeling"), dao.onMachine().map { it.name })
    }

    @Test fun `noms à initiale accentuée rangés avec leur lettre`() = runBlocking {
        listOf("Yunnan", "écorce d'orange", "Earl Grey", "Échinacée", "assam", "Fujian").forEach { dao.insert(tea(it)) }
        assertEquals(listOf("assam", "Earl Grey", "Échinacée", "écorce d'orange", "Fujian", "Yunnan"), dao.observeAll().first().map { it.name })
    }

    @Test fun `synchronisé seulement si le thé a encore les valeurs écrites`() = runBlocking {
        val written = tea("Assam", onMachine = true).let { it.copy(id = dao.insert(it.copy(synced = false))) }
        suspend fun sync(t: Tea) = dao.setMachineSync(t.id, true, 4, t.name, t.temperature, t.waterMl, t.steepSeconds)
        // Modifié pendant l'envoi : la position est notée, mais il reste à synchroniser.
        dao.update(written.copy(temperature = 80, synced = false))
        sync(written)
        dao.get(written.id)!!.let { assertFalse(it.synced); assertEquals(4, it.machineSlot) }
        // Valeurs inchangées : synchronisé.
        sync(written.copy(temperature = 80))
        assertTrue(dao.get(written.id)!!.synced)
        // Retiré de la machine pendant l'envoi : rien n'est touché.
        dao.update(written.copy(temperature = 80, onMachine = false, machineSlot = null, synced = false))
        sync(written.copy(temperature = 80))
        dao.get(written.id)!!.let { assertFalse(it.synced); assertNull(it.machineSlot) }
    }
}
