package fr.theworms.infusion.ui.theme

import fr.theworms.infusion.R
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class ThemeMode(@StringRes val label: Int) { LIGHT(R.string.theme_light), DARK(R.string.theme_dark), SYSTEM(R.string.theme_system) }

enum class NavDisplay(@StringRes val label: Int) { ICONS(R.string.nav_icons), BOTH(R.string.nav_both), TEXT(R.string.nav_text) }

/**
 * Couleurs proposées dans les options. `deep` teinte le bloc 1 de l'accueil, l'écran d'infusion
 * et les boutons foncés ; avec Sauge, c'est l'ardoise du logo d'origine (#192F33).
 */
enum class Accent(@StringRes val label: Int, val color: Color, val deep: Color, val deepDark: Color) {
    SAUGE(R.string.accent_sauge, Color(0xFF8CA674), Color(0xFF192F33), Color(0xFF21403F)),
    MATCHA(R.string.accent_matcha, Color(0xFF7FB36A), Color(0xFF1F3A1E), Color(0xFF2A4C28)),
    AMBRE(R.string.accent_ambre, Color(0xFFD9A05B), Color(0xFF3F2B14), Color(0xFF523A1D)),
    CANARD(R.string.accent_canard, Color(0xFF5FA8A8), Color(0xFF123637), Color(0xFF1A4748)),
    CIEL(R.string.accent_ciel, Color(0xFF7FA3C4), Color(0xFF1A2C40), Color(0xFF253C55)),
    PRUNE(R.string.accent_prune, Color(0xFFB98AB0), Color(0xFF36213B), Color(0xFF472C4E)),
}

/** Couleurs propres à l'app, en plus de celles de Material. */
@Immutable
data class InfusionColors(
    val dark: Boolean,
    val background: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    /** Texte ou icône dans la couleur choisie, lisible sur le fond. */
    val accentInk: Color,
    /** Fond léger dans la couleur choisie (sélection, pastilles). */
    val accentSoft: Color,
    /** Texte foncé posé sur la couleur choisie (boutons pleins). */
    val onAccent: Color,
    val deep: Color,
    val onDeep: Color,
    val onDeepMuted: Color,
    /** Logo « synchronisé sur la machine » : toujours vert, quelle que soit la couleur choisie. */
    val sync: Color,
    val heat: Color,
    val heatInk: Color,
    val heatSoft: Color,
    val danger: Color,
)

fun infusionColors(accent: Accent, dark: Boolean): InfusionColors {
    val background = if (dark) Color(0xFF0F1B1D) else Color(0xFFF6F7F4)
    return InfusionColors(
        dark = dark,
        background = background,
        card = if (dark) Color(0xFF172A2D) else Color.White,
        ink = if (dark) Color(0xFFE7EEE9) else Color(0xFF192F33),
        muted = if (dark) Color(0xFF93A7A6) else Color(0xFF5D7073),
        line = if (dark) Color(0xFF273B3E) else Color(0xFFE1E6E1),
        accent = accent.color,
        accentInk = if (dark) lerp(accent.color, Color.White, 0.30f) else lerp(accent.color, Color.Black, 0.42f),
        accentSoft = lerp(if (dark) background else Color.White, accent.color, if (dark) 0.22f else 0.20f),
        onAccent = Color(0xFF14272A),
        deep = if (dark) accent.deepDark else accent.deep,
        onDeep = Color(0xFFF3F6F1),
        onDeepMuted = Color(0xFFA9BAB5),
        sync = Color(0xFF3F9A5B),
        heat = Color(0xFFD0873A),
        heatInk = if (dark) Color(0xFFE6A865) else Color(0xFF9A5B12),
        heatSoft = if (dark) Color(0xFF3A2A18) else Color(0xFFF7E9D8),
        danger = if (dark) Color(0xFFE27C6F) else Color(0xFFB4473A),
    )
}

val LocalInfusionColors = staticCompositionLocalOf { infusionColors(Accent.SAUGE, dark = false) }
val LocalNavDisplay = staticCompositionLocalOf { NavDisplay.BOTH }
val LocalMetric = staticCompositionLocalOf { true }

/** Alertes de la machine : distinctes des six couleurs au choix, mêmes en clair et en sombre. */
val AlertWarn = Color(0xFFF07A48)
val AlertError = Color(0xFFE5534B)
val OnAlert = Color(0xFF2A0D04)

object Infusion {
    val colors: InfusionColors @Composable get() = LocalInfusionColors.current
    val navDisplay: NavDisplay @Composable get() = LocalNavDisplay.current
    /** Unités choisies (°C et ml, ou °F et oz) : celles de l'écran de la machine. */
    val metric: Boolean @Composable get() = LocalMetric.current
}

@Composable
fun InfusionTheme(
    theme: ThemeMode,
    accent: Accent,
    navDisplay: NavDisplay,
    metric: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val c = infusionColors(accent, dark)
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.deep, onSecondary = c.onDeep,
            background = c.background, onBackground = c.ink, surface = c.card, onSurface = c.ink,
            surfaceVariant = c.background, onSurfaceVariant = c.muted, outline = c.line, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.deep, onSecondary = c.onDeep,
            background = c.background, onBackground = c.ink, surface = c.card, onSurface = c.ink,
            surfaceVariant = c.background, onSurfaceVariant = c.muted, outline = c.line, error = c.danger,
        )
    }
    CompositionLocalProvider(LocalInfusionColors provides c, LocalNavDisplay provides navDisplay, LocalMetric provides metric) {
        MaterialTheme(colorScheme = scheme, typography = InfusionTypography, content = content)
    }
}
