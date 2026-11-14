package fr.theworms.infusion.ui.home

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import fr.theworms.infusion.ui.ConnectionTexts
import androidx.compose.foundation.lazy.LazyRow
import fr.theworms.infusion.ui.components.specLine
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import fr.theworms.infusion.text.asString
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import fr.theworms.infusion.ble.MachineAlert
import fr.theworms.infusion.ui.theme.AlertError
import fr.theworms.infusion.ui.theme.AlertWarn
import fr.theworms.infusion.ui.components.MachineAlertPanel
import fr.theworms.infusion.ui.update.rememberUpdateInstaller
import fr.theworms.infusion.update.UpdateStatus
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.theworms.infusion.AppContainer
import fr.theworms.infusion.R
import fr.theworms.infusion.ble.MachineState
import fr.theworms.infusion.ble.MachineTexts
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.ui.components.SectionTitle
import fr.theworms.infusion.ui.components.TeaCard
import fr.theworms.infusion.ui.theme.Infusion
import fr.theworms.infusion.ui.theme.NavDisplay

@Composable
fun HomeScreen(
    container: AppContainer, lastTeaId: Long, bluetoothOn: Boolean = true,
    onManual: () -> Unit, onManageFavorites: () -> Unit, onOpenTea: (Long) -> Unit, onRepeat: (Tea) -> Unit,
) {
    val update by container.updater.status.collectAsStateWithLifecycle()
    val install = rememberUpdateInstaller(container.updater, container.appScope)
    val favorites by remember { container.database.teas().observeFavorites() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val machine by container.machine.state.collectAsStateWithLifecycle()
    val lastTea by remember(lastTeaId) { container.database.teas().observe(lastTeaId) }.collectAsStateWithLifecycle(initialValue = null)
    val alert = if (machine.connected) MachineTexts.alert(machine.status, machine.subStatus) else null
    var confirmRepeat by remember { mutableStateOf(false) }
    // Le bloc machine et le titre à leur hauteur, les favoris dans ce qui reste (deux rangées d'au moins 150 dp). Si
    // l'écran est trop bas (paysage, grande police), toute la page défile au lieu d'écraser « Refaire » et les favoris.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        TopThenRest(
            viewport = maxHeight - 18.dp, gap = 16.dp, minRest = MIN_ROW * 2 + 12.dp, stretch = favorites.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MachineBlock(
                    machineName = machine.name,
                    stateLine = when (machine.connection) {
                        MachineState.Connection.NONE -> stringResource(R.string.home_no_machine)
                        // La cause quand elle est connue (Bluetooth coupé, appairage perdu), sinon « Hors de portée ».
                        MachineState.Connection.CONNECTING, MachineState.Connection.OFFLINE ->
                            stringResource(ConnectionTexts.pillLabel(machine.connection, bluetoothOn, machine.problem) ?: R.string.pill_offline)
                        MachineState.Connection.CONNECTED -> stringResource(
                            R.string.home_connected,
                            when {
                                alert?.error == true -> stringResource(R.string.home_state_error)
                                alert != null -> stringResource(R.string.home_state_waiting)
                                else -> MachineTexts.status(machine.status).asString()
                            },
                        )
                    },
                    hint = ConnectionTexts.homeHint(machine.connection, bluetoothOn, machine.problem)?.asString(),
                    connected = machine.connected, alert = alert, update = update, onUpdate = install,
                    lastTea = lastTea, onRepeat = { if (lastTea != null) confirmRepeat = true }, onManual = onManual,
                )
                SectionTitle(stringResource(R.string.home_favorites, favorites.size))
            }
            if (favorites.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Infusion.colors.card).clickable(onClick = onManageFavorites).padding(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.home_no_favorites), color = Infusion.colors.muted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // Les deux rangées se partagent la hauteur donnée (jamais moins de 150 dp chacune).
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val rowHeight = ((maxHeight - 12.dp) / 2).coerceAtLeast(MIN_ROW)
                    FavoritesTwoRows(favorites, rowHeight, onOpen = { onOpenTea(it.id) })
                }
            }
        }
    }
    // « Refaire » demande confirmation, avec le nom du thé et ses réglages.
    val repeatTea = lastTea
    if (confirmRepeat && repeatTea != null) {
        val c = Infusion.colors
        AlertDialog(
            onDismissRequest = { confirmRepeat = false },
            title = { Text(stringResource(R.string.home_repeat_confirm, repeatTea.name)) },
            text = { Text(specLine(repeatTea.temperature, repeatTea.waterMl, repeatTea.steepSeconds)) },
            confirmButton = { TextButton(onClick = { confirmRepeat = false; onRepeat(repeatTea) }) { Text(stringResource(R.string.action_validate), color = c.accentInk) } },
            dismissButton = { TextButton(onClick = { confirmRepeat = false }) { Text(stringResource(R.string.action_cancel), color = c.ink) } },
            containerColor = c.card, titleContentColor = c.ink, textContentColor = c.muted,
        )
    }
}

private val MIN_ROW = 150.dp

/**
 * Deux enfants l'un sous l'autre : le premier à sa hauteur, le second dans ce qui reste de `viewport`, sans descendre
 * sous `minRest` (la page, défilante, s'allonge alors) ; à sa propre hauteur si `stretch` est faux.
 */
@Composable
private fun TopThenRest(viewport: Dp, gap: Dp, minRest: Dp, stretch: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val free = Constraints(minWidth = width, maxWidth = width)
        val top = measurables[0].measure(free)
        val gapPx = gap.roundToPx()
        val rest = measurables[1].measure(
            if (stretch) Constraints.fixed(width, maxOf(viewport.roundToPx() - top.height - gapPx, minRest.roundToPx())) else free,
        )
        layout(width, top.height + gapPx + rest.height) {
            top.placeRelative(0, 0)
            rest.placeRelative(0, top.height + gapPx)
        }
    }
}

/**
 * Bloc 1 : la machine, le bouton de mise à jour sous son nom, puis « Refaire » et « À la main » — ou, à
 * leur place, ce que la machine demande (eau, couvercle…), qui clignote jusqu'à ce que ce soit réglé.
 */
@Composable
private fun MachineBlock(
    machineName: String, stateLine: String,
    /** Ce qu'il faut faire quand la cause d'une absence de liaison est connue (« Active le Bluetooth du téléphone. »). */
    hint: String?,
    connected: Boolean, alert: MachineAlert?, update: UpdateStatus, onUpdate: () -> Unit,
    lastTea: Tea?, onRepeat: () -> Unit, onManual: () -> Unit,
) {
    val c = Infusion.colors
    val display = Infusion.navDisplay
    val alertColor = if (alert?.error == true) AlertError else AlertWarn
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(c.deep)
            .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.30f), Color.Transparent), radius = 520f, center = androidx.compose.ui.geometry.Offset(900f, 0f)))
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_my_machine), style = MaterialTheme.typography.labelSmall, color = c.onDeepMuted)
                Spacer(Modifier.height(4.dp))
                Text(machineName, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 32.sp), color = c.onDeep, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (alert != null) BlinkingDot(alertColor)
                    else Box(Modifier.size(9.dp).clip(CircleShape).background(if (connected) c.accent else c.heat))
                    Spacer(Modifier.width(8.dp))
                    Text(stateLine, style = MaterialTheme.typography.bodySmall, color = c.onDeepMuted)
                }
                if (hint != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = c.onDeep)
                }
                UpdatePill(update, onUpdate)
            }
            Box(Modifier.size(width = 104.dp, height = 124.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0x57F3F6F1), Color(0x298CA674), Color.Transparent))))
                Image(painterResource(R.drawable.machine), contentDescription = stringResource(R.string.machine_image), modifier = Modifier.fillMaxSize())
            }
        }
        if (alert != null) {
            MachineAlertPanel(alert)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Le nom du thé n'est plus sous la flèche : il est dans la confirmation (« Refaire « Sencha » ? »).
                BigButton(
                    icon = R.drawable.ic_play, label = stringResource(R.string.home_repeat), sub = null,
                    filled = true, enabled = connected && lastTea != null, onClick = onRepeat, display = display, modifier = Modifier.weight(1f),
                )
                BigButton(
                    icon = R.drawable.ic_sliders, label = stringResource(R.string.home_manual), sub = stringResource(R.string.home_manual_sub),
                    filled = false, enabled = true, onClick = onManual, display = display, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Point d'état qui clignote pendant une demande de la machine. L'animation n'existe que là (sans demande, l'accueil
 * ne demande plus une image à chaque rafraîchissement de l'écran), et se lit au dessin, sans recomposer le bloc.
 */
@Composable
private fun BlinkingDot(color: Color) {
    val blink = rememberInfiniteTransition(label = "alerte")
    val pulse = blink.animateFloat(0f, 1f, infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulsation")
    Box(Modifier.size(9.dp).drawBehind { drawCircle(color.copy(alpha = if (pulse.value > 0.5f) 0.2f else 1f)) })
}

/** Bouton « Mise à jour 0.1.7 » sous le nom de la machine : téléchargement, vérification, puis Android installe. */
@Composable
private fun UpdatePill(u: UpdateStatus, onClick: () -> Unit) {
    val v = u.available ?: return
    val c = Infusion.colors
    val busy = u.busy
    val failed = u.failure != null && !busy
    val shape = RoundedCornerShape(99.dp)
    // 48 dp de haut réservés au doigt (la pastille en fait 28, centrée : 10 dp au-dessus, comme l'ancien espace).
    Box(
        Modifier.minimumInteractiveComponentSize().clip(shape)
            .then(if (failed) Modifier.border(1.5.dp, c.onDeep.copy(alpha = 0.4f), shape) else Modifier.background(c.accent))
            .clickable(enabled = !busy, onClickLabel = stringResource(R.string.update_install_label, v), onClick = onClick),
    ) {
        if (busy) {
            val done = if (u.verifying || u.waitingBrew || u.installing) 1f else (u.progress ?: 0) / 100f
            Box(Modifier.matchParentSize()) { Box(Modifier.fillMaxHeight().fillMaxWidth(done).background(c.onAccent.copy(alpha = 0.24f))) }
        }
        Row(Modifier.padding(start = 9.dp, end = 13.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(if (failed) R.drawable.ic_warn else R.drawable.ic_download), contentDescription = null,
                tint = if (failed) AlertWarn else c.onAccent, modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    u.progress != null -> stringResource(R.string.update_downloading, u.progress)
                    u.verifying -> stringResource(R.string.update_verifying)
                    u.waitingBrew -> stringResource(R.string.update_after_brew)
                    u.installing -> stringResource(R.string.update_installing)
                    failed -> stringResource(R.string.update_retry, u.failure?.asString().orEmpty())
                    else -> stringResource(R.string.update_pill, v)
                },
                style = MaterialTheme.typography.labelMedium, color = if (failed) c.onDeep else c.onAccent,
            )
        }
    }
}

@Composable
private fun BigButton(
    /** `sub` : petite ligne sous le libellé (affichages avec texte) ; null : pas de ligne. */
    icon: Int, label: String, sub: String?, filled: Boolean, enabled: Boolean, onClick: () -> Unit, display: NavDisplay, modifier: Modifier,
) {
    val c = Infusion.colors
    val shape = RoundedCornerShape(20.dp)
    val fg = if (filled) c.onAccent else c.onDeep
    Column(
        modifier.heightIn(min = 60.dp).clip(shape)
            .then(if (filled) Modifier.background(if (enabled) c.accent else c.accent.copy(alpha = 0.45f)) else Modifier.border(1.5.dp, c.onDeep.copy(alpha = 0.28f), shape))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (display != NavDisplay.TEXT) {
            Icon(painterResource(icon), contentDescription = label, tint = fg, modifier = Modifier.size(if (display == NavDisplay.ICONS) 26.dp else 20.dp))
        }
        if (display != NavDisplay.ICONS) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = fg)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bloc 2 : les favoris sur deux lignes, qui défilent de gauche à droite (on voit le bord de la colonne suivante). */
@Composable
private fun FavoritesTwoRows(favorites: List<Tea>, rowHeight: Dp, onOpen: (Tea) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth: Dp = (maxWidth - 12.dp) / 2.2f
        // Colonnes composées à la demande : seules les photos visibles (et voisines) sont décodées.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(favorites.chunked(2), key = { pair -> pair.first().id }) { pair ->
                Column(Modifier.width(cardWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { tea -> TeaCard(tea, onClick = { onOpen(tea) }, modifier = Modifier.height(rowHeight), fillHeight = true) }
                }
            }
        }
    }
}
