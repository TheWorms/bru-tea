package fr.theworms.infusion

import kotlinx.coroutines.flow.first
import fr.theworms.infusion.text.AppLanguage
import fr.theworms.infusion.text.resolve
import android.app.Application
import fr.theworms.infusion.ble.BleTransport
import fr.theworms.infusion.ble.MachineController
import fr.theworms.infusion.ble.AlertFeedback
import fr.theworms.infusion.ble.BrewService
import fr.theworms.infusion.catalog.CatalogNotifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import fr.theworms.infusion.catalog.TeaCatalog
import fr.theworms.infusion.update.Http
import fr.theworms.infusion.update.Updater
import fr.theworms.infusion.data.AppDatabase
import fr.theworms.infusion.data.PhotoStore
import fr.theworms.infusion.data.SettingsStore
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Dépendances de l'app, créées une fois (pas de framework d'injection : l'app est petite). */
class AppContainer(app: Application) {
    /**
     * Travaux qui doivent survivre à un écran (infusion, écritures, téléchargements). Une exception qui
     * s'échapperait est journalisée au lieu de faire tomber l'app.
     */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e("Infusion", "erreur non rattrapée", e) },
    )
    val database: AppDatabase = AppDatabase.create(app)
    val settings = SettingsStore(app)
    val catalog = TeaCatalog(app)
    val photos = PhotoStore(app)
    val transport = BleTransport(app)
    val machine = MachineController(transport, settings, database.teas(), appScope, resolve = { it.resolve(app) }, appLanguage = { AppLanguage.current(app) })
    val updater = Updater(app, Http.client) { machine.brew.first { it == null || it.finished } }
    private val alertFeedback = AlertFeedback(app, settings)

    init {
        alertFeedback.start(appScope, machine)
        // Nouvelles fiches dans tea-db à l'ouverture : une notification l'annonce.
        appScope.launch { catalog.added.collect { n -> CatalogNotifier.notify(app, n) } }
        // Une infusion commence : son suivi passe dans un service, pour pouvoir quitter l'app.
        appScope.launch {
            machine.brew.map { it != null && !it.finished }.distinctUntilChanged().collect { active -> if (active) BrewService.start(app) }
        }
        // La base est revérifiée à chaque ouverture (MainActivity.onStart).
        appScope.launch { catalog.loadCached() }
        appScope.launch { updater.abandonOldSessions(); updater.check() }
        // Photos qu'aucun thé n'utilise plus (saisie abandonnée, app tuée en route) : supprimées.
        appScope.launch { photos.sweep(database.teas().photoPaths().toSet()) }
    }
}

class InfusionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
