package fr.theworms.infusion.ui.teas

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import fr.theworms.infusion.InfusionApp
import fr.theworms.infusion.R
import fr.theworms.infusion.ui.theme.Accent
import fr.theworms.infusion.ui.theme.InfusionTheme
import fr.theworms.infusion.ui.theme.NavDisplay
import fr.theworms.infusion.ui.theme.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Rotation pendant que « Enregistrer » attend une photo en chemin : avant, l'écran recréé redevenait actif, la photo
 * était perdue et un second appui créait le thé une seconde fois ; l'ancien écran, détruit, ne se fermait plus.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w412dp-h915dp-xxhdpi", sdk = [35])
class TeaEditRotationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<InfusionApp>()
    private fun s(id: Int) = app.getString(id)

    @Test fun `rotation pendant l'enregistrement, un seul thé, avec sa photo, et l'écran affiché se ferme`() {
        val restorer = StateRestorationTester(compose)
        var closed = 0
        restorer.setContent {
            InfusionTheme(ThemeMode.LIGHT, Accent.SAUGE, NavDisplay.BOTH) {
                TeaEditScreen(app.container, 0L, TeaDraft(), onDone = { closed++ }, onDeleted = {})
            }
        }
        compose.onNodeWithContentDescription(s(R.string.edit_name_label)).performTextInput(NAME)

        // Photo de la base en chemin (téléchargement de quelques secondes), puis « Enregistrer ».
        val work = ViewModelProvider(compose.activity)[TeaEditWork::class.java]
        val photo = File(app.filesDir, "photos/rotation.webp").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }.path
        val gate = CompletableDeferred<Unit>()
        compose.runOnUiThread { work.loadPhoto { gate.await(); work.setPhoto(photo, fromCatalog = true, credit = "") } }
        compose.onNodeWithText(s(R.string.action_save)).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(work.saving)

        // Rotation : l'écran est recréé pendant l'attente ; « Enregistrer » y reste inactif.
        restorer.emulateSavedInstanceStateRestore()
        val button = compose.onAllNodes(hasText(s(R.string.edit_photo_loading)) and hasClickAction())
        button.assertCountEquals(1)
        button[0].performScrollTo().assertIsNotEnabled().performClick()

        // La photo arrive : le thé est enregistré une fois, avec elle, et l'écran affiché se ferme.
        gate.complete(Unit)
        waitFor { closed > 0 }
        repeat(5) { Thread.sleep(20); compose.waitForIdle() }
        assertEquals(1, closed)
        val saved = runBlocking { app.container.database.teas().observeAll().first() }.filter { it.name == NAME }
        assertEquals(1, saved.size)
        assertEquals(photo, saved.single().photoPath)
        // Rien n'a été dit en erreur sur l'écran recréé.
        compose.onAllNodesWithText(s(R.string.edit_save_failed)).assertCountEquals(0)
    }

    /** Room écrit sur ses propres fils, puis l'enregistrement repasse par le fil principal : on laisse tourner les deux. */
    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "toujours pas fait au bout de 5 s" }
            Thread.sleep(20)
            compose.waitForIdle()
        }
    }

    private companion object {
        const val NAME = "Thé de la rotation"
    }
}
