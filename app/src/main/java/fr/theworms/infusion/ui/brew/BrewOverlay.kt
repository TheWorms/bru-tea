package fr.theworms.infusion.ui.brew

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.activity.compose.LocalActivity
import fr.theworms.infusion.text.text
import fr.theworms.infusion.text.asString
import fr.theworms.infusion.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.theworms.infusion.ble.AlertKind
import fr.theworms.infusion.ble.BrewPhase
import fr.theworms.infusion.ble.BrewProblem
import fr.theworms.infusion.ble.BrewSession
import fr.theworms.infusion.ble.MachineAlert
import fr.theworms.infusion.ble.MachineState
import fr.theworms.infusion.ble.MachineTexts
import fr.theworms.infusion.domain.MachineLimits
import fr.theworms.infusion.ui.components.MachineAlertPanel
import fr.theworms.infusion.ui.components.specLine
import fr.theworms.infusion.ui.theme.AlertError
import fr.theworms.infusion.ui.theme.AlertWarn
import fr.theworms.infusion.ui.theme.Infusion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Infusion en cours, d'après l'état que la machine envoie toutes les 3 secondes. Ce que la machine
 * demande (eau, couvercle…) et les échecs clignotent comme sur l'accueil ; l'app ne relance jamais
 * d'elle-même : c'est « Relancer » ou « Réessayer » qui le fait.
 */
@Composable
fun BrewOverlay(
    session: BrewSession,
    machine: MachineState,
    onStop: suspend () -> Unit,
    onRetry: suspend () -> Unit,
    onEdit: ((Long) -> Unit)?,
    onClose: () -> Unit,
) {
    val c = Infusion.colors
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var retrying by remember { mutableStateOf(false) }
    LaunchedEffect(session.finished) { while (!session.finished) { now = System.currentTimeMillis(); delay(500) } }
    // Retour pendant l'infusion : l'app passe en arrière-plan, l'infusion continue (suivie en notification).
    val activity = LocalActivity.current
    BackHandler { if (session.finished) onClose() else activity?.moveTaskToBack(true) }

    val live = if (machine.connected) MachineTexts.alert(machine.status, machine.subStatus) else null
    val error = session.error
    val problem = session.problem
    // Machine pas prête au départ : on montre ce qu'elle demande ; quand c'est réglé, « Relancer » s'active.
    val ready = problem == BrewProblem.NOT_READY && live == null
    val alert: MachineAlert? = when {
        // Liaison perdue pendant l'infusion : la session reste ouverte, la liaison est retentée.
        session.linkDown && !session.finished -> MachineAlert(AlertKind.OFFLINE, text(R.string.overlay_unreachable, machine.name), text(R.string.notif_reconnecting))
        error == null -> if (!session.finished) live else null
        problem == BrewProblem.NOT_READY -> live
        problem == BrewProblem.OFFLINE || problem == BrewProblem.NO_ANSWER -> MachineAlert(AlertKind.OFFLINE, text(R.string.overlay_unreachable, machine.name), error)
        problem == BrewProblem.REFUSED -> MachineAlert(AlertKind.ERROR, text(R.string.overlay_refused), error)
        else -> MachineAlert(AlertKind.ERROR, text(R.string.alert_error_title), error)
    }
    val alertColor = when {
        alert?.error == true -> AlertError
        alert != null || (error != null && !ready) -> AlertWarn
        else -> null
    }

    val phases = listOf(BrewPhase.SENDING, BrewPhase.FILLING, BrewPhase.STEEPING, BrewPhase.SERVING, BrewPhase.DONE)
    val current = if (session.phase == BrewPhase.CUP) BrewPhase.SENDING else session.phase
    // La machine se tait en infusant : décompte local depuis l'acceptation, recalé quand l'infusion commence.
    val remaining = if (session.accepted && !session.finished && session.phase < BrewPhase.SERVING && session.steepStartedAt > 0)
        (session.steepSeconds - ((now - session.steepStartedAt) / 1000).toInt()).coerceAtLeast(0) else null
    val progress = when (current) {
        BrewPhase.SENDING, BrewPhase.CUP -> 0.05f
        BrewPhase.FILLING -> if (remaining != null) 0.2f + 0.65f * (1f - remaining.toFloat() / session.steepSeconds.coerceAtLeast(1)) else 0.2f
        BrewPhase.STEEPING -> 0.25f + 0.6f * (1f - (remaining ?: session.steepSeconds).toFloat() / session.steepSeconds.coerceAtLeast(1))
        BrewPhase.SERVING -> 0.9f
        BrewPhase.DONE -> 1f
    }

    Column(
        // Les appuis s'arrêtent ici : rien ne passe à l'écran caché dessous.
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }
            .background(c.deep).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.overlay_header, machine.name.uppercase()), style = MaterialTheme.typography.labelSmall, color = c.onDeepMuted)
            Text(session.name, style = MaterialTheme.typography.headlineMedium, color = c.onDeep)
            Text(specLine(session.temperature, session.waterMl, session.steepSeconds), style = MaterialTheme.typography.bodyMedium, color = c.onDeepMuted)
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                val track = c.onDeep.copy(alpha = 0.12f)
                val bar = alertColor ?: if (current == BrewPhase.FILLING) c.heat else c.accent
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val d = size.minDimension - stroke
                    val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                    drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                    drawArc(bar, -90f, 360f * progress, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            error == null -> stringResource(current.label)
                            ready -> stringResource(R.string.overlay_ready_machine, machine.name)
                            problem == BrewProblem.NOT_READY -> stringResource(R.string.overlay_not_started)
                            problem == BrewProblem.REFUSED -> stringResource(R.string.overlay_refused_short)
                            problem == BrewProblem.OFFLINE -> stringResource(R.string.overlay_nothing_sent)
                            problem == BrewProblem.NO_ANSWER -> stringResource(R.string.overlay_no_answer)
                            else -> stringResource(R.string.overlay_stopped)
                        },
                        style = MaterialTheme.typography.bodyMedium, color = c.onDeepMuted, textAlign = TextAlign.Center,
                    )
                    Text(
                        when {
                            ready -> "✓"
                            error != null -> "!"
                            current == BrewPhase.DONE -> stringResource(R.string.overlay_done)
                            remaining != null -> MachineLimits.steepLabel(remaining)
                            else -> "…"
                        },
                        style = MaterialTheme.typography.displayMedium, color = c.onDeep,
                    )
                    if (current == BrewPhase.DONE && error == null) Text(stringResource(R.string.overlay_enjoy), style = MaterialTheme.typography.bodyMedium, color = c.onDeepMuted)
                }
            }
            val message = session.message
            when {
                alert != null -> MachineAlertPanel(alert)
                ready -> Note(stringResource(R.string.overlay_relaunch_hint))
                message != null -> Note(message.asString())
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                phases.forEach { p ->
                    val done = p.ordinal < current.ordinal || (current == BrewPhase.DONE && error == null)
                    val on = p == current && current != BrewPhase.DONE
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val color = when {
                            done -> c.accent
                            on -> alertColor ?: c.heat
                            else -> c.onDeep.copy(alpha = 0.14f)
                        }
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(9.dp)).background(color))
                        Text(stringResource(p.label), style = MaterialTheme.typography.labelSmall, color = if (on) c.onDeep else c.onDeepMuted)
                    }
                }
            }
            val retry: () -> Unit = { scope.launch { retrying = true; onRetry(); retrying = false } }
            val teaId = session.teaId
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val m = Modifier.weight(1f)
                when {
                    // Liaison perdue : « Arrêter » ne pourrait pas partir ; on peut seulement cesser de suivre.
                    !session.finished && session.linkDown -> OverlayButton(stringResource(R.string.action_close), filled = false, modifier = m, onClick = onClose)
                    // « Arrêter » prend la place de « Infuser » : inactif 1 s, contre un double appui qui arrêterait tout.
                    !session.finished -> OverlayButton(
                        stringResource(R.string.action_stop), filled = false, modifier = m, enabled = now - session.createdAt > 1_000,
                    ) { scope.launch { onStop() } }
                    error == null -> OverlayButton(stringResource(R.string.action_close), filled = true, modifier = m, onClick = onClose)
                    problem == BrewProblem.NOT_READY -> {
                        OverlayButton(stringResource(R.string.action_cancel), filled = false, modifier = m, onClick = onClose)
                        OverlayButton(stringResource(R.string.action_relaunch), filled = true, enabled = ready && !retrying, modifier = m, onClick = retry)
                    }
                    problem == BrewProblem.REFUSED && teaId != null && onEdit != null -> {
                        OverlayButton(stringResource(R.string.action_close), filled = false, modifier = m, onClick = onClose)
                        OverlayButton(stringResource(R.string.action_edit_tea), filled = true, modifier = m) { onEdit(teaId) }
                    }
                    problem == BrewProblem.OFFLINE || problem == BrewProblem.NO_ANSWER || problem == BrewProblem.MACHINE_ERROR -> {
                        OverlayButton(stringResource(R.string.action_close), filled = false, modifier = m, onClick = onClose)
                        OverlayButton(stringResource(R.string.action_retry), filled = true, enabled = !retrying, modifier = m, onClick = retry)
                    }
                    else -> OverlayButton(stringResource(R.string.action_close), filled = true, modifier = m, onClick = onClose)
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    val c = Infusion.colors
    Text(
        text, style = MaterialTheme.typography.bodyLarge, color = c.onDeep, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.onDeep.copy(alpha = 0.10f)).padding(12.dp),
    )
}

@Composable
private fun OverlayButton(label: String, filled: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Infusion.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.height(56.dp).alpha(if (enabled) 1f else 0.4f).clip(shape)
            .then(if (filled) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.onDeep.copy(alpha = 0.28f), shape))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (filled) c.onAccent else c.onDeep)
    }
}
