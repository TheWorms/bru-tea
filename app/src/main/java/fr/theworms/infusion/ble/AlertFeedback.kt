package fr.theworms.infusion.ble

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import fr.theworms.infusion.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Bip et vibration quand une alerte apparaît : demande de la machine (eau, couvercle…) ou infusion qui
 * échoue. Une seule fois par alerte (elle clignote ensuite à l'écran), selon les options (actives par défaut).
 */
class AlertFeedback(private val context: Context, private val settings: SettingsStore) {
    private var lastSignal = 0L

    fun start(scope: CoroutineScope, machine: MachineController) {
        scope.launch {
            machine.state.map { if (it.connected) MachineTexts.alert(it.status, it.subStatus) else null }
                .distinctUntilChanged()
                .collect { alert -> if (alert != null) signal(alert.error) }
        }
        scope.launch {
            machine.brew.map { it?.error }.distinctUntilChanged().collect { error -> if (error != null) signal(error = true) }
        }
    }

    private suspend fun signal(error: Boolean) {
        // Le refus de la machine et l'alerte qu'elle affiche arrivent ensemble : un seul signal.
        val now = System.currentTimeMillis()
        if (now - lastSignal < 2_000L) return
        lastSignal = now
        val s = settings.settings.first()
        if (s.alertVibration) vibrate(error)
        if (s.alertSound) beep(error)
    }

    private fun vibrate(error: Boolean) {
        val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return
        val pattern = if (error) longArrayOf(0, 300, 150, 300, 150, 300) else longArrayOf(0, 180, 120, 180)
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    /** Sur le volume des notifications : le mode silencieux du téléphone est respecté. */
    private suspend fun beep(error: Boolean) {
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull() ?: return
        try {
            tone.startTone(if (error) ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD else ToneGenerator.TONE_PROP_BEEP2, if (error) 900 else 500)
            delay(1_000)
        } finally {
            tone.release()
        }
    }
}
