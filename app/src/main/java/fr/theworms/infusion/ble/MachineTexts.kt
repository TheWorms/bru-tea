package fr.theworms.infusion.ble

import androidx.annotation.StringRes
import fr.theworms.infusion.R
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.text.text

/** Libellés des états, sous-états et codes de résultat de la machine. */
object MachineTexts {
    const val STANDBY = 1
    const val MENU = 2
    const val BREWING = 5
    const val WARNING = 12
    const val ERROR = 13
    const val LID_REMINDER = 23
    const val BLUETOOTH_MENU = 24

    const val SUB_FILLING = 25
    const val SUB_STEEPING = 26
    const val SUB_RINSE_DELAY = 27
    const val SUB_SERVING = 28
    const val SUB_RINSE = 29
    const val SUB_ENJOY = 30
    const val SUB_PAIRING = 112
    const val SUB_PAIRING_EDIT = 113

    fun status(code: Int?): UiText = when (code) {
        null -> text(R.string.status_unknown)
        1 -> text(R.string.status_standby)
        2 -> text(R.string.status_menu)
        3, 4 -> text(R.string.status_settings)
        5 -> text(R.string.status_brewing)
        6 -> text(R.string.status_dispensing)
        7 -> text(R.string.status_timer)
        8 -> text(R.string.status_cold_tea)
        9 -> text(R.string.status_split_cup)
        10 -> text(R.string.status_multi_steep)
        11 -> text(R.string.status_info)
        12 -> text(R.string.status_warning)
        13 -> text(R.string.status_error)
        14 -> text(R.string.status_hot_tea)
        21 -> text(R.string.status_drying)
        22 -> text(R.string.status_descaling)
        23 -> text(R.string.status_lid_reminder)
        24 -> text(R.string.status_bluetooth_menu)
        in 15..20 -> text(R.string.status_test)
        else -> text(R.string.status_code, code)
    }

    /** Message à afficher pour un sous-état qui demande une action, sinon null. */
    fun message(sub: Int?): UiText? = when (sub) {
        120 -> text(R.string.msg_place_cup)
        121 -> text(R.string.msg_remove_cup)
        122 -> text(R.string.msg_close_lid)
        123 -> text(R.string.msg_open_lid)
        124 -> text(R.string.msg_add_water)
        125 -> text(R.string.msg_insert_chamber)
        126 -> text(R.string.msg_no_nozzle)
        127 -> text(R.string.msg_error)
        130 -> text(R.string.msg_next_cup)
        SUB_PAIRING, SUB_PAIRING_EDIT -> text(R.string.msg_pairing)
        else -> null
    }

    /**
     * Ce que la machine demande ou signale (sous-états 120 à 127, ou état d'avertissement, d'erreur,
     * de rappel du couvercle), à montrer à la place des boutons de l'accueil ; null s'il n'y a rien.
     */
    fun alert(status: Int?, sub: Int?): MachineAlert? = when (sub) {
        124 -> MachineAlert(AlertKind.WATER, R.string.alert_water_title, R.string.alert_water_detail)
        122 -> MachineAlert(AlertKind.LID, R.string.alert_lid_close_title, R.string.alert_lid_close_detail)
        123 -> MachineAlert(AlertKind.LID, R.string.alert_lid_open_title, R.string.alert_lid_open_detail)
        120 -> MachineAlert(AlertKind.CUP, R.string.alert_cup_place_title, R.string.alert_cup_place_detail)
        121 -> MachineAlert(AlertKind.CUP, R.string.alert_cup_remove_title, R.string.alert_cup_remove_detail)
        125 -> MachineAlert(AlertKind.CHAMBER, R.string.alert_chamber_title, R.string.alert_chamber_detail)
        126 -> MachineAlert(AlertKind.NOZZLE, R.string.alert_nozzle_title, R.string.alert_nozzle_detail)
        127 -> MachineAlert(AlertKind.ERROR, R.string.alert_error_title, R.string.alert_error_detail)
        else -> when (status) {
            ERROR -> MachineAlert(AlertKind.ERROR, R.string.alert_error_title, R.string.alert_error_detail)
            WARNING -> MachineAlert(AlertKind.WARNING, R.string.alert_warning_title, R.string.alert_warning_detail)
            LID_REMINDER -> MachineAlert(AlertKind.LID, R.string.alert_lid_reminder_title, R.string.alert_lid_reminder_detail)
            else -> null
        }
    }

    /** Étape d'infusion d'un sous-état, ou null. */
    fun phase(sub: Int?): BrewPhase? = when (sub) {
        SUB_FILLING -> BrewPhase.FILLING
        SUB_RINSE, SUB_RINSE_DELAY -> BrewPhase.FILLING
        SUB_STEEPING -> BrewPhase.STEEPING
        SUB_SERVING -> BrewPhase.SERVING
        SUB_ENJOY -> BrewPhase.DONE
        else -> null
    }

    fun brewResult(code: Int): UiText? = when (code) {
        0 -> null
        1 -> text(R.string.brew_refused)
        3 -> text(R.string.brew_bad_temperature)
        4 -> text(R.string.brew_bad_time)
        5 -> text(R.string.brew_bad_water)
        16 -> text(R.string.brew_not_ready)
        else -> text(R.string.brew_unknown, code)
    }

    fun presetResult(code: Int): UiText? = when (code) {
        0 -> null
        1 -> text(R.string.preset_bad_time)
        2 -> text(R.string.preset_bad_temperature)
        3 -> text(R.string.preset_bad_water)
        4 -> text(R.string.preset_bad_slot)
        5 -> text(R.string.preset_bad_format)
        else -> text(R.string.preset_error, code)
    }
}

enum class AlertKind { WATER, LID, CUP, CHAMBER, NOZZLE, WARNING, ERROR, OFFLINE }

/** Une demande ou une erreur de la machine ; `error` : rouge, sinon orange. */
data class MachineAlert(val kind: AlertKind, val title: UiText, val detail: UiText) {
    constructor(kind: AlertKind, @StringRes title: Int, @StringRes detail: Int) : this(kind, text(title), text(detail))
    val error get() = kind == AlertKind.ERROR || kind == AlertKind.OFFLINE
}

enum class BrewPhase(@StringRes val label: Int) {
    SENDING(R.string.phase_sending), CUP(R.string.phase_cup), FILLING(R.string.phase_filling),
    STEEPING(R.string.phase_steeping), SERVING(R.string.phase_serving), DONE(R.string.phase_done)
}
