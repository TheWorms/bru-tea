package fr.theworms.infusion.text

import androidx.compose.ui.res.stringResource
import fr.theworms.infusion.R
import androidx.compose.runtime.Composable
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import fr.theworms.infusion.ble.MachineLanguage

/**
 * Langue de l'app (français ou anglais), gardée par Android (LocaleManager, Android 13+) : aussi réglable
 * dans les réglages d'Android, Applis › Infusion › Langue. Le choix de l'onglet Machine la change en même
 * temps que l'écran de la machine.
 */
object AppLanguage {
    /**
     * Langue des textes réellement affichés (celle choisie, sinon celle qu'Android a retenue parmi les langues
     * du téléphone), lue dans les ressources elles-mêmes : juste aussi avec un téléphone à plusieurs langues.
     */
    fun current(context: Context): MachineLanguage = MachineLanguage.ofTag(context.getString(R.string.lang_code)) ?: MachineLanguage.FR

    /** Change la langue de l'app : Android recrée l'écran dans la nouvelle langue. */
    fun set(context: Context, language: MachineLanguage) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(language.tag)
    }
}

/** Langue dans laquelle l'écran est affiché (pour ce qui s'écrit selon la langue : décimales, notes enregistrées). */
@Composable
fun displayedLanguage(): MachineLanguage = MachineLanguage.ofTag(stringResource(R.string.lang_code)) ?: MachineLanguage.FR
