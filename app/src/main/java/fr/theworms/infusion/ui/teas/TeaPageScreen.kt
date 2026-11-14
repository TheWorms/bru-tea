package fr.theworms.infusion.ui.teas

import androidx.compose.runtime.remember
import fr.theworms.infusion.text.displayedLanguage
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.theworms.infusion.AppContainer
import fr.theworms.infusion.R
import fr.theworms.infusion.catalog.Catalog
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.domain.MachineLimits
import fr.theworms.infusion.ui.components.TeaPhoto
import fr.theworms.infusion.ui.components.specLine
import fr.theworms.infusion.ui.theme.Infusion
import kotlinx.coroutines.launch

/**
 * Fiche d'un thé : la photo en haut, fondue dans la page, avec le nom posé dessus ; les réglages dans un
 * bandeau, la dose à part, la description ; « Infuser » envoie les réglages puis lance. Boutons en verre
 * fumé : retour, synchro (brique : pas encore sur la machine, sauge : reçu), favori, crayon.
 */
@Composable
fun TeaPageScreen(
    container: AppContainer,
    teaId: Long,
    connected: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onBrew: (Tea) -> Unit,
) {
    val c = Infusion.colors
    val metric = Infusion.metric
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tea by remember(teaId) { container.database.teas().observe(teaId) }.collectAsStateWithLifecycle(initialValue = null)
    val t = tea ?: return
    val parts = Catalog.splitNotes(t.notes)
    val grams = parts.grams?.let { g -> Catalog.scaleDose(g, parts.gramsForMl ?: t.waterMl, t.waterMl) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Photo fondue dans la page ; type, origine et nom posés sur son bas.
                Box(Modifier.fillMaxWidth().height(400.dp)) {
                    TeaPhoto(t, Modifier.fillMaxSize(), maxPx = 1400)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.28f), 0.22f to Color.Transparent,
                                0.45f to Color.Transparent, 1f to c.background,
                            ),
                        ),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PhotoChip(stringResource(t.type.label), dot = Color(t.type.argb))
                            originLabel(t.origin)?.let { PhotoChip(it) }
                        }
                        Text(
                            t.name, color = c.ink,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, lineHeight = 36.sp),
                        )
                    }
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Les trois réglages dans un bandeau.
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(22.dp)).background(c.card).padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spec(R.drawable.ic_temp, MachineLimits.temperatureLabel(t.temperature, metric), stringResource(R.string.spec_temperature), Modifier.weight(1f))
                        SpecDivider()
                        Spec(R.drawable.ic_drop, MachineLimits.waterLabel(t.waterMl, metric), stringResource(R.string.spec_water), Modifier.weight(1f))
                        SpecDivider()
                        Spec(R.drawable.ic_clock, MachineLimits.steepLabel(t.steepSeconds), stringResource(R.string.spec_steep), Modifier.weight(1f))
                    }
                    // La dose à part : c'est ce qu'on cherche en préparant la chambre d'infusion.
                    if (grams != null) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accentSoft).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ic_leaf), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.dose_title, Catalog.formatGramsLabel(grams, displayedLanguage())), style = MaterialTheme.typography.titleMedium, color = c.ink)
                                Text(
                                    stringResource(R.string.dose_detail, MachineLimits.waterLabel(t.waterMl, metric)),
                                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                                )
                            }
                        }
                    }
                    if (parts.text.isNotBlank()) {
                        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.page_about), style = MaterialTheme.typography.labelSmall, color = c.muted)
                            Text(parts.text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp), color = c.ink)
                        }
                    }
                    if (parts.caffeineFree) {
                        Row(
                            Modifier.clip(CircleShape).background(c.card).padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(c.sync))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.tag_caffeine_free), style = MaterialTheme.typography.labelMedium, color = c.muted)
                        }
                    }
                }
            }
            // « Infuser » rappelle les réglages envoyés à la machine.
            Column(
                Modifier.fillMaxWidth().background(c.background).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 62.dp).alpha(if (connected) 1f else 0.45f).clip(RoundedCornerShape(20.dp))
                        .background(c.accent).clickable(enabled = connected) { onBrew(t) },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.action_brew), style = MaterialTheme.typography.titleMedium, color = c.onAccent)
                    Spacer(Modifier.width(10.dp))
                    Text(specLine(t.temperature, t.waterMl, t.steepSeconds), style = MaterialTheme.typography.bodySmall, color = c.onAccent.copy(alpha = 0.75f))
                }
                if (!connected) {
                    Text(
                        stringResource(R.string.machine_unreachable), style = MaterialTheme.typography.bodySmall, color = c.muted,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // Boutons en verre fumé, fixes au-dessus de la photo.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassButton(stringResource(R.string.action_back), onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            if (t.onMachine) {
                val (bg, fg) = if (t.synced) SYNC_OK else SYNC_PENDING
                val syncMsg = if (t.synced) t.machineSlot?.let { stringResource(R.string.page_sync_ok_slot, it) } ?: stringResource(R.string.page_sync_ok)
                    else stringResource(R.string.page_sync_pending)
                GlassButton(
                    stringResource(if (t.synced) R.string.sync_ok else R.string.sync_not_yet), background = bg,
                    onClick = { Toast.makeText(context, syncMsg, Toast.LENGTH_SHORT).show() },
                ) { Icon(painterResource(R.drawable.ic_sync), contentDescription = null, tint = fg, modifier = Modifier.size(20.dp)) }
            }
            GlassButton(stringResource(if (t.favorite) R.string.fav_remove else R.string.fav_add), onClick = {
                scope.launch { container.database.teas().update(t.copy(favorite = !t.favorite)) }
            }) {
                Text(if (t.favorite) "★" else "☆", color = if (t.favorite) c.accent else Color.White, style = MaterialTheme.typography.titleLarge)
            }
            GlassButton(stringResource(R.string.edit_this_tea), onClick = onEdit) { Icon(painterResource(R.drawable.ic_pen), contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp)) }
        }
    }
}

/** Fond des pastilles de synchro : même verre fumé, teinté brique (en attente) ou sauge (reçu). */
private val SYNC_PENDING = Color(0x94964A3A) to Color(0xFFF4D3CB)
private val SYNC_OK = Color(0x9448704A) to Color(0xFFD6EBCF)
private val GLASS = Color(0x6B0F1B1D)

/** « Inde (Darjeeling) » → « Inde · Darjeeling ». */
private fun originLabel(origin: String): String? =
    origin.trim().takeIf { it.isNotEmpty() }?.replace(PARENTHESES, " · $1")

private val PARENTHESES = Regex("\\s*\\((.+)\\)\\s*$")

@Composable
private fun GlassButton(description: String, background: Color = GLASS, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(background).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PhotoChip(label: String, dot: Color? = null) {
    val c = Infusion.colors
    Row(
        Modifier.clip(CircleShape).background(c.background.copy(alpha = 0.78f)).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink)
    }
}

@Composable
private fun Spec(icon: Int, value: String, label: String, modifier: Modifier) {
    val c = Infusion.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = c.accentInk, modifier = Modifier.size(18.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = c.ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

@Composable
private fun SpecDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight(0.8f).background(Infusion.colors.line))
}
