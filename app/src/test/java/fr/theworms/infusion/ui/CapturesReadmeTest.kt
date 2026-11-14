package fr.theworms.infusion.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import fr.theworms.infusion.InfusionApp
import fr.theworms.infusion.R
import fr.theworms.infusion.catalog.Catalog
import fr.theworms.infusion.data.AppSettings
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.ui.machine.MachineScreen
import fr.theworms.infusion.ui.teas.TeaEditScreen
import fr.theworms.infusion.ui.theme.Accent
import fr.theworms.infusion.ui.theme.Infusion
import fr.theworms.infusion.ui.theme.InfusionTheme
import fr.theworms.infusion.ui.theme.NavDisplay
import fr.theworms.infusion.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Captures du README (les JPEG de docs/captures) : l'app en clair, couleur Matcha, en français, avec des thés de tea-db et
 * leurs photos. Écrites dans build/captures/readme/ (591 px de large, comme celles du README), à recopier dans
 * docs/captures/ quand l'app change d'aspect. Il faut la copie locale de tea-db (~/work/tea-db-ref) ; sans elle, le
 * test est ignoré. Pas de machine dans un test : la pastille propose d'en associer une.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w412dp-h915dp-xxhdpi", sdk = [35])
class CapturesReadmeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<InfusionApp>()
    private fun s(id: Int) = app.getString(id)
    private val teaDb = File(System.getProperty("user.home"), "work/tea-db-ref/docs")
    private val out = File("build/captures/readme")

    @Test fun `captures du README`() {
        assumeTrue("copie locale de tea-db absente", File(teaDb, "thes.json").exists())
        out.mkdirs()
        val container = app.container
        // Base de thés lue dans la copie locale (pas de réseau dans les tests).
        File(app.filesDir, "catalogue/thes.json").apply { parentFile?.mkdirs() }.writeBytes(File(teaDb, "thes.json").readBytes())
        runBlocking { container.catalog.loadCached() }
        val earlGrey = insertTeas()

        val settings = AppSettings(theme = ThemeMode.LIGHT, accent = Accent.MATCHA, navDisplay = NavDisplay.ICONS)
        var screen by mutableStateOf<@Composable () -> Unit>({ InfusionAppUi(container, settings) })
        compose.setContent {
            InfusionTheme(theme = settings.theme, accent = settings.accent, navDisplay = settings.navDisplay) {
                Box(Modifier.fillMaxSize().background(Infusion.colors.background)) { screen() }
            }
        }

        tab(R.string.tab_teas)
        settle("Darjeeling Autumnal")
        save("thes")

        tab(R.string.tab_brew)
        settle(s(R.string.manual_save))
        save("infuser")

        tab(R.string.tab_machine)
        settle("Oolong Darjeeling")
        save("machine")

        // Fiche d'un thé, nom touché : la base suggère ses fiches.
        screen = { TeaEditScreen(container, earlGrey, null, onDone = {}, onDeleted = {}) }
        settle("Earl Grey")
        compose.onNodeWithContentDescription(s(R.string.edit_name_label)).performClick()
        settle("Earl Grey Fumé")
        save("modifier")
    }

    /** Réglages, plus bas dans l'onglet Machine : de leur titre jusqu'au bip, dans une fenêtre assez haute pour les tenir. */
    @Config(qualifiers = "fr-w412dp-h2400dp-xxhdpi", sdk = [35])
    @Test fun `captures du README, réglages`() {
        assumeTrue("copie locale de tea-db absente", File(teaDb, "thes.json").exists())
        out.mkdirs()
        val settings = AppSettings(theme = ThemeMode.LIGHT, accent = Accent.MATCHA, navDisplay = NavDisplay.ICONS)
        compose.setContent {
            InfusionTheme(theme = settings.theme, accent = settings.accent, navDisplay = settings.navDisplay) {
                Box(Modifier.fillMaxSize().background(Infusion.colors.background)) {
                    MachineScreen(app.container, settings, onPair = {}, onRequestPermissions = {})
                }
            }
        }
        settle(s(R.string.options_beep))
        val top = compose.onNodeWithText(s(R.string.machine_section_settings)).fetchSemanticsNode().boundsInWindow.top
        val bottom = compose.onNode(hasText(s(R.string.options_beep)) and isToggleable()).fetchSemanticsNode().boundsInWindow.bottom
        val margin = 16 * app.resources.displayMetrics.density
        save("reglages", (top - margin).toInt(), (bottom + margin).toInt())
    }

    private fun tab(label: Int) = compose.onNode(hasContentDescription(s(label)) and isSelectable()).performClick()

    /** Texte attendu à l'écran (Room relit sur ses fils), puis un temps pour décoder les photos. */
    private fun settle(text: String?) {
        if (text != null) runCatching {
            compose.waitUntil(5_000) {
                Thread.sleep(20)
                compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }.onFailure { println("[captures README] « $text » absent") }
        repeat(30) { Thread.sleep(100); compose.waitForIdle() }
    }

    /** Fenêtre dessinée, coupée de [from] à [to] (en pixels), ramenée à 591 px de large, en JPEG. */
    private fun save(name: String, from: Int = 0, to: Int = Int.MAX_VALUE) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(full)) }
        val y0 = from.coerceIn(0, full.height - 1)
        val y1 = to.coerceIn(y0 + 1, full.height)
        val cut = Bitmap.createBitmap(full, 0, y0, full.width, y1 - y0)
        val scaled = Bitmap.createScaledBitmap(cut, WIDTH, cut.height * WIDTH / cut.width, true)
        File(out, "$name.jpg").outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }

    /** Thés repris de la base, avec leur photo : cinq sur la machine (synchronisés), six favoris. Rend l'id de l'Earl Grey. */
    private fun insertTeas(): Long {
        val catalog = app.container.catalog
        fun tea(id: String, favorite: Boolean, slot: Int? = null): Tea {
            val e = checkNotNull(catalog.entry(id)) { "fiche $id absente de tea-db" }
            val photo = e.photo?.let { File(teaDb, it) }?.takeIf { it.exists() }
                ?.let { src -> File(app.filesDir, "photos/$id.webp").also { it.parentFile?.mkdirs(); src.copyTo(it, overwrite = true) }.path }
            return Tea(
                name = e.name, type = e.type, origin = e.origin, temperature = e.temperature, waterMl = e.waterMl, steepSeconds = e.steepSeconds,
                favorite = favorite, photoPath = photo, notes = Catalog.notesFor(e), catalogId = e.id,
                onMachine = slot != null, machineSlot = slot, synced = true,
            )
        }
        val teas = listOf(
            tea("oolong-oolong-darjeeling", favorite = true, slot = 1),
            tea("green-laoshan-green", favorite = true, slot = 2),
            tea("black-darjeeling-autumnal", favorite = true, slot = 3),
            tea("white-bai-hao-yin-zhen", favorite = false, slot = 4),
            tea("white-the-blanc-au-jasmin", favorite = false, slot = 5),
            tea("black-earl-grey", favorite = true),
            tea("oolong-mao-xie", favorite = true),
            tea("herbal-rooibos-citron", favorite = true),
        )
        // Premier décodage sur le fil principal : le moteur natif de Robolectric s'initialise ici, pas dans plusieurs fils
        // à la fois quand les vignettes s'affichent (la JVM des tests tombait : « JniConstants: Class not found »).
        teas.firstNotNullOf { it.photoPath }.let { BitmapFactory.decodeFile(it) }
        val dao = app.container.database.teas()
        return runBlocking { teas.map { it.name to dao.insert(it) } }.first { it.first == "Earl Grey" }.second
    }

    private companion object {
        /** Largeur des captures du README. */
        const val WIDTH = 591
    }
}
