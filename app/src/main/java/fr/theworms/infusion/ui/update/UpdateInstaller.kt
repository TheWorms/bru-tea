package fr.theworms.infusion.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.theworms.infusion.update.Updater
import kotlinx.coroutines.launch

/**
 * Installe la version disponible : la première fois, Android demande d'autoriser Infusion à installer
 * des applis ; au retour de ce réglage, l'installation reprend seule. Puis téléchargement vérifié et
 * session d'installation (Updater.install). Partagé par le bouton de l'accueil et l'onglet Machine.
 */
@Composable
fun rememberUpdateInstaller(updater: Updater, appScope: CoroutineScope): () -> Unit {
    val context = LocalContext.current
    var waitingPermission by rememberSaveable { mutableStateOf(false) }
    val install: () -> Unit = remember(updater) {
        {
            if (!updater.canInstall()) {
                waitingPermission = true
                context.startActivity(updater.installPermissionIntent())
            } else appScope.launch { updater.downloadAndInstall() } // dans le scope de l'app : quitter l'écran ne l'interrompt pas
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, install) {
        // Retour du réglage d'Android : on reprend si l'autorisation a été donnée.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && waitingPermission) {
                waitingPermission = false
                if (updater.canInstall()) install() else updater.permissionRefused()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return install
}
