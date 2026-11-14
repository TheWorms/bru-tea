package fr.theworms.infusion.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.theworms.infusion.ui.theme.Accent
import fr.theworms.infusion.ui.theme.NavDisplay
import fr.theworms.infusion.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Fichier de réglages illisible (corrompu) : remplacé par des réglages vides plutôt que de bloquer l'app. */
private val Context.dataStore by preferencesDataStore(
    name = "reglages",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Options de l'app (onglet Machine → Options de l'app). */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.LIGHT,
    val accent: Accent = Accent.SAUGE,
    val navDisplay: NavDisplay = NavDisplay.BOTH,
    /** Machine associée (adresse Bluetooth). */
    val machineAddress: String = "",
    /** Nom choisi pour la machine, gardé dans l'app (la machine ne le renvoie pas). Vide : pas encore choisi. */
    val machineName: String = "",
    /** Nom Bluetooth donné par la machine (générique), utilisé tant qu'aucun nom n'est choisi. */
    val machineBleName: String = "",
    val machineFirmware: String = "",
    /** Langue de l'écran de la machine et unités, envoyées avec le nombre de préréglages. */
    val machineLanguage: String = "FR",
    val metric: Boolean = true,
    /** Dernier thé infusé, pour « Refaire ». */
    val lastTeaId: Long = 0,
    val lastBrewAt: Long = 0,
    /** Identifiant stable de cette installation (forme longue de la commande d'infusion). */
    val installId: String = "",
    /** Bip et vibration quand la machine demande quelque chose ou qu'une infusion échoue (activés par défaut). */
    val alertSound: Boolean = true,
    val alertVibration: Boolean = true,
)

/** Nom affiché : celui choisi, sinon le nom Bluetooth, sinon « Maker One ». */
val AppSettings.machineDisplayName: String get() = machineName.ifBlank { machineBleName.ifBlank { "Maker One" } }

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val navDisplay = stringPreferencesKey("affichage_menu")
        val machineAddress = stringPreferencesKey("machine_adresse")
        val machineName = stringPreferencesKey("machine_surnom")
        val machineBleName = stringPreferencesKey("machine_nom_bluetooth")
        val machineFirmware = stringPreferencesKey("machine_firmware")
        val machineLanguage = stringPreferencesKey("machine_langue")
        val metric = booleanPreferencesKey("machine_metrique")
        val lastTeaId = longPreferencesKey("dernier_the")
        val lastBrewAt = longPreferencesKey("derniere_infusion")
        val installId = stringPreferencesKey("installation")
        val alertSound = booleanPreferencesKey("alerte_bip")
        val alertVibration = booleanPreferencesKey("alerte_vibration")
    }

    /**
     * Réglages en flux continu. Une erreur de lecture passagère donne les valeurs par défaut le temps de
     * relire (1 s, puis jusqu'à 10 s entre deux essais), sans terminer le flux.
     */
    val settings: Flow<AppSettings> = context.dataStore.data
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false
            if (attempt == 0L) emit(emptyPreferences())
            delay(minOf(1_000L * (attempt + 1), 10_000L))
            true
        }
        .map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        theme = enumOr(this[Keys.theme], ThemeMode.LIGHT),
        accent = enumOr(this[Keys.accent], Accent.SAUGE),
        navDisplay = enumOr(this[Keys.navDisplay], NavDisplay.BOTH),
        machineAddress = this[Keys.machineAddress].orEmpty(),
        machineName = this[Keys.machineName].orEmpty(),
        machineBleName = this[Keys.machineBleName].orEmpty(),
        machineFirmware = this[Keys.machineFirmware].orEmpty(),
        machineLanguage = this[Keys.machineLanguage] ?: "FR",
        metric = this[Keys.metric] ?: true,
        lastTeaId = this[Keys.lastTeaId] ?: 0,
        lastBrewAt = this[Keys.lastBrewAt] ?: 0,
        installId = this[Keys.installId].orEmpty(),
        alertSound = this[Keys.alertSound] ?: true,
        alertVibration = this[Keys.alertVibration] ?: true,
    )

    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setAccent(value: Accent) = context.dataStore.edit { it[Keys.accent] = value.name }
    suspend fun setNavDisplay(value: NavDisplay) = context.dataStore.edit { it[Keys.navDisplay] = value.name }
    /** Nouvelle machine : l'adresse et son nom Bluetooth ; le nom choisi pour l'ancienne est oublié. */
    suspend fun setMachine(address: String, bleName: String) = context.dataStore.edit {
        if (it[Keys.machineAddress] != address) it[Keys.machineName] = ""
        it[Keys.machineAddress] = address; it[Keys.machineBleName] = bleName
    }
    suspend fun setMachineBleName(name: String) = context.dataStore.edit { it[Keys.machineBleName] = name }
    suspend fun setMachineName(name: String) = context.dataStore.edit { it[Keys.machineName] = name }
    suspend fun setMachineFirmware(value: String) = context.dataStore.edit { it[Keys.machineFirmware] = value }
    suspend fun setMachineLanguage(code: String) = context.dataStore.edit { it[Keys.machineLanguage] = code }
    suspend fun setMetric(value: Boolean) = context.dataStore.edit { it[Keys.metric] = value }
    suspend fun setAlertSound(value: Boolean) = context.dataStore.edit { it[Keys.alertSound] = value }
    suspend fun setAlertVibration(value: Boolean) = context.dataStore.edit { it[Keys.alertVibration] = value }
    suspend fun setLastBrew(teaId: Long) = context.dataStore.edit { it[Keys.lastTeaId] = teaId; it[Keys.lastBrewAt] = System.currentTimeMillis() }

    /** Identifiant d'installation, créé au premier besoin puis gardé. */
    suspend fun installId(): String {
        var id = ""
        context.dataStore.edit { p -> id = p[Keys.installId] ?: java.util.UUID.randomUUID().toString().also { p[Keys.installId] = it } }
        return id
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
