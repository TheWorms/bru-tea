package fr.theworms.infusion.ui.teas

import androidx.compose.runtime.remember
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.theworms.infusion.AppContainer
import fr.theworms.infusion.R
import fr.theworms.infusion.domain.TeaType
import fr.theworms.infusion.domain.normalizeForSearch
import fr.theworms.infusion.ui.components.Chip
import fr.theworms.infusion.ui.components.TeaCard
import fr.theworms.infusion.ui.components.infusionCard
import fr.theworms.infusion.ui.theme.Infusion
import kotlinx.coroutines.launch

private sealed interface Filter {
    data object All : Filter
    data object Favorites : Filter
    data object OnMachine : Filter
    data class Type(val type: TeaType) : Filter
}

/** Catégorie gardée quand on revient de la modification d'un thé (« type:GREEN », « fav »…). */
private val FilterSaver = Saver<Filter, String>(
    save = { f -> when (f) { Filter.All -> "all"; Filter.Favorites -> "fav"; Filter.OnMachine -> "machine"; is Filter.Type -> "type:" + f.type.name } },
    restore = { s ->
        when {
            s == "fav" -> Filter.Favorites
            s == "machine" -> Filter.OnMachine
            s.startsWith("type:") -> TeaType.entries.firstOrNull { it.name == s.removePrefix("type:") }?.let { Filter.Type(it) } ?: Filter.All
            else -> Filter.All
        }
    },
)

@Composable
fun TeasScreen(container: AppContainer, onEdit: (Long) -> Unit) {
    val c = Infusion.colors
    val dao = container.database.teas()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable(stateSaver = FilterSaver) { mutableStateOf<Filter>(Filter.All) }
    val scope = rememberCoroutineScope()
    // Position gardée au retour d'une modification : la grille n'est affichée qu'une fois la liste relue
    // (avant, elle partait d'une liste vide et remontait en haut).
    val gridState = rememberLazyGridState()
    val loaded by remember(dao) { dao.observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val teas = loaded ?: return

    val q = normalizeForSearch(query)
    val shown = teas.filter { t ->
        when (val f = filter) {
            Filter.All -> true
            Filter.Favorites -> t.favorite
            Filter.OnMachine -> t.onMachine
            is Filter.Type -> t.type == f.type
        } && (q.isEmpty() || normalizeForSearch(t.name).contains(q) || normalizeForSearch(t.notes).contains(q))
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(
                Modifier.fillMaxWidth().infusionCard(RoundedCornerShape(16.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    val searchLabel = stringResource(R.string.teas_search)
                    if (query.isEmpty()) Text(searchLabel, color = c.muted, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                        // Nommé pour le lecteur d'écran : l'indication disparaît dès qu'on tape.
                        cursorBrush = SolidColor(c.accentInk), modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchLabel },
                    )
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.teas_all), filter == Filter.All, { filter = Filter.All })
                Chip(stringResource(R.string.teas_favorites), filter == Filter.Favorites, { filter = Filter.Favorites })
                Chip(stringResource(R.string.teas_on_machine), filter == Filter.OnMachine, { filter = Filter.OnMachine })
                TeaType.entries.forEach { type ->
                    Chip(stringResource(type.label), filter == Filter.Type(type), { filter = Filter.Type(type) }, dot = Color(type.argb))
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.deep).clickable { onEdit(0L) }.padding(15.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = c.onDeep, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.teas_add), style = MaterialTheme.typography.labelLarge, color = c.onDeep)
            }
        }
        item(span = { GridItemSpan(2) }) {
            Text(stringResource(R.string.teas_hint), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (shown.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(22.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (teas.isEmpty()) R.string.teas_empty else R.string.teas_none_here), color = c.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(shown, key = { it.id }) { tea ->
            TeaCard(
                tea, onClick = { onEdit(tea.id) },
                onFavorite = { scope.launch { dao.setFavorite(tea.id, !tea.favorite) } },
            )
        }
    }
}
