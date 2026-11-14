package fr.theworms.infusion.ui.teas

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

/** Ce que la fiche d'un thé garde hors de l'écran : photos, chargements en cours, fin de l'enregistrement. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TeaEditWorkTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun `photo, photos ajoutées et fin retrouvées après l'arrêt de l'app en arrière-plan`() {
        val handle = SavedStateHandle()
        TeaEditWork(handle).apply {
            setPhoto("/photos/a.webp", fromCatalog = false, credit = "")
            setPhoto("/photos/b.webp", fromCatalog = true, credit = "Théothèque (IA)")
            saving = true
            finish(TeaEditWork.Finish.SAVED)
        }
        // L'app tuée puis rouverte ne retrouve que le contenu du SavedStateHandle.
        val restored = TeaEditWork(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        assertEquals("/photos/b.webp", restored.photo)
        assertTrue(restored.photoFromCatalog)
        assertEquals("Théothèque (IA)", restored.photoCredit)
        assertEquals(listOf("/photos/a.webp", "/photos/b.webp"), restored.newPhotos)
        assertEquals(TeaEditWork.Finish.SAVED, restored.finished)
        // L'enregistrement s'est arrêté avec l'app : rien ne reste bloqué.
        assertFalse(restored.saving)
    }

    @Test fun `fiche neuve, sans photo ni fin`() {
        val work = TeaEditWork(SavedStateHandle())
        assertNull(work.photo)
        assertFalse(work.photoFromCatalog)
        assertEquals(emptyList<String>(), work.newPhotos)
        assertNull(work.finished)
    }

    @Test fun `photo du thé enregistré, sans crédit ni photo ajoutée à supprimer`() {
        val work = TeaEditWork(SavedStateHandle())
        work.setPhoto("/photos/base.webp", fromCatalog = true, credit = "Théothèque (IA)")
        work.takeNewPhotos()
        work.showStoredPhoto("/photos/mienne.webp")
        assertEquals("/photos/mienne.webp", work.photo)
        assertFalse(work.photoFromCatalog)
        assertEquals("", work.photoCredit)
        assertEquals(emptyList<String>(), work.newPhotos)
    }

    @Test fun `photos ajoutées rendues une seule fois, la photo reste sur la fiche`() {
        val work = TeaEditWork(SavedStateHandle())
        work.setPhoto("/photos/a.webp", fromCatalog = false, credit = "")
        assertEquals(listOf("/photos/a.webp"), work.takeNewPhotos())
        assertEquals(emptyList<String>(), work.takeNewPhotos())
        assertEquals("/photos/a.webp", work.photo)
    }

    @Test fun `photo en chemin comptée jusqu'à son arrivée, un chargement raté ne reste pas compté`() {
        val work = TeaEditWork(SavedStateHandle())
        val gate = CompletableDeferred<Unit>()
        work.loadPhoto { gate.await(); work.setPhoto("/photos/a.webp", fromCatalog = true, credit = "") }
        work.loadPhoto { throw IOException("réseau coupé") }
        idle()
        assertEquals(1, work.photoLoads.value)
        gate.complete(Unit)
        idle()
        assertEquals(0, work.photoLoads.value)
        assertEquals("/photos/a.webp", work.photo)
    }

    @Test fun `« Enregistrer » n'attend pas sans photo en chemin, et pas plus que le délai sinon`() = runBlocking {
        val work = TeaEditWork(SavedStateHandle())
        work.awaitPhotos(60_000)
        work.loadPhoto { awaitCancellation() }
        assertEquals(1, work.photoLoads.value)
        work.awaitPhotos(50)
        assertEquals(1, work.photoLoads.value)
    }
}
