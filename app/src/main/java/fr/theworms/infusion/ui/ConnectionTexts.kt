package fr.theworms.infusion.ui

import androidx.annotation.StringRes
import fr.theworms.infusion.R
import fr.theworms.infusion.ble.MachineState.Connection
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.text.text

/**
 * Ce que l'app dit de la liaison hors de l'onglet Machine (pastille, accueil, fiche d'un thé, mode manuel) :
 * la vraie cause quand elle est connue (Bluetooth du téléphone coupé, appairage perdu) plutôt qu'un « hors de
 * portée » pour tout, qui laissait sans réponse un appui sur la pastille.
 */
object ConnectionTexts {
    private val PAIRING_LOST = text(R.string.problem_pairing_lost)

    /** Libellé de la pastille de l'en-tête et de la ligne d'état de l'accueil ; null : connectée (le nom de la machine). */
    @StringRes
    fun pillLabel(connection: Connection, bluetoothOn: Boolean, problem: UiText?): Int? = when (connection) {
        Connection.NONE -> R.string.pill_pair
        Connection.CONNECTING -> R.string.pill_connecting
        Connection.OFFLINE -> when {
            !bluetoothOn -> R.string.pill_bluetooth_off
            problem == PAIRING_LOST -> R.string.pill_pairing_lost
            else -> R.string.pill_offline
        }
        Connection.CONNECTED -> null
    }

    /**
     * Ligne sous l'état, sur l'accueil : ce qu'il faut faire quand la cause est connue. Rien pour une machine
     * simplement éteinte ou loin (le cas courant, déjà dit par « Hors de portée »), ni pendant la connexion.
     */
    fun homeHint(connection: Connection, bluetoothOn: Boolean, problem: UiText?): UiText? = when (connection) {
        Connection.CONNECTED, Connection.CONNECTING -> null
        Connection.NONE, Connection.OFFLINE -> when {
            !bluetoothOn && connection == Connection.OFFLINE -> text(R.string.problem_bluetooth_off)
            problem is UiText.Res && problem.id == R.string.problem_unreachable -> null
            else -> problem
        }
    }

    /** Pourquoi « Infuser » est grisé (fiche d'un thé, mode manuel) ; null quand la machine est connectée. */
    fun whyNoBrew(connection: Connection, bluetoothOn: Boolean, problem: UiText?): UiText? = when (connection) {
        Connection.NONE -> text(R.string.machine_pair_hint)
        Connection.CONNECTING -> text(R.string.machine_connecting_hint)
        Connection.OFFLINE -> when {
            !bluetoothOn -> text(R.string.problem_bluetooth_off)
            problem == PAIRING_LOST -> PAIRING_LOST
            else -> text(R.string.machine_unreachable)
        }
        Connection.CONNECTED -> null
    }
}
