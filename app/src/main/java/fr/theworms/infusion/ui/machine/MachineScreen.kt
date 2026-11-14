package fr.theworms.infusion.ui.machine

import kotlinx.coroutines.async
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import fr.theworms.infusion.ble.MachineOp
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import fr.theworms.infusion.text.text
import fr.theworms.infusion.text.asString
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.text.AppLanguage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.theworms.infusion.BuildConfig
import fr.theworms.infusion.ble.MachineLanguage
import androidx.compose.ui.text.style.TextAlign
import fr.theworms.infusion.ble.MachineState.Connection
import fr.theworms.infusion.ble.MachineTexts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.theworms.infusion.AppContainer
import fr.theworms.infusion.R
import fr.theworms.infusion.data.AppSettings
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.domain.MachineLimits
import fr.theworms.infusion.ui.components.SectionTitle
import fr.theworms.infusion.ui.components.Segmented
import fr.theworms.infusion.ui.components.SyncBadge
import fr.theworms.infusion.ui.components.TeaPhoto
import fr.theworms.infusion.ui.components.infusionCard
import fr.theworms.infusion.ui.components.specLine
import fr.theworms.infusion.ui.update.rememberUpdateInstaller
import fr.theworms.infusion.ui.theme.Accent
import fr.theworms.infusion.ui.theme.Infusion
import fr.theworms.infusion.ui.theme.NavDisplay
import fr.theworms.infusion.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/**
 * Carte où s'affichent « Envoi à la machine… » et le résultat d'une action : celle du bouton touché, pas le haut de
 * la page, hors de vue quand on a fait défiler jusqu'aux préréglages ou à l'heure.
 */
private enum class Spot { STATUS, NAME, PRESETS, CLOCK }

private data class Info(val spot: Spot, val text: UiText)

@Composable
fun MachineScreen(container: AppContainer, settings: AppSettings, onPair: () -> Unit, onRequestPermissions: () -> Unit) {
    val c = Infusion.colors
    val scope = rememberCoroutineScope()
    val machine = container.machine
    val dao = container.database.teas()
    val state by machine.state.collectAsStateWithLifecycle()
    val all by remember(dao) { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Même ordre que la synchronisation (TeaDao.onMachine) : positions, puis les thés sans position, par nom (tri stable
    // sur la liste déjà rangée par nom).
    val onMachine = all.filter { it.onMachine }.sortedBy { it.machineSlot ?: Int.MAX_VALUE }
    var info by remember { mutableStateOf<Info?>(null) }
    /** Action courte en cours (connexion, nom, heure), et où elle a été lancée. */
    var busySpot by remember { mutableStateOf<Spot?>(null) }
    // Synchronisation, lecture des préréglages, langue ou unités : suivies par la machine, pas par l'écran. Après un
    // changement d'onglet ou une rotation, les boutons restent inactifs tant que l'envoi lancé avant tourne encore.
    val operation by machine.operation.collectAsStateWithLifecycle()
    val presetsBusy = busySpot == Spot.PRESETS || operation == MachineOp.SYNC || operation == MachineOp.READ
    val busy = busySpot != null || operation != null
    var picking by remember { mutableStateOf(false) }
    var confirmSync by remember { mutableStateOf(false) }
    var unknownReply by remember { mutableStateOf<String?>(null) }
    val setupInfo by machine.setupMessage.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Tea?>(null) }
    var setupLocal by remember { mutableStateOf(false) }
    val setupBusy = setupLocal || operation == MachineOp.SETUP
    // Envois à la machine dans le scope de l'app : changer d'onglet ou tourner le téléphone ne les coupe pas
    // en route (une synchronisation interrompue laissait des préréglages mélangés). Le résultat s'affiche si
    // l'écran est encore là.
    val changeSetup: (suspend () -> UiText) -> Unit = { block ->
        setupLocal = true
        scope.launch { container.appScope.async { block() }.await(); setupLocal = false }
    }
    val run: (Spot, suspend () -> UiText?) -> Unit = { spot, block ->
        busySpot = spot
        scope.launch { info = container.appScope.async { block() }.await()?.let { Info(spot, it) }; busySpot = null }
    }
    val context = LocalContext.current
    val appLanguage = AppLanguage.current(context)
    val clipLabel = stringResource(R.string.clip_machine_reply)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = when (state.connection) { Connection.CONNECTED -> c.sync; Connection.CONNECTING -> c.accent; else -> c.heat }
                Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (state.connection) {
                            Connection.NONE -> stringResource(R.string.home_no_machine)
                            Connection.CONNECTED -> stringResource(R.string.machine_card_status, state.name, MachineTexts.status(state.status).asString())
                            Connection.CONNECTING -> stringResource(R.string.machine_card_connecting, state.name)
                            Connection.OFFLINE -> stringResource(R.string.machine_card_offline, state.name)
                        },
                        style = MaterialTheme.typography.titleMedium, color = c.ink,
                    )
                    Text(
                        if (state.connection == Connection.NONE) stringResource(R.string.machine_pair_hint)
                        else stringResource(R.string.machine_firmware, state.firmware ?: stringResource(R.string.machine_firmware_unknown)),
                        style = MaterialTheme.typography.bodySmall, color = c.muted,
                    )
                }
            }
            state.problem?.let {
                Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = c.heatInk, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    !machine.hasPermissions() -> SmallAction(stringResource(R.string.machine_allow_bluetooth), filled = true, onClick = onRequestPermissions)
                    state.connection == Connection.NONE -> SmallAction(stringResource(R.string.machine_pair), filled = true, onClick = onPair)
                    state.connection == Connection.OFFLINE -> {
                        SmallAction(stringResource(R.string.machine_connect), filled = true, enabled = busySpot != Spot.STATUS) {
                            run(Spot.STATUS) { if (machine.ensureConnected(userInitiated = true)) text(R.string.connected_ok) else null }
                        }
                        SmallAction(stringResource(R.string.machine_change), filled = false, onClick = onPair)
                    }
                    else -> SmallAction(stringResource(R.string.machine_change), filled = false, onClick = onPair)
                }
            }
            Feedback(busySpot == Spot.STATUS, info.at(Spot.STATUS))
        }

        if (state.connection != Connection.NONE) {
            Card {
                NameField(
                    saved = state.name,
                    enabled = true,
                ) { n -> run(Spot.NAME) { machine.rename(n) } }
                if (state.firmware != null && !state.supportsNickname) {
                    Text(stringResource(R.string.machine_old_firmware_name), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Feedback(busySpot == Spot.NAME, info.at(Spot.NAME))
            }
        }

        SectionTitle(stringResource(R.string.machine_section_presets), trailing = { Text(stringResource(R.string.count_of, onMachine.size, MachineLimits.PRESETS), style = MaterialTheme.typography.bodySmall, color = c.muted) })
        Card {
            if (onMachine.isEmpty()) {
                Text(stringResource(R.string.machine_no_presets), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            } else {
                onMachine.forEach { tea ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SyncBadge(tea.synced)
                        TeaPhoto(tea, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)), maxPx = 160)
                        Column(Modifier.weight(1f)) {
                            Text(tea.name, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1)
                            Text(stringResource(if (tea.synced) R.string.machine_preset_received else R.string.machine_preset_pending, tea.specLine()), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1)
                        }
                        Box(
                            Modifier.size(48.dp).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(c.background)
                                .clickable(onClickLabel = stringResource(R.string.machine_delete_label, tea.name)) { removing = tea },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_delete), tint = c.muted, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallAction(stringResource(R.string.machine_add_tea), filled = false, enabled = onMachine.size < MachineLimits.PRESETS && all.any { !it.onMachine }) { picking = true }
                SmallAction(stringResource(R.string.machine_sync), filled = true, enabled = !busy && state.connected) { confirmSync = true }
            }
            Text(
                stringResource(R.string.machine_sync_hint),
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            HorizontalDivider(color = c.line)
            Text(stringResource(R.string.machine_read_title), style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(
                stringResource(R.string.machine_read_hint),
                style = MaterialTheme.typography.bodySmall, color = c.muted,
            )
            SmallAction(stringResource(R.string.machine_read_action), filled = false, enabled = !busy && state.connected) {
                busySpot = Spot.PRESETS
                scope.launch {
                    val r = container.appScope.async { machine.readMachinePresets() }.await()
                    info = Info(Spot.PRESETS, r.message); unknownReply = r.unknownReply
                    busySpot = null
                }
            }
            Feedback(presetsBusy, info.at(Spot.PRESETS))
            unknownReply?.let { raw ->
                val clipboard = androidx.compose.ui.platform.LocalClipboard.current
                Text(raw, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = c.ink)
                SmallAction(stringResource(R.string.machine_copy_reply), filled = false) {
                    scope.launch { clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText(clipLabel, raw))) }
                }
            }
        }

        SectionTitle(stringResource(R.string.machine_section_settings))
        Card {
            // Langue : celle de l'app (gardée par Android) et de l'écran de la machine, connectée ou non.
            // Unités : lues sur la machine à la connexion, changées seulement connectée (puis relues).
            val canChange = state.connected && !setupBusy && operation == null
            Label(stringResource(R.string.machine_language))
            Segmented(MachineLanguage.entries.map { it to it.label }, appLanguage, { l ->
                if (l != appLanguage) {
                    setupLocal = true
                    val appContext = context.applicationContext
                    // Hors de l'écran : changer la langue de l'app le recrée.
                    container.appScope.launch {
                        if (machine.state.value.connected) machine.changeSetup(language = l) else machine.clearSetupMessage()
                        withContext(Dispatchers.Main) { AppLanguage.set(appContext, l) }
                    }
                }
            }, enabled = !setupBusy && !busy)
            val machineLanguage = state.setup?.languageCode
            if (state.connected && machineLanguage != null && machineLanguage != appLanguage.code) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        MachineLanguage.of(machineLanguage)?.let { stringResource(R.string.machine_language_differs, stringResource(it.nameRes)) }
                            ?: stringResource(R.string.machine_language_differs_other),
                        style = MaterialTheme.typography.bodySmall, color = c.heatInk, modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    SmallAction(stringResource(R.string.machine_language_apply, stringResource(appLanguage.nameRes)), filled = false, enabled = canChange && !busy) {
                        changeSetup { machine.changeSetup(language = appLanguage) }
                    }
                }
            }
            Label(stringResource(R.string.machine_units))
            Segmented(listOf(true to "°C · ml", false to "°F · oz"), settings.metric, { metric ->
                changeSetup { machine.changeSetup(metric = metric) }
            }, enabled = canChange)
            Text(
                when {
                    setupBusy -> stringResource(R.string.machine_sending)
                    setupInfo != null -> setupInfo!!.asString()
                    state.connected -> stringResource(R.string.machine_settings_hint)
                    else -> stringResource(R.string.machine_settings_offline)
                },
                style = MaterialTheme.typography.bodySmall, color = if (setupInfo != null && !setupBusy) c.ink else c.muted,
            )
            SettingRow(R.drawable.ic_clock, stringResource(R.string.machine_clock), stringResource(R.string.machine_clock_detail)) {
                SmallAction(stringResource(R.string.machine_clock_set), filled = false, enabled = state.connected && !busy) {
                    run(Spot.CLOCK) { text(if (machine.syncClock()) R.string.machine_clock_done else R.string.machine_unreachable) }
                }
            }
            Feedback(busySpot == Spot.CLOCK, info.at(Spot.CLOCK))
        }

        SectionTitle(stringResource(R.string.machine_section_care))
        Card {
            SettingRow(R.drawable.ic_download, stringResource(R.string.machine_firmware_title), stringResource(R.string.machine_firmware_detail, state.firmware ?: stringResource(R.string.machine_version_unknown)))
        }

        if (confirmSync) {
            val n = onMachine.size
            AlertDialog(
                onDismissRequest = { confirmSync = false },
                title = { Text(stringResource(R.string.sync_confirm_title)) },
                text = {
                    Text(
                        if (n == 0) stringResource(R.string.sync_confirm_none)
                        else pluralStringResource(R.plurals.sync_confirm, n, n)
                    )
                },
                confirmButton = { TextButton(onClick = { confirmSync = false; run(Spot.PRESETS) { machine.syncPresets() } }) { Text(stringResource(R.string.action_replace), color = c.danger) } },
                dismissButton = { TextButton(onClick = { confirmSync = false }) { Text(stringResource(R.string.action_cancel), color = c.ink) } },
                containerColor = c.card, titleContentColor = c.ink, textContentColor = c.muted,
            )
        }

        // Croix d'un thé « sur la machine » : il est supprimé partout (thés, favoris, cette liste), après confirmation.
        removing?.let { tea ->
            AlertDialog(
                onDismissRequest = { removing = null },
                title = { Text(stringResource(R.string.delete_title, tea.name)) },
                text = { Text(stringResource(R.string.machine_delete_detail)) },
                confirmButton = {
                    TextButton(onClick = {
                        removing = null
                        container.appScope.launch { dao.delete(tea); container.photos.delete(tea.photoPath) }
                        info = Info(Spot.PRESETS, text(R.string.machine_deleted, tea.name))
                    }) { Text(stringResource(R.string.action_delete), color = c.danger) }
                },
                dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_keep), color = c.ink) } },
                containerColor = c.card, titleContentColor = c.ink, textContentColor = c.muted,
            )
        }

        if (picking) {
            AlertDialog(
                onDismissRequest = { picking = false },
                title = { Text(stringResource(R.string.machine_add_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        all.filter { !it.onMachine }.forEach { tea ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                    picking = false
                                    container.appScope.launch { dao.update(tea.copy(onMachine = true, synced = false)) }
                                }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TeaPhoto(tea, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)), maxPx = 140)
                                Spacer(Modifier.width(10.dp))
                                Column { Text(tea.name, color = c.ink, style = MaterialTheme.typography.titleMedium); Text(tea.specLine(), color = c.muted, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_close), color = c.ink) } },
                containerColor = c.card, titleContentColor = c.ink,
            )
        }

        SectionTitle(stringResource(R.string.options_title))
        Card {
            Label(stringResource(R.string.options_theme))
            Segmented(ThemeMode.entries.map { it to stringResource(it.label) }, settings.theme, { container.appScope.launch { container.settings.setTheme(it) } })
            Label(stringResource(R.string.options_color, stringResource(settings.accent.label)))
            // Couleurs lues comme un groupe de boutons radio : chacune par son nom, la choisie « sélectionnée ».
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Accent.entries.forEach { a ->
                    val on = a == settings.accent
                    val accentName = stringResource(a.label)
                    Box(
                        Modifier.size(40.dp)
                            .then(if (on) Modifier.border(2.dp, c.ink, CircleShape).padding(4.dp) else Modifier)
                            .clip(CircleShape).background(a.color)
                            .selectable(selected = on, role = Role.RadioButton) { container.appScope.launch { container.settings.setAccent(a) } }
                            .semantics { contentDescription = accentName },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Label(stringResource(R.string.options_alerts))
            ToggleRow(stringResource(R.string.options_beep), stringResource(R.string.options_beep_detail), settings.alertSound) { on -> container.appScope.launch { container.settings.setAlertSound(on) } }
            ToggleRow(stringResource(R.string.options_vibration), stringResource(R.string.options_vibration_detail), settings.alertVibration) { on -> container.appScope.launch { container.settings.setAlertVibration(on) } }
            Label(stringResource(R.string.options_display))
            Segmented(NavDisplay.entries.map { it to stringResource(it.label) }, settings.navDisplay, { container.appScope.launch { container.settings.setNavDisplay(it) } })
            HorizontalDivider(color = c.line)
            Label(stringResource(R.string.options_base))
            val status by container.catalog.status.collectAsStateWithLifecycle()
            // Langue de l'app, suivie (pas Locale.getDefault(), que l'écran ne voit pas changer).
            val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        status.busy -> stringResource(R.string.base_checking)
                        status.error != null -> status.error!!.asString()
                        status.count > 0 -> stringResource(R.string.base_ok, status.count, status.version, formatCheck(status.checkedAt, stringResource(R.string.date_pattern), locale))
                        else -> stringResource(R.string.base_never)
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (status.error != null) c.heatInk else c.muted, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                SmallAction(stringResource(R.string.action_check), filled = false, enabled = !status.busy) { container.appScope.launch { container.catalog.refresh() } }
            }
            HorizontalDivider(color = c.line)
            UpdateRow(container)
        }
        // Pied de page : la version de l'app.
        Text(
            stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall, color = c.muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}


@Composable
private fun NameField(saved: String, enabled: Boolean, onRename: (String) -> Unit) {
    val c = Infusion.colors
    var text by remember(saved) { mutableStateOf(saved) }
    val label = stringResource(R.string.machine_name_label)
    Label(label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.background).padding(horizontal = 14.dp, vertical = 12.dp)) {
            BasicTextField(
                value = text, onValueChange = { text = it.take(MachineLimits.NAME_MAX) }, singleLine = true, enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) c.ink else c.muted), cursorBrush = SolidColor(c.accentInk),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onRename(text) }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        SmallAction(stringResource(R.string.action_rename), filled = true, enabled = enabled && text.isNotBlank() && text != saved) { onRename(text) }
    }
    Text(
        stringResource(R.string.machine_name_hint, text.length, MachineLimits.NAME_MAX),
        style = MaterialTheme.typography.bodySmall, color = if (text.length >= MachineLimits.NAME_MAX) c.heatInk else c.muted,
    )
}

@Composable
private fun SmallAction(label: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Infusion.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.clip(shape)
            .then(if (filled) Modifier.background(if (enabled) c.accent else c.accent.copy(alpha = 0.4f)) else Modifier.border(1.5.dp, c.line, shape))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (filled) c.onAccent else if (enabled) c.ink else c.muted) }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().infusionCard().background(Infusion.colors.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Infusion.colors.ink)
}

/** Toute la ligne bascule l'interrupteur, et le lecteur d'écran la lit d'un seul arrêt (« Bip, activé »). */
@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Infusion.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, uncheckedTrackColor = c.background, uncheckedBorderColor = c.line),
        )
    }
}

@Composable
private fun SettingRow(icon: Int, title: String, detail: String, trailing: @Composable (() -> Unit)? = null) {
    val c = Infusion.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = c.accentInk, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        trailing?.invoke()
    }
}


/** Date de la dernière vérification de la base, au motif de la langue (« 04/11 à 13:31 », « 4 Nov at 13:31 »). */
internal fun formatCheck(millis: Long, pattern: String, locale: java.util.Locale): String =
    if (millis <= 0) "—" else java.text.SimpleDateFormat(pattern, locale).format(java.util.Date(millis))

/** « Envoi à la machine… » pendant l'action, puis son résultat, dit au lecteur d'écran quand il paraît. */
@Composable
private fun Feedback(sending: Boolean, message: UiText?) {
    val c = Infusion.colors
    if (sending) Text(stringResource(R.string.machine_sending), style = MaterialTheme.typography.bodyMedium, color = c.muted)
    else message?.let {
        Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

private fun Info?.at(spot: Spot): UiText? = this?.takeIf { it.spot == spot }?.text

/** Mises à jour depuis les Releases GitHub : vérifier, télécharger, confier à l'installateur d'Android. */
@Composable
private fun UpdateRow(container: AppContainer) {
    val c = Infusion.colors
    val install = rememberUpdateInstaller(container.updater, container.appScope)
    val u by container.updater.status.collectAsStateWithLifecycle()
    Label(stringResource(R.string.updates_title))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                u.progress != null -> stringResource(R.string.update_row_downloading, u.available.orEmpty(), u.progress!!)
                u.verifying -> stringResource(R.string.update_row_verifying, u.available.orEmpty())
                u.waitingBrew -> stringResource(R.string.update_row_after_brew, u.available.orEmpty())
                u.installing -> stringResource(R.string.update_row_installing, u.available.orEmpty())
                u.checking -> stringResource(R.string.update_row_searching)
                u.error != null -> u.error!!.asString()
                u.available != null -> stringResource(R.string.update_row_available, u.available!!, u.current)
                u.checkedAt > 0 -> stringResource(R.string.update_row_up_to_date, u.current)
                else -> stringResource(R.string.app_version, u.current)
            },
            style = MaterialTheme.typography.bodySmall, color = if (u.error != null) c.heatInk else c.muted, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        if (u.available != null) {
            SmallAction(stringResource(R.string.action_install), filled = true, enabled = !u.busy, onClick = install)
        } else {
            SmallAction(stringResource(R.string.action_check), filled = false, enabled = !u.checking) { container.appScope.launch { container.updater.check(force = true) } }
        }
    }
}
