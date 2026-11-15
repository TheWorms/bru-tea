package fr.theworms.infusion.ui.teas

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import fr.theworms.infusion.text.text
import fr.theworms.infusion.text.displayedLanguage
import fr.theworms.infusion.text.asString
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.ble.MachineLanguage
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import fr.theworms.infusion.catalog.CatalogEntry
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import fr.theworms.infusion.ui.components.specLine
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.theworms.infusion.AppContainer
import fr.theworms.infusion.R
import fr.theworms.infusion.catalog.Catalog
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.domain.MachineLimits
import fr.theworms.infusion.domain.TeaType
import fr.theworms.infusion.ui.components.Chip
import fr.theworms.infusion.ui.components.TeaPhoto
import fr.theworms.infusion.ui.components.infusionCard
import fr.theworms.infusion.ui.theme.Infusion
import kotlinx.coroutines.launch

/** Réglages de départ d'un nouveau thé (depuis le mode manuel). */
data class TeaDraft(val temperature: Int = 85, val waterMl: Int = 250, val steepSeconds: Int = 120)

private sealed interface Lookup {
    data object None : Lookup
    data object Searching : Lookup
    data class Found(val name: String, val origin: String, val adjusted: Boolean, val unsuitable: UiText? = null) : Lookup
    data class NotFound(val name: String) : Lookup
    data class Problem(val message: UiText) : Lookup
}

/** Où une erreur de la fiche s'affiche : près de ce qui l'a causée, là où l'on regarde (le formulaire dépasse l'écran). */
internal enum class ErrorSpot { NAME, PHOTO, MACHINE, SAVE }

/**
 * Ajouter ou modifier un thé. Pour un nouveau thé, on écrit le nom puis « Récupérer » cherche dans la
 * base GitHub ; s'il n'y est pas, « Saisir à la main » ouvre les réglages.
 */
@Composable
fun TeaEditScreen(
    container: AppContainer,
    teaId: Long,
    /** Réglages venus du mode manuel (« Enregistrer dans mes thés ») ; null : nouveau thé à chercher, ou thé existant. */
    draft: TeaDraft?,
    onDone: () -> Unit,
    onDeleted: () -> Unit,
) {
    val c = Infusion.colors
    val dao = container.database.teas()
    val scope = rememberCoroutineScope()
    val isNew = teaId == 0L
    // Photos, chargements et enregistrement en cours : tenus hors de l'écran, ils vont au bout d'une rotation.
    val work = viewModel { TeaEditWork(createSavedStateHandle()) }

    // Fiche enregistrée ou supprimée : c'est l'écran affiché qui se ferme, une fois au premier plan (après une rotation
    // pendant l'enregistrement, l'écran d'avant est détruit et ne peut plus revenir en arrière).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val finished = work.finished
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(finished) {
        if (finished == null) return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        if (finished == TeaEditWork.Finish.SAVED) currentOnDone() else currentOnDeleted()
    }

    // La saisie survit à une rotation, au passage clair/sombre ou à un changement de langue (rememberSaveable).
    var ready by remember { mutableStateOf(isNew) }
    var initialized by rememberSaveable { mutableStateOf(isNew) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(TeaType.GREEN) }
    var origin by rememberSaveable { mutableStateOf("") }
    val start = draft ?: TeaDraft()
    var temperature by rememberSaveable { mutableIntStateOf(MachineLimits.roundTemperature(start.temperature)) }
    var water by rememberSaveable { mutableIntStateOf(MachineLimits.roundWater(start.waterMl)) }
    var steep by rememberSaveable { mutableIntStateOf(MachineLimits.roundSteep(start.steepSeconds)) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var onMachine by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var catalogId by rememberSaveable { mutableStateOf<String?>(null) }
    var fieldsShown by rememberSaveable { mutableStateOf(fieldsShownAtStart(isNew, draft)) }
    var lookup by remember { mutableStateOf<Lookup>(Lookup.None) }
    val photoLoads by work.photoLoads.collectAsStateWithLifecycle()
    val nameFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var machineCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(teaId) {
        machineCount = dao.countOnMachine()
        if (!isNew) {
            dao.get(teaId)?.let { t ->
                // Thé déjà enregistré par cette fiche (rotation pendant l'enregistrement) : la ligne relue vaut celle-ci.
                work.loaded = t
                // Lu dans la base une seule fois : après une recréation, la saisie en cours est gardée.
                if (!initialized) {
                    name = t.name; type = t.type; origin = t.origin; temperature = t.temperature; water = t.waterMl
                    steep = t.steepSeconds; favorite = t.favorite; onMachine = t.onMachine; notes = t.notes; catalogId = t.catalogId
                    work.showStoredPhoto(t.photoPath)
                }
            }
            initialized = true
            ready = true
        }
    }
    if (!ready) return

    val leave: () -> Unit = leave@{
        // Pendant l'enregistrement, on ne part pas : les photos en cours d'enregistrement seraient supprimées.
        if (work.saving) return@leave
        val unsaved = work.takeNewPhotos()
        container.appScope.launch { unsaved.forEach { container.photos.delete(it) } }
        onDone()
    }
    BackHandler { leave() }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) work.loadPhoto {
            val path = container.photos.importFromGallery(uri)
            // Le choix dans la galerie l'emporte sur une photo de la base encore en chemin (voir applyEntry).
            if (path != null) work.setPhoto(path, fromCatalog = false, credit = "")
            else work.error = ErrorSpot.PHOTO to text(R.string.edit_photo_unreadable)
        }
    }

    val catalogStatus by container.catalog.status.collectAsStateWithLifecycle()
    // Notes reprises de la base dans la langue affichée ; dernières notes remplies remplaçables si on choisit une autre fiche.
    val language = displayedLanguage()
    var autoNotes by rememberSaveable { mutableStateOf<String?>(null) }
    // Fiche choisie (suggestion ou « Récupérer ») : réglages, notes et photo de la base, modifiables ensuite.
    val applyEntry: (CatalogEntry) -> Unit = { found ->
        type = found.type; origin = found.origin; temperature = found.temperature; water = found.waterMl; steep = found.steepSeconds
        // Notes automatiques (thé repris de la machine, fiche reprise avant) remplacées ; notes écrites à la main gardées.
        val previous = catalogId?.let { container.catalog.entry(it) }
        val replaceable = setOfNotNull(autoNotes) + (previous?.let { e -> MachineLanguage.entries.map { Catalog.notesFor(e, it) } } ?: emptyList())
        notes = Catalog.mergeNotes(notes, found, replaceable, language)
        autoNotes = Catalog.notesFor(found, language)
        catalogId = found.id
        fieldsShown = true
        lookup = Lookup.Found(found.name, found.origin, found.adjusted, found.unsuitable)
        if (found.photo != null) work.loadPhoto {
            val got = container.catalog.downloadPhoto(found) ?: return@loadPhoto
            // Une photo choisie dans la galerie n'est pas remplacée par celle de la base : le fichier téléchargé part.
            if (work.photo != null && !work.photoFromCatalog) container.photos.delete(got.path)
            else work.setPhoto(got.path, fromCatalog = true, credit = found.photoCredit, source = got.source, etag = got.etag)
        }
    }
    var nameFocused by remember { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    // Suggestions calculées hors du fil principal, à chaque lettre tapée.
    val suggestions by produceState(emptyList<CatalogEntry>(), name, picked, catalogStatus.version, catalogStatus.count) {
        value = if (name.trim() == picked) emptyList() else withContext(Dispatchers.Default) { container.catalog.suggest(name) }
    }
    val focus = LocalFocusManager.current
    // Base pas encore chargée sur ce téléphone : on la charge pour les suggestions d'un nouveau thé.
    LaunchedEffect(isNew) { if (isNew && catalogStatus.count == 0 && !catalogStatus.busy) container.catalog.refresh() }
    val doLookup: () -> Unit = {
        val typed = name.trim()
        if (typed.isEmpty()) lookup = Lookup.Problem(text(R.string.edit_name_first))
        else scope.launch {
            lookup = Lookup.Searching
            if (catalogStatus.count == 0) container.catalog.refresh()
            val found = container.catalog.lookup(typed)
            if (found == null) {
                val err = container.catalog.status.value.error
                lookup = if (container.catalog.status.value.count == 0 && err != null) Lookup.Problem(err) else Lookup.NotFound(typed)
            } else applyEntry(found)
        }
    }

    val save: () -> Unit = save@{
        if (work.saving || work.finished != null) return@save
        val n = name.trim()
        if (n.isEmpty()) {
            work.error = ErrorSpot.NAME to text(R.string.edit_name_missing)
            // Le nom est en haut du formulaire : on y remonte, curseur dans le champ.
            scope.launch { scroll.animateScrollTo(0) }
            nameFocus.requestFocus()
            return@save
        }
        work.saving = true
        work.error = null
        // Dans le scope de l'app : l'écriture va au bout même si l'écran est quitté ou recréé.
        container.appScope.launch {
          var saved = false
          try {
            // Photo encore en chemin : attendue un moment (l'écran reste ouvert pendant l'enregistrement). Son chargement
            // tient à une rotation (TeaEditWork) : la photo arrive dans la fiche enregistrée.
            work.awaitPhotos(PHOTO_WAIT_MS)
            val before = work.loaded
            // Relue maintenant : une synchronisation finie pendant la saisie a pu donner position et « synchronisé », et
            // « Vérifier » une nouvelle photo de la base (gardée si la photo n'a pas été changée dans la fiche).
            val current = before?.let { dao.get(it.id) } ?: before
            val keptPhoto = work.photo == before?.photoPath
            val photo = if (keptPhoto && current != null) current.photoPath else work.photo
            // Origine de la photo : celle du thé si elle n'a pas changé, celle de la base pour une photo de la base,
            // aucune pour une photo de la galerie (jamais remplacée).
            val (photoSource, photoEtag) = when {
                photo == null -> null to null
                keptPhoto -> current?.photoSource to current?.photoEtag
                work.photoFromCatalog -> work.photoSource to work.photoEtag
                else -> null to null
            }
            val changedForMachine = before == null || before.temperature != temperature || before.waterMl != water ||
                before.steepSeconds != steep || before.name != n || before.onMachine != onMachine
            val (synced, slot) = machineSyncAfterEdit(current, onMachine, changedForMachine)
            val tea = (current ?: Tea(name = n, temperature = temperature, waterMl = water, steepSeconds = steep)).copy(
                name = n, type = type, origin = origin.trim(), temperature = temperature, waterMl = water, steepSeconds = steep,
                favorite = favorite, onMachine = onMachine, notes = notes.trim(), photoPath = photo, photoSource = photoSource, photoEtag = photoEtag, catalogId = catalogId,
                synced = synced, machineSlot = slot,
            )
            val stored = if (before == null) tea.copy(id = dao.insert(tea)) else tea.also { dao.update(it) }
            val added = withContext(Dispatchers.Main) { work.loaded = stored; work.takeNewPhotos() }
            // Photo remplacée : l'ancienne n'est plus utilisée.
            if (before?.photoPath != null && before.photoPath != photo) container.photos.delete(before.photoPath)
            added.filter { it != photo }.forEach { container.photos.delete(it) }
            saved = true
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            android.util.Log.w("Infusion", "enregistrement du thé impossible", e)
            withContext(Dispatchers.Main) { work.error = ErrorSpot.SAVE to text(R.string.edit_save_failed) }
          } finally {
            // Enregistré : « Enregistrer » reste inactif et l'écran affiché se ferme (voir finished plus haut).
            withContext(NonCancellable + Dispatchers.Main) { if (saved) work.finish(TeaEditWork.Finish.SAVED) else work.saving = false }
          }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = stringResource(R.string.action_back), onClick = leave), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back), tint = c.ink)
            }
            Text(stringResource(if (isNew) R.string.edit_title_new else R.string.edit_title_edit), style = MaterialTheme.typography.titleLarge, color = c.ink)
        }
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 18.dp).padding(bottom = 18.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val nameLabel = stringResource(R.string.edit_name_label)
            Label(nameLabel)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputBox(Modifier.weight(1f)) {
                    if (name.isEmpty()) Text(stringResource(R.string.edit_name_hint), color = c.muted, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = name, onValueChange = { name = it.take(Catalog.NAME_MAX); if (work.error?.first == ErrorSpot.NAME) work.error = null }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink), cursorBrush = SolidColor(c.accentInk),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { doLookup() }),
                        // Nommé pour le lecteur d'écran : l'indication disparaît dès qu'on tape.
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocus).onFocusChanged { nameFocused = it.isFocused }
                            .semantics { contentDescription = nameLabel },
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(c.deep).clickable(enabled = lookup != Lookup.Searching, onClick = doLookup)
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_cloud), contentDescription = null, tint = c.onDeep, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.edit_lookup), style = MaterialTheme.typography.labelLarge, color = c.onDeep)
                }
            }
            if (nameFocused && suggestions.isNotEmpty()) {
                Suggestions(suggestions) { entry ->
                    picked = entry.name; name = entry.name
                    focus.clearFocus()
                    applyEntry(entry)
                }
            }
            LookupMessage(lookup, fieldsShown) { fieldsShown = true; lookup = Lookup.None }
            ErrorText(work.error, ErrorSpot.NAME)

            if (fieldsShown) {
                Column(Modifier.fillMaxWidth().infusionCard(RoundedCornerShape(20.dp)).background(c.card)) {
                    val shownTea = Tea(name = name, type = type, temperature = temperature, waterMl = water, steepSeconds = steep, photoPath = work.photo)
                    TeaPhoto(shownTea, Modifier.fillMaxWidth().height(140.dp), maxPx = 900)
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(c.background)
                                .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_photo), contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.edit_my_photo), style = MaterialTheme.typography.labelMedium, color = c.ink)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                photoLoads > 0 -> stringResource(R.string.edit_photo_loading)
                                work.photo == null -> ""
                                work.photoFromCatalog -> catalogPhotoCredit(work.photoCredit)?.let { stringResource(R.string.edit_photo_from_base_credit, it) }
                                    ?: stringResource(R.string.edit_photo_from_base)
                                else -> stringResource(R.string.edit_photo_mine)
                            },
                            style = MaterialTheme.typography.bodySmall, color = c.muted,
                        )
                    }
                }
                ErrorText(work.error, ErrorSpot.PHOTO)
                Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TeaType.entries.forEach { tt -> Chip(stringResource(tt.label), type == tt, { type = tt }, dot = Color(tt.argb)) }
                }
                InputBox(Modifier.fillMaxWidth()) {
                    val originLabel = stringResource(R.string.edit_origin_hint)
                    if (origin.isEmpty()) Text(originLabel, color = c.muted, style = MaterialTheme.typography.bodyMedium)
                    BasicTextField(origin, { origin = it.take(Catalog.ORIGIN_MAX) }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink), cursorBrush = SolidColor(c.accentInk),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = originLabel })
                }
                val tIndex = MachineLimits.temperatures.indexOf(temperature).coerceAtLeast(0)
                SliderField(stringResource(R.string.label_temperature), MachineLimits.temperatureLabel(temperature, Infusion.metric), tIndex.toFloat(), 0f..MachineLimits.temperatures.lastIndex.toFloat(),
                    MachineLimits.temperatures.size - 2) { temperature = MachineLimits.temperatures[it.toInt().coerceIn(0, MachineLimits.temperatures.lastIndex)] }
                SliderField(stringResource(R.string.label_water), MachineLimits.waterLabel(water, Infusion.metric), water.toFloat(), MachineLimits.WATER_MIN.toFloat()..MachineLimits.WATER_MAX.toFloat(),
                    (MachineLimits.WATER_MAX - MachineLimits.WATER_MIN) / MachineLimits.WATER_STEP - 1) { water = MachineLimits.roundWater(it.toInt()) }
                SliderField(stringResource(R.string.label_steeping), MachineLimits.steepLabel(steep), MachineLimits.steepIndex(steep).toFloat(), 0f..MachineLimits.steepDurations.lastIndex.toFloat(),
                    MachineLimits.steepDurations.size - 2, showTicks = false) { steep = MachineLimits.steepDurations[kotlin.math.round(it).toInt().coerceIn(0, MachineLimits.steepDurations.lastIndex)] }

                Column(Modifier.fillMaxWidth().infusionCard(RoundedCornerShape(20.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    SwitchRow(stringResource(R.string.edit_favorite), stringResource(R.string.edit_favorite_detail), favorite) { favorite = it }
                    val othersOnMachine = machineCount - if (work.loaded?.onMachine == true) 1 else 0
                    SwitchRow(stringResource(R.string.edit_on_machine), stringResource(R.string.edit_on_machine_detail, othersOnMachine + if (onMachine) 1 else 0, MachineLimits.PRESETS), onMachine) {
                        if (it && othersOnMachine >= MachineLimits.PRESETS) work.error = ErrorSpot.MACHINE to text(R.string.edit_machine_full, MachineLimits.PRESETS)
                        else { onMachine = it; work.error = null }
                    }
                }
                ErrorText(work.error, ErrorSpot.MACHINE)
                val notesLabel = stringResource(R.string.edit_notes)
                Label(notesLabel)
                InputBox(Modifier.fillMaxWidth().height(110.dp), alignTop = true) {
                    if (notes.isEmpty()) Text(stringResource(R.string.edit_notes_hint), color = c.muted, style = MaterialTheme.typography.bodyMedium)
                    BasicTextField(notes, { notes = it.take(Catalog.NOTES_MAX) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink), cursorBrush = SolidColor(c.accentInk),
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = notesLabel })
                }
                Text(stringResource(R.string.count_of, notes.length, Catalog.NOTES_MAX), style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.align(Alignment.End))
                ErrorText(work.error, ErrorSpot.SAVE)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.deep).clickable(enabled = !work.saving, onClick = save).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (work.saving && photoLoads > 0) R.string.edit_photo_loading else R.string.action_save),
                        style = MaterialTheme.typography.labelLarge, color = c.onDeep,
                    )
                }
                if (!isNew) {
                    Text(stringResource(R.string.edit_delete), style = MaterialTheme.typography.labelLarge, color = c.danger,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { confirmDelete = true }.padding(vertical = 6.dp))
                }
            }
        }
    }

    if (confirmDelete) {
        val t = work.loaded
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, t?.name ?: name)) },
            text = {
                Text(
                    stringResource(if (t?.onMachine == true) R.string.delete_detail_machine else R.string.delete_detail)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (t != null) {
                        val unsaved = work.takeNewPhotos()
                        container.appScope.launch {
                            dao.delete(t); container.photos.delete(t.photoPath)
                            unsaved.forEach { container.photos.delete(it) }
                            // Retour en arrière par l'écran affiché, même recréé entre-temps (voir finished plus haut).
                            withContext(Dispatchers.Main) { work.finish(TeaEditWork.Finish.DELETED) }
                        }
                    }
                }) { Text(stringResource(R.string.action_delete), color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_keep), color = c.ink) } },
            containerColor = c.card, titleContentColor = c.ink, textContentColor = c.muted,
        )
    }
}

/** Fiches de la base qui correspondent au nom en cours de saisie ; en toucher une remplit le thé. */
@Composable
private fun Suggestions(entries: List<CatalogEntry>, onPick: (CatalogEntry) -> Unit) {
    val c = Infusion.colors
    Column(Modifier.fillMaxWidth().infusionCard(RoundedCornerShape(16.dp)).background(c.card).padding(vertical = 4.dp)) {
        entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = c.line, modifier = Modifier.padding(horizontal = 14.dp))
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.suggest_pick, e.name)) { onPick(e) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(e.type.argb)))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(stringResource(e.type.label), e.origin).filter { it.isNotBlank() }.joinToString(" · ") +
                            " · " + (if (e.unsuitable != null) stringResource(R.string.suggest_unsuitable) else specLine(e.temperature, e.waterMl, e.steepSeconds)),
                        style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LookupMessage(lookup: Lookup, fieldsShown: Boolean, onManual: () -> Unit) {
    val c = Infusion.colors
    val (bg, fg, text) = when (lookup) {
        Lookup.None -> return
        Lookup.Searching -> Triple(c.accentSoft, c.accentInk, stringResource(R.string.lookup_searching))
        is Lookup.Found -> Triple(
            c.sync.copy(alpha = 0.16f), if (c.dark) Color(0xFF9FD8AD) else Color(0xFF245C35),
            listOfNotNull(
                stringResource(R.string.lookup_found, if (lookup.origin.isNotEmpty()) stringResource(R.string.lookup_found_origin, lookup.name, lookup.origin) else lookup.name),
                if (lookup.adjusted) stringResource(R.string.lookup_adjusted) else null,
                lookup.unsuitable?.let { stringResource(R.string.lookup_warning, it.asString()) },
            ).joinToString(" "),
        )
        is Lookup.NotFound -> Triple(c.heatSoft, c.heatInk, stringResource(R.string.lookup_not_found, lookup.name))
        is Lookup.Problem -> Triple(c.heatSoft, c.heatInk, lookup.message.asString())
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
        if (!fieldsShown && (lookup is Lookup.NotFound || lookup is Lookup.Problem)) {
            Text(stringResource(R.string.lookup_manual), style = MaterialTheme.typography.labelLarge, color = c.accentInk,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onManual).padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = Infusion.colors.ink)

/** Erreur montrée à sa place, et dite au lecteur d'écran quand elle paraît. */
@Composable
private fun ErrorText(error: Pair<ErrorSpot, UiText>?, spot: ErrorSpot) {
    val e = error?.takeIf { it.first == spot } ?: return
    Text(
        e.second.asString(), style = MaterialTheme.typography.bodyMedium, color = Infusion.colors.danger,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun InputBox(modifier: Modifier, alignTop: Boolean = false, content: @Composable () -> Unit) {
    val c = Infusion.colors
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = if (alignTop) Alignment.TopStart else Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun SliderField(
    label: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    /** Les 110 durées font une ligne de points serrés : sans repères, le curseur reste lisible. */
    showTicks: Boolean = true,
    onChange: (Float) -> Unit,
) {
    val c = Infusion.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium, color = c.accentInk)
        }
        // Lu « Température, 90 °C » : sans cela, le lecteur d'écran annonce un pourcentage de la plage (des index ici).
        Slider(current, onChange, modifier = Modifier.semantics { contentDescription = label; stateDescription = value }, valueRange = range, steps = steps,
            colors = if (showTicks) SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line)
            else SliderDefaults.colors(
                thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ))
    }
}

/** Toute la ligne bascule l'interrupteur, et le lecteur d'écran la lit d'un seul arrêt (« Favori, activé »). */
@Composable
private fun ColumnScope.SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Infusion.colors
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White, uncheckedTrackColor = c.line, uncheckedBorderColor = c.line, uncheckedThumbColor = Color.White))
    }
}

/** Attente au plus d'une photo en chemin quand on enregistre ; au-delà, le thé part avec la photo qu'il a. */
private const val PHOTO_WAIT_MS = 15_000L

/** Crédit à écrire sous une photo de la base : celui de sa fiche, s'il y en a un (sinon rien de plus). */
internal fun catalogPhotoCredit(credit: String?): String? = credit?.trim()?.takeIf { it.isNotEmpty() }

/** Réglages affichés d'entrée : thé existant, ou nouveau thé venu du mode manuel (pas d'après des valeurs « par défaut »). */
internal fun fieldsShownAtStart(isNew: Boolean, draft: TeaDraft?): Boolean = !isNew || draft != null

/**
 * « Synchronisé » et position à enregistrer, d'après la ligne relue au moment d'enregistrer (une synchronisation a pu
 * finir pendant la saisie) : gardés si le thé ne change pas pour la machine, « à synchroniser » sinon, sans position
 * hors de la machine.
 */
internal fun machineSyncAfterEdit(current: Tea?, onMachine: Boolean, changedForMachine: Boolean): Pair<Boolean, Int?> =
    (if (onMachine && changedForMachine) false else current?.synced ?: true) to (if (onMachine) current?.machineSlot else null)
