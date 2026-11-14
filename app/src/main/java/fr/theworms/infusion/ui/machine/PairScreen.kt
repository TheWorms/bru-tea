package fr.theworms.infusion.ui.machine

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.async
import fr.theworms.infusion.text.asString
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import fr.theworms.infusion.AppContainer
import fr.theworms.infusion.R
import fr.theworms.infusion.ble.FoundMachine
import fr.theworms.infusion.ui.components.infusionCard
import fr.theworms.infusion.ui.theme.Infusion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Recherche et association de la machine. La première fois, Android demande de confirmer l'appairage. */
@Composable
fun PairScreen(container: AppContainer, permissionTick: Int, onRequestPermissions: () -> Unit, onDone: () -> Unit) {
    val c = Infusion.colors
    val machine = container.machine
    val state by machine.state.collectAsStateWithLifecycle()
    val found = remember { mutableStateListOf<FoundMachine>() }
    var scanning by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var pairing by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val allowed = remember(permissionTick) { machine.hasPermissions() }
    // Bluetooth suivi en direct : allumé depuis les réglages rapides, la recherche démarre aussitôt.
    val bluetoothOn by machine.bluetoothOn.collectAsStateWithLifecycle()

    LaunchedEffect(round, allowed, bluetoothOn) {
        if (!allowed || !bluetoothOn) return@LaunchedEffect
        scanning = true
        withTimeoutOrNull(12_000) {
            machine.scan().collect { m -> if (found.none { it.address == m.address }) found += m }
        }
        scanning = false
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = stringResource(R.string.action_back), onClick = onDone), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back), tint = c.ink)
            }
            Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accentSoft).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.pair_step), style = MaterialTheme.typography.titleMedium, color = c.ink)
                Text(stringResource(R.string.pair_detail), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            }
            when {
                !allowed -> Action(stringResource(R.string.pair_allow), onRequestPermissions)
                !bluetoothOn -> Text(stringResource(R.string.pair_bluetooth_off), color = c.heatInk, style = MaterialTheme.typography.bodyMedium)
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (scanning) stringResource(R.string.pair_searching) else pluralStringResource(R.plurals.pair_found, found.size, found.size), style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.pair_search_again), style = MaterialTheme.typography.labelLarge, color = c.accentInk, modifier = Modifier.clickable { found.clear(); round++ }.padding(6.dp))
                    }
                    found.sortedByDescending { it.rssi }.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().infusionCard(RoundedCornerShape(16.dp)).background(c.card)
                                .clickable(enabled = pairing == null) {
                                    pairing = m.address
                                    // Dans le scope de l'app : quitter l'écran pendant l'association ne laisse pas
                                    // une liaison à moitié ouverte (connectTo se termine proprement).
                                    scope.launch {
                                        val ok = container.appScope.async { machine.pair(m) }.await()
                                        pairing = null
                                        if (ok) onDone()
                                    }
                                }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null, tint = c.accentInk, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium, color = c.ink)
                                Text(stringResource(if (m.bonded) R.string.pair_bonded else R.string.pair_new, stringResource(signal(m.rssi))), style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            if (pairing == m.address) CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp)
                            else Text(stringResource(R.string.pair_action), style = MaterialTheme.typography.labelLarge, color = c.accentInk)
                        }
                    }
                    if (!scanning && found.isEmpty()) {
                        Text(stringResource(R.string.pair_none), style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                }
            }
            state.problem?.let { Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = c.heatInk) }
        }
    }
}

private fun signal(rssi: Int) = when { rssi > -60 -> R.string.signal_strong; rssi > -75 -> R.string.signal_medium; else -> R.string.signal_weak }

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    val c = Infusion.colors
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.deep).clickable(onClick = onClick).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.onDeep)
    }
}
