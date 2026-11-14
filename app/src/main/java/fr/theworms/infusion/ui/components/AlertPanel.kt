package fr.theworms.infusion.ui.components

import fr.theworms.infusion.text.asString
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.theworms.infusion.R
import fr.theworms.infusion.ble.AlertKind
import fr.theworms.infusion.ble.MachineAlert
import fr.theworms.infusion.ui.theme.AlertError
import fr.theworms.infusion.ui.theme.AlertWarn
import fr.theworms.infusion.ui.theme.OnAlert

/**
 * Demande ou erreur de la machine, qui clignote : orange pour une demande (eau, couvercle…), rouge pour
 * une erreur. Sur l'accueil à la place des boutons, et dans la fenêtre d'infusion.
 */
@Composable
fun MachineAlertPanel(alert: MachineAlert, modifier: Modifier = Modifier) {
    val base = if (alert.error) AlertError else AlertWarn
    val blink = rememberInfiniteTransition(label = "alerte")
    val pulse by blink.animateFloat(0f, 1f, infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulsation")
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 62.dp)
            .border((7 * (1f - pulse)).dp, base.copy(alpha = 0.35f * (1f - pulse)), shape)
            .clip(shape).background(lerp(base, Color.White, 0.45f * pulse))
            .semantics { liveRegion = LiveRegionMode.Assertive }
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(OnAlert), contentAlignment = Alignment.Center) {
            Icon(painterResource(alertIcon(alert.kind)), contentDescription = null, tint = base, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(alert.title.asString(), style = MaterialTheme.typography.titleMedium, color = OnAlert)
            // Détail opaque : à 82 %, il tombait à 3,9:1 sur le rouge au creux de la pulsation (4,5:1 exigés en 12 sp).
            Text(alert.detail.asString(), style = MaterialTheme.typography.bodySmall, color = OnAlert)
        }
    }
}

private fun alertIcon(kind: AlertKind): Int = when (kind) {
    AlertKind.WATER -> R.drawable.ic_drop
    AlertKind.LID -> R.drawable.ic_lid
    AlertKind.CUP -> R.drawable.ic_cup
    AlertKind.CHAMBER -> R.drawable.ic_chamber
    AlertKind.NOZZLE -> R.drawable.ic_nozzle
    AlertKind.OFFLINE -> R.drawable.ic_bluetooth
    AlertKind.WARNING, AlertKind.ERROR -> R.drawable.ic_warn
}
