package fr.theworms.infusion.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fr.theworms.infusion.InfusionApp
import fr.theworms.infusion.R
import fr.theworms.infusion.data.AppSettings
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.domain.MachineLimits
import fr.theworms.infusion.ui.brew.ManualBrewScreen
import fr.theworms.infusion.ui.machine.MachineScreen
import fr.theworms.infusion.ui.teas.TeaEditScreen
import fr.theworms.infusion.ui.teas.TeaPageScreen
import fr.theworms.infusion.ui.theme.Accent
import fr.theworms.infusion.ui.theme.InfusionTheme
import fr.theworms.infusion.ui.theme.NavDisplay
import fr.theworms.infusion.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ce que le lecteur d'écran trouve sur les écrans : noms des boutons, choix sélectionnés, interrupteurs, erreurs. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr-w412dp-h915dp-xxhdpi", sdk = [35])
class AccessibiliteTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<InfusionApp>()
    private fun s(id: Int) = app.getString(id)

    private fun show(content: @Composable () -> Unit) =
        compose.setContent { InfusionTheme(ThemeMode.LIGHT, Accent.SAUGE, NavDisplay.BOTH) { content() } }

    /** Room relit sur ses propres fils : on attend que le texte paraisse. */
    private fun waitForText(text: String) = compose.waitUntil(5_000) {
        Thread.sleep(20)
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun insert(tea: Tea): Long = runBlocking { app.container.database.teas().insert(tea) }

    @Test fun `fiche d'un thé, les boutons ronds sont nommés par leur action`() {
        val id = insert(Tea(name = "Sencha", temperature = 80, waterMl = 250, steepSeconds = 90, onMachine = true, synced = false))
        show { TeaPageScreen(app.container, id, connected = false, onBack = {}, onEdit = {}, onBrew = {}) }
        waitForText("Sencha")
        listOf(R.string.action_back, R.string.sync_not_yet, R.string.fav_add, R.string.edit_this_tea).forEach {
            compose.onNodeWithContentDescription(s(it)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    @Test fun `mode manuel, « − » dit ce qu'il règle, le choix pris est « sélectionné », le curseur dit sa valeur`() {
        show { ManualBrewScreen() }
        compose.onNodeWithContentDescription(s(R.string.manual_cooler)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithContentDescription(s(R.string.manual_longer)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        // Départ à 90 °C : la pastille Oolong est le choix pris.
        compose.onNodeWithText(s(R.string.chip_oolong)).assertIsSelected()
        compose.onNodeWithText(s(R.string.chip_green)).assertIsNotSelected()
        compose.onNodeWithContentDescription(s(R.string.label_water))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, MachineLimits.waterLabel(250, true)))
    }

    @Test fun `édition, interrupteur lu avec son titre, nom manquant affiché là où il est, et on y remonte`() {
        val id = insert(Tea(name = "", temperature = 80, waterMl = 250, steepSeconds = 90))
        show { TeaEditScreen(app.container, id, null, onDone = {}, onDeleted = {}) }
        val save = s(R.string.action_save)
        waitForText(save)
        compose.onNode(hasText(s(R.string.edit_favorite)) and isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText(save).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.edit_name_missing)).assertIsDisplayed()
        compose.onNodeWithContentDescription(s(R.string.edit_name_label)).assertIsFocused()
    }

    @Test fun `onglet Machine, couleur choisie et thème « sélectionnés », interrupteurs lus avec leur titre`() {
        val settings = AppSettings(theme = ThemeMode.LIGHT, accent = Accent.AMBRE)
        show { MachineScreen(app.container, settings, onPair = {}, onRequestPermissions = {}) }
        compose.onNodeWithContentDescription(s(R.string.accent_ambre)).performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription(s(R.string.accent_sauge)).assertIsNotSelected()
        compose.onNodeWithText(s(R.string.theme_light)).assertIsSelected()
        compose.onNodeWithText(s(R.string.theme_dark)).assertIsNotSelected()
        compose.onNode(hasText(s(R.string.options_beep)) and isToggleable()).performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }

    @Test fun `barre du bas, l'onglet affiché est « sélectionné »`() {
        show { InfusionAppUi(app.container, AppSettings()) }
        compose.onNodeWithContentDescription(s(R.string.tab_home)).assertIsSelected()
        compose.onNodeWithContentDescription(s(R.string.tab_teas)).assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription(s(R.string.tab_teas)).assertIsSelected()
    }
}
