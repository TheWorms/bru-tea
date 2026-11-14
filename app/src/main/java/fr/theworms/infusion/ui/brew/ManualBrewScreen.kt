package fr.theworms.infusion.ui.brew

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.text.asString
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import fr.theworms.infusion.R
import fr.theworms.infusion.domain.MachineLimits
import fr.theworms.infusion.ui.components.Chip
import fr.theworms.infusion.ui.components.infusionCard
import fr.theworms.infusion.ui.components.specLine
import fr.theworms.infusion.ui.theme.Infusion

/** Mode manuel : température, eau, durée, puis « Infuser » (envoi branché au lot Bluetooth). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualBrewScreen(
    connected: Boolean = false,
    /** Pourquoi « Infuser » est grisé quand la machine n'est pas connectée (voir ConnectionTexts.whyNoBrew). */
    whyNoBrew: UiText? = null,
    onBrew: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onSaveAsTea: (Int, Int, Int) -> Unit = { _, _, _ -> },
) {
    val c = Infusion.colors
    var temp by rememberSaveable { mutableIntStateOf(90) }
    var water by rememberSaveable { mutableIntStateOf(250) }
    var steep by rememberSaveable { mutableIntStateOf(180) }
    val tempIndex = MachineLimits.temperatures.indexOf(temp).coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.manual_intro), style = MaterialTheme.typography.bodySmall, color = c.muted)

        Column(
            Modifier.fillMaxWidth().infusionCard(RoundedCornerShape(24.dp)).background(c.card).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.label_temperature), style = MaterialTheme.typography.labelMedium, color = c.muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton("−", stringResource(R.string.manual_cooler)) { temp = MachineLimits.temperatures[(tempIndex - 1).coerceAtLeast(0)] }
                Text(
                    MachineLimits.temperatureLabel(temp, Infusion.metric), style = MaterialTheme.typography.displayMedium, color = c.ink,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                RoundButton("+", stringResource(R.string.manual_hotter)) { temp = MachineLimits.temperatures[(tempIndex + 1).coerceAtMost(MachineLimits.temperatures.lastIndex)] }
            }
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(75 to R.string.chip_white, 80 to R.string.chip_green, 90 to R.string.chip_oolong, 95 to R.string.chip_black, 100 to R.string.chip_herbal).forEach { (t, label) ->
                    Chip(stringResource(label), temp == t, { temp = t })
                }
            }
        }

        Column(Modifier.fillMaxWidth().infusionCard().background(c.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val waterTitle = stringResource(R.string.label_water)
            val waterLabel = MachineLimits.waterLabel(water, Infusion.metric)
            Row { Text(waterTitle, style = MaterialTheme.typography.labelMedium, color = c.ink, modifier = Modifier.weight(1f)); Text(waterLabel, style = MaterialTheme.typography.labelMedium, color = c.accentInk) }
            Slider(
                value = water.toFloat(), onValueChange = { water = MachineLimits.roundWater(it.toInt()) },
                // Lu « Eau, 250 ml » et non en pourcentage de la plage.
                modifier = Modifier.semantics { contentDescription = waterTitle; stateDescription = waterLabel },
                valueRange = MachineLimits.WATER_MIN.toFloat()..MachineLimits.WATER_MAX.toFloat(),
                steps = (MachineLimits.WATER_MAX - MachineLimits.WATER_MIN) / MachineLimits.WATER_STEP - 1,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line),
            )
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200 to R.string.chip_cup, 300 to R.string.chip_mug, 350 to R.string.chip_max).forEach { (ml, label) ->
                    Chip(stringResource(label), water == ml, { water = ml }, modifier = Modifier.weight(1f))
                }
            }
        }

        Column(Modifier.fillMaxWidth().infusionCard().background(c.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.label_steep_time), style = MaterialTheme.typography.labelMedium, color = c.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton("−", stringResource(R.string.manual_shorter)) { steep = MachineLimits.steepDurations[(MachineLimits.steepIndex(steep) - 1).coerceAtLeast(0)] }
                Text(MachineLimits.steepLabel(steep), style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                RoundButton("+", stringResource(R.string.manual_longer)) { steep = MachineLimits.steepDurations[(MachineLimits.steepIndex(steep) + 1).coerceAtMost(MachineLimits.steepDurations.lastIndex)] }
            }
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 120, 180, 240, 300).forEach { s -> Chip(stringResource(R.string.minutes_short, s / 60), steep == s, { steep = s }) }
            }
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(18.dp))
                .background(if (connected) c.accent else c.accent.copy(alpha = 0.45f))
                .clickable(enabled = connected) { onBrew(temp, water, steep) },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.manual_brew_button, specLine(temp, water, steep)),
                style = MaterialTheme.typography.labelLarge, color = c.onAccent,
            )
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.5.dp, c.line, RoundedCornerShape(16.dp)).clickable { onSaveAsTea(temp, water, steep) }.padding(15.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.manual_save), style = MaterialTheme.typography.labelLarge, color = c.ink) }
        if (!connected) Text(whyNoBrew?.asString() ?: stringResource(R.string.machine_unreachable), style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(4.dp))
    }
}

/** « − » / « + » : lus par ce qu'ils règlent (« Moins chaud », « Plus long »), pas « moins » / « plus ». */
@Composable
private fun RoundButton(text: String, description: String, onClick: () -> Unit) {
    val c = Infusion.colors
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(c.background).clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.headlineMedium, color = c.ink) }
}
