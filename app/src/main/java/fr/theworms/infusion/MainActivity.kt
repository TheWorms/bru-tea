package fr.theworms.infusion

import fr.theworms.infusion.text.text
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.theworms.infusion.data.AppSettings
import fr.theworms.infusion.ui.InfusionAppUi
import fr.theworms.infusion.ui.theme.InfusionTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as InfusionApp).container
        // Confirmation demandée par Android pour une mise à jour : ouverte une seule fois, app au premier plan,
        // et pas pendant une infusion (l'installation tuerait l'app et son suivi).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                container.updater.confirmation.collect { pending ->
                    if (pending != null) container.machine.brew.first { it == null || it.finished }
                    if (pending != null) container.updater.takeConfirmation()?.let { confirm ->
                        runCatching { startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { container.updater.onInstallFailed(text(R.string.install_window_failed)) }
                    }
                }
            }
        }
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            InfusionTheme(theme = settings.theme, accent = settings.accent, navDisplay = settings.navDisplay, metric = settings.metric) {
                InfusionAppUi(container = container, settings = settings)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val container = (application as InfusionApp).container
        container.machine.onForeground()
        container.updater.onForeground()
        // À chaque ouverture : nouvelle version de l'app ? base de thés changée ?
        container.appScope.launch { container.updater.checkIfStale() }
        container.appScope.launch { container.catalog.refreshOnOpen() }
    }

    override fun onStop() {
        super.onStop()
        (application as InfusionApp).container.machine.onBackground()
    }
}
