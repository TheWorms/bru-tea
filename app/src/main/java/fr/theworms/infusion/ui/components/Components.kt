package fr.theworms.infusion.ui.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.theworms.infusion.R
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.domain.MachineLimits
import fr.theworms.infusion.ui.theme.Infusion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

val CardShape = RoundedCornerShape(20.dp)

/** Carte blanche (ou foncée en thème sombre) avec l'ombre douce de la démo. */
fun Modifier.infusionCard(shape: RoundedCornerShape = RoundedCornerShape(22.dp)): Modifier = this
    .shadow(6.dp, shape, ambientColor = Color(0x22192F33), spotColor = Color(0x33192F33))
    .clip(shape)

/** Photo d'un thé, lue depuis les fichiers de l'app et réduite à la taille affichée. */
@Composable
fun TeaPhoto(tea: Tea, modifier: Modifier = Modifier, maxPx: Int = 900) {
    val path = tea.photoPath
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) { decodeSampled(path, maxPx) }
    }
    val typeColor = Color(tea.type.argb)
    Box(modifier.background(lerp(Infusion.colors.card, typeColor, 0.22f)), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_leaf), contentDescription = null, tint = typeColor, modifier = Modifier.size(30.dp))
        }
    }
}

/**
 * Photo décodée à peu près à la taille affichée : échantillonnée sur le plus grand côté, avec un plafond de
 * 4 millions de pixels ; un fichier illisible ou trop lourd (manque de mémoire) laisse la vignette.
 */
private fun decodeSampled(path: String, maxPx: Int): ImageBitmap? = try {
    val file = File(path)
    if (!file.exists()) null
    else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (side / (sample * 2) >= maxPx || (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > MAX_PIXELS) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
} catch (e: Throwable) {
    null
}

private const val MAX_PIXELS = 4_000_000L

/** Logo vert « synchronisé sur la machine », orange tant que la machine n'a pas confirmé. */
@Composable
fun SyncBadge(synced: Boolean, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    val c = Infusion.colors
    Box(
        modifier.size(size).clip(CircleShape).background(if (synced) c.sync else c.heat),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_sync),
            contentDescription = stringResource(if (synced) R.string.sync_ok else R.string.sync_pending),
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
fun Tea.specLine(): String = specLine(temperature, waterMl, steepSeconds)

/** « 80 °C · 250 ml · 1:30 », dans les unités choisies. */
@Composable
fun specLine(temperature: Int, waterMl: Int, steepSeconds: Int): String {
    val metric = Infusion.metric
    return "${MachineLimits.temperatureLabel(temperature, metric)} · ${MachineLimits.waterLabel(waterMl, metric)} · ${MachineLimits.steepLabel(steepSeconds)}"
}

/** Carte d'un thé : photo, type, nom, réglages. `onFavorite` affiche l'étoile cliquable (onglet Thés). */
@Composable
fun TeaCard(
    tea: Tea,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    photoHeight: Dp = 112.dp,
    onFavorite: (() -> Unit)? = null,
    /** La photo prend toute la hauteur que le texte laisse (carte de hauteur fixée par `modifier`). */
    fillHeight: Boolean = false,
) {
    val c = Infusion.colors
    Column(modifier.infusionCard(CardShape).background(c.card).clickable(onClick = onClick)) {
        Box(if (fillHeight) Modifier.weight(1f) else Modifier) {
            TeaPhoto(tea, Modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(photoHeight)), maxPx = if (fillHeight) 700 else 500)
            if (onFavorite != null) {
                // Cible de 48 dp (cercle visible de 34 dp) ; le lecteur d'écran dit l'action, pas « étoile blanche ».
                val label = stringResource(if (tea.favorite) R.string.fav_remove else R.string.fav_add)
                Box(
                    Modifier.size(48.dp).clickable(onClickLabel = label, onClick = onFavorite).semantics { contentDescription = label }.padding(7.dp)
                        .clip(CircleShape).background(if (c.dark) Color(0xD90F1B1D) else Color(0xEBFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (tea.favorite) "★" else "☆", color = if (tea.favorite) c.heat else c.muted, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (tea.onMachine) SyncBadge(tea.synced, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(tea.type.argb)))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(tea.type.label), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Text(tea.name, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(tea.specLine(), style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}

/** Petit choix sélectionnable, dans la couleur choisie quand il est actif. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dot: Color? = null) {
    val c = Infusion.colors
    Row(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) c.accent else c.card)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.line, RoundedCornerShape(99.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) c.onAccent else c.ink)
    }
}

/** Choix exclusif sur une ligne (thème, affichage). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Infusion.colors
    Row(
        modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f).clip(RoundedCornerShape(14.dp)).background(c.background).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) c.accent else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(value) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) c.onAccent else c.ink, maxLines = 2)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Infusion.colors.ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
