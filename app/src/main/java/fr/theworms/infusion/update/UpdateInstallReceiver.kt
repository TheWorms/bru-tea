package fr.theworms.infusion.update

import fr.theworms.infusion.text.text
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.R
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import fr.theworms.infusion.InfusionApp

/**
 * Réponse d'Android à la session d'installation (Updater.install). La confirmation éventuelle n'est
 * pas lancée d'ici (Android la bloque quand l'app n'est pas au premier plan) : elle est confiée à
 * Updater, et MainActivity l'ouvre une seule fois.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val updater = (context.applicationContext as InfusionApp).container.updater
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Log.i("Infusion", "Installation : statut $status ${message.orEmpty()}")
        when {
            status == PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let(updater::onConfirmationNeeded)
                    ?: updater.onInstallFailed(text(R.string.install_no_confirmation))
            status == PackageInstaller.STATUS_SUCCESS -> updater.onInstalled()
            // Seconde fenêtre de confirmation pendant que la première installe : pas un échec.
            status == PackageInstaller.STATUS_FAILURE && message?.contains("Session files in use", ignoreCase = true) == true -> Unit
            else -> updater.onInstallFailed(failureMessage(status), text(if (status == PackageInstaller.STATUS_FAILURE_ABORTED) R.string.update_short_cancelled else R.string.update_short_install_failed))
        }
    }

    companion object {
        const val ACTION = "fr.theworms.infusion.INSTALLATION"

        /** Le message d'Android est en anglais et technique : on en dit le sens. */
        fun failureMessage(status: Int): UiText = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> text(R.string.install_aborted)
            PackageInstaller.STATUS_FAILURE_BLOCKED -> text(R.string.install_blocked)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> text(R.string.install_conflict)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> text(R.string.install_incompatible)
            PackageInstaller.STATUS_FAILURE_INVALID -> text(R.string.install_invalid)
            PackageInstaller.STATUS_FAILURE_STORAGE -> text(R.string.install_storage)
            else -> text(R.string.install_refused)
        }
    }
}
