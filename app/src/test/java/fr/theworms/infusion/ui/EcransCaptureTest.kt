package fr.theworms.infusion.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import fr.theworms.infusion.InfusionApp
import fr.theworms.infusion.R
import fr.theworms.infusion.ble.BrewPhase
import fr.theworms.infusion.ble.BrewProblem
import fr.theworms.infusion.ble.BrewSession
import fr.theworms.infusion.ble.MachineState
import fr.theworms.infusion.data.AppSettings
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.domain.TeaType
import fr.theworms.infusion.text.text
import fr.theworms.infusion.ui.brew.BrewOverlay
import fr.theworms.infusion.ui.brew.ManualBrewScreen
import fr.theworms.infusion.ui.machine.MachineScreen
import fr.theworms.infusion.ui.teas.TeaEditScreen
import fr.theworms.infusion.ui.teas.TeaDraft
import fr.theworms.infusion.ui.teas.TeaPageScreen
import fr.theworms.infusion.ui.teas.TeasScreen
import fr.theworms.infusion.ui.theme.Accent
import fr.theworms.infusion.ui.theme.Infusion
import fr.theworms.infusion.ui.theme.InfusionTheme
import fr.theworms.infusion.ui.theme.NavDisplay
import fr.theworms.infusion.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Captures des écrans principaux au format d'un grand téléphone (412 × 915 dp), en sombre et
 * ambre comme chez lui, en français et en anglais : build/captures/<langue>-<écran>.png. Sert à repérer
 * un texte coupé ou débordant sans téléphone ; rien n'est comparé automatiquement.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EcransCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<InfusionApp>()
    private val out = File("build/captures").apply { mkdirs() }

    @Config(qualifiers = "fr-w412dp-h915dp-xxhdpi", sdk = [35])
    @Test fun `écrans en français`() = captureAll("fr")

    @Config(qualifiers = "en-w412dp-h915dp-xxhdpi", sdk = [35])
    @Test fun `écrans en anglais`() = captureAll("en")

    private fun captureAll(lang: String) {
        val container = app.container
        val dao = container.database.teas()
        // Photo réelle si la copie de tea-db est là (sinon la vignette de l'app).
        val photo = File(System.getProperty("user.home"), "work/tea-db-ref/docs/assets/photos/catalogue/black-darjeeling-autumnal.webp")
            .takeIf { it.exists() }?.let { src -> File(app.filesDir, "photos/darjeeling.webp").also { it.parentFile?.mkdirs(); src.copyTo(it, overwrite = true) }.path }
        val ids = runBlocking {
            listOf(
                Tea(name = "Darjeeling Autumnal", type = TeaType.BLACK, origin = "Inde (Darjeeling)", temperature = 90, waterMl = 250, steepSeconds = 180,
                    favorite = true, photoPath = photo, onMachine = true, machineSlot = 1, synced = false, catalogId = "black-darjeeling-autumnal",
                    notes = "3 g de thé pour 250 ml. Récolte d'automne, après la mousson. Tasse plus ronde et plus douce que les récoltes de printemps et d'été, liqueur cuivrée, notes boisées et de fruits mûrs."),
                Tea(name = "Sencha", type = TeaType.GREEN, origin = "Japon", temperature = 80, waterMl = 250, steepSeconds = 90, favorite = true, onMachine = true, machineSlot = 2),
                Tea(name = "Earl Grey", type = TeaType.BLACK, temperature = 95, waterMl = 250, steepSeconds = 240, favorite = true),
                Tea(name = "Verveine citronnée du jardin", type = TeaType.HERBAL, temperature = 100, waterMl = 300, steepSeconds = 360, favorite = true,
                    notes = "2 g de thé pour 300 ml. Sans théine. Verveine du jardin, séchée à l'ombre."),
                Tea(name = "Bai Hao Yin Zhen", type = TeaType.WHITE, temperature = 80, waterMl = 200, steepSeconds = 240),
            ).map { dao.insert(it) }
        }
        val settings = AppSettings(theme = ThemeMode.DARK, accent = Accent.AMBRE, navDisplay = NavDisplay.ICONS, lastTeaId = ids[2])
        var screen by mutableScreen()
        compose.setContent {
            InfusionTheme(theme = settings.theme, accent = settings.accent, navDisplay = settings.navDisplay) {
                Box(Modifier.fillMaxSize().background(Infusion.colors.background)) { screen() }
            }
        }
        fun shot(name: String, waitFor: String? = null, content: @Composable () -> Unit) {
            screen = content
            if (waitFor != null) runCatching {
                compose.waitUntil(5_000) {
                    Thread.sleep(20) // Room relit sur ses propres fils : on leur laisse la main.
                    compose.onAllNodesWithText(waitFor, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                }
            }.onFailure { println("[$lang-$name] « $waitFor » absent :\n" + compose.onRoot(useUnmergedTree = true).printToString(maxDepth = 6).take(3000)) }
            compose.waitForIdle()
            // Dessin direct de la fenêtre (captureToImage attend un rendu matériel que Robolectric ne fait pas).
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
            File(out, "$lang-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        shot("accueil", "Sencha") { InfusionAppUi(container, settings) }
        shot("accueil-texte", "Sencha") { InfusionTheme(ThemeMode.DARK, Accent.AMBRE, NavDisplay.BOTH) { InfusionAppUi(container, settings.copy(navDisplay = NavDisplay.BOTH)) } }
        shot("thes", "Sencha") { TeasScreen(container, onEdit = {}) }
        shot("fiche", "Darjeeling") { TeaPageScreen(container, ids[0], connected = false, onBack = {}, onEdit = {}, onBrew = {}) }
        shot("edition", "Darjeeling") { TeaEditScreen(container, ids[0], TeaDraft(), onDone = {}, onDeleted = {}) }
        shot("machine") { MachineScreen(container, settings, onPair = {}, onRequestPermissions = {}) }
        shot("infuser") { ManualBrewScreen() }
        shot("infusion-erreur", "Earl Grey") {
            BrewOverlay(
                session = BrewSession("Earl Grey", 95, 250, 240, phase = BrewPhase.STEEPING, error = text(R.string.brew_link_lost), finished = true, problem = BrewProblem.OFFLINE),
                machine = MachineState(name = "Koala", connection = MachineState.Connection.OFFLINE),
                onStop = {}, onRetry = {}, onEdit = null, onClose = {},
            )
        }
    }

    private fun mutableScreen() = androidx.compose.runtime.mutableStateOf<@Composable () -> Unit>({})
}
