package fr.theworms.infusion.ui

import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.Lifecycle
import androidx.core.net.toUri
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.mutableLongStateOf
import androidx.activity.compose.LocalActivity
import android.provider.Settings
import android.content.Intent
import fr.theworms.infusion.text.asString
import fr.theworms.infusion.text.UiText
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import fr.theworms.infusion.ui.teas.TeaDraft
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.theworms.infusion.ble.BleTransport
import fr.theworms.infusion.ble.MachineState
import fr.theworms.infusion.ble.brew
import fr.theworms.infusion.ui.brew.BrewOverlay
import fr.theworms.infusion.ui.machine.PairScreen
import kotlinx.coroutines.launch
import fr.theworms.infusion.ui.teas.TeaEditScreen
import fr.theworms.infusion.ui.teas.TeaPageScreen
import fr.theworms.infusion.AppContainer
import fr.theworms.infusion.R
import fr.theworms.infusion.data.AppSettings
import fr.theworms.infusion.ui.brew.ManualBrewScreen
import fr.theworms.infusion.ui.home.HomeScreen
import fr.theworms.infusion.ui.machine.MachineScreen
import fr.theworms.infusion.ui.teas.TeasScreen
import fr.theworms.infusion.ui.theme.Infusion
import fr.theworms.infusion.ui.theme.NavDisplay
import java.time.LocalTime

enum class Tab(val route: String, @StringRes val label: Int, val icon: Int, val title: () -> Int) {
    HOME("accueil", R.string.tab_home, R.drawable.ic_home, {
        val h = LocalTime.now().hour
        if (h < 12) R.string.greeting_morning else if (h < 18) R.string.greeting_afternoon else R.string.greeting_evening
    }),
    TEAS("thes", R.string.tab_teas, R.drawable.ic_leaf, { R.string.tab_teas }),
    BREW("infuser", R.string.tab_brew, R.drawable.ic_cup, { R.string.tab_brew }),
    MACHINE("machine", R.string.tab_machine, R.drawable.ic_machine, { R.string.tab_machine }),
}

@Composable
fun InfusionAppUi(container: AppContainer, settings: AppSettings) {
    val c = Infusion.colors
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }
    val tab = currentTab ?: Tab.HOME
    val machine by container.machine.state.collectAsStateWithLifecycle()
    val brew by container.machine.brew.collectAsStateWithLifecycle()
    var permissionTick by remember { mutableIntStateOf(0) }
    val activity = LocalActivity.current
    var permissionAskedAt by remember { mutableLongStateOf(0L) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
        // Refus définitif : Android répond aussitôt sans rien montrer. On ouvre alors les réglages de l'app,
        // seul endroit où l'autorisation peut encore être donnée.
        val refusedForGood = !container.machine.hasPermissions() && activity != null &&
            BleTransport.REQUIRED_PERMISSIONS.none { p -> activity.shouldShowRequestPermissionRationale(p) } &&
            System.currentTimeMillis() - permissionAskedAt < 600
        if (refusedForGood) runCatching {
            activity?.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${activity.packageName}".toUri()))
        }
        container.machine.onForeground()
    }
    val requestPermissions: () -> Unit = {
        permissionAskedAt = System.currentTimeMillis()
        permissionLauncher.launch(BleTransport.REQUIRED_PERMISSIONS)
    }
    var brewError by remember { mutableStateOf<UiText?>(null) }
    val manualBrewName = stringResource(R.string.manual_brew_name)
    // Lance une infusion ; l'écran d'infusion s'affiche par-dessus tout tant qu'elle n'est pas fermée.
    // Notifications (Android 13+) : pour suivre l'infusion hors de l'app ; demandées au lancement d'une infusion.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val appContext = LocalContext.current
    // L'infusion part dans le scope de l'app : une rotation ou un changement d'écran ne la coupe pas en route.
    // Ignorée si une infusion est déjà affichée (double appui).
    val startBrew: (suspend () -> UiText?) -> Unit = start@{ block ->
        if (container.machine.brew.value != null) return@start
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        container.appScope.launch {
            val error = block()
            withContext(Dispatchers.Main) { brewError = error }
        }
    }
    /** Navigation seulement depuis l'écran affiché : un double appui ou l'écran qui sort ne naviguent pas deux fois. */
    fun NavBackStackEntry?.go(action: () -> Unit) { if (this == null || lifecycle.currentState == Lifecycle.State.RESUMED) action() }
    val goTab: (Tab) -> Unit = { t ->
        nav.navigate(t.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(Modifier.fillMaxSize()) {
    // Sous la fenêtre d'infusion, l'app est masquée aux lecteurs d'écran.
    Column(Modifier.fillMaxSize().background(c.background).then(if (brew != null) Modifier.clearAndSetSemantics { } else Modifier)) {
        // Page d'un thé et édition : plein écran, sans en-tête ni barre du bas.
        if (currentTab != null || route == null) {
            Header(
                title = stringResource(tab.title()), state = machine, modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                onClick = {
                    nav.currentBackStackEntry.go {
                        when {
                            !container.machine.hasPermissions() -> requestPermissions()
                            machine.connection == MachineState.Connection.NONE -> nav.navigate("associer") { launchSingleTop = true }
                            machine.connection == MachineState.Connection.OFFLINE -> container.appScope.launch { container.machine.ensureConnected(userInitiated = true) }
                            else -> goTab(Tab.MACHINE)
                        }
                    }
                },
            )
        }
        Box(Modifier.weight(1f)) {
            NavHost(nav, startDestination = Tab.HOME.route) {
                composable(Tab.HOME.route) { e ->
                    HomeScreen(
                        container, lastTeaId = settings.lastTeaId,
                        onManual = { goTab(Tab.BREW) }, onManageFavorites = { goTab(Tab.TEAS) }, onOpenTea = { id -> e.go { nav.navigate("the/$id") } },
                        onRepeat = { tea -> startBrew { container.machine.brew(tea) } },
                    )
                }
                composable(Tab.TEAS.route) { e -> TeasScreen(container, onEdit = { id -> e.go { nav.navigate("edition/$id") } }) }
                composable(Tab.BREW.route) { e ->
                    ManualBrewScreen(
                        connected = machine.connected,
                        onBrew = { t, ml, s -> startBrew { container.machine.brew(manualBrewName, t, ml, s) } },
                        onSaveAsTea = { t, ml, s -> e.go { nav.navigate("edition/0?t=$t&ml=$ml&s=$s") } },
                    )
                }
                composable("the/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments?.getLong("id") ?: 0L
                    TeaPageScreen(
                        container, id, connected = machine.connected,
                        onBack = { e.go { nav.popBackStack() } }, onEdit = { e.go { nav.navigate("edition/$id") } },
                        onBrew = { tea -> startBrew { container.machine.brew(tea) } },
                    )
                }
                composable(
                    "edition/{id}?t={t}&ml={ml}&s={s}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType },
                        navArgument("t") { type = NavType.IntType; defaultValue = TeaDraft().temperature },
                        navArgument("ml") { type = NavType.IntType; defaultValue = TeaDraft().waterMl },
                        navArgument("s") { type = NavType.IntType; defaultValue = TeaDraft().steepSeconds },
                    ),
                ) { e ->
                    val a = e.arguments
                    TeaEditScreen(
                        container, teaId = a?.getLong("id") ?: 0L,
                        draft = TeaDraft(a?.getInt("t") ?: 85, a?.getInt("ml") ?: 250, a?.getInt("s") ?: 120),
                        onDone = { e.go { nav.popBackStack() } },
                        // Supprimé depuis sa page : on revient à la liste, pas sur la page d'un thé disparu.
                        onDeleted = { e.go { if (!nav.popBackStack("thes", inclusive = false)) nav.popBackStack(Tab.HOME.route, inclusive = false) } },
                    )
                }
                composable(Tab.MACHINE.route) { e ->
                    MachineScreen(container, settings, onPair = { e.go { nav.navigate("associer") { launchSingleTop = true } } }, onRequestPermissions = requestPermissions)
                }
                composable("associer") { e ->
                    PairScreen(container, permissionTick, onRequestPermissions = requestPermissions, onDone = { e.go { nav.popBackStack() } })
                }
            }
        }
        if (currentTab != null || route == null) BottomBar(current = tab, onSelect = goTab)
    }
    brew?.let { session ->
        BrewOverlay(
            session = session, machine = machine,
            // Dans le scope de l'app : l'envoi va au bout même si l'écran est recréé entre-temps.
            onStop = { container.appScope.async { container.machine.cancelBrew() }.await() },
            onRetry = { brewError = container.appScope.async { container.machine.retryBrew() }.await() },
            onEdit = { id -> container.machine.closeBrew(); brewError = null; nav.navigate("edition/$id") },
            onClose = { container.machine.closeBrew(); brewError = null },
        )
    }
    if (brew == null) brewError?.let { msg ->
        AlertDialog(
            onDismissRequest = { brewError = null },
            title = { Text(stringResource(R.string.brew_not_started)) }, text = { Text(msg.asString()) },
            confirmButton = { TextButton(onClick = { brewError = null }) { Text(stringResource(R.string.ok), color = c.ink) } },
            containerColor = c.card, titleContentColor = c.ink, textContentColor = c.muted,
        )
    }
    }
}

@Composable
private fun Header(title: String, state: MachineState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Infusion.colors
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = c.ink, modifier = Modifier.weight(1f))
        // Pastille de connexion : le nom de la machine quand elle répond. Un appui reconnecte ou associe.
        val ok = state.connected
        val (bg, fg) = if (ok) c.accentSoft to c.accentInk else c.heatSoft to c.heatInk
        Row(
            // Hauteur d'au moins 48 dp pour le doigt.
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(99.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
            Spacer(Modifier.width(7.dp))
            Text(
                when (state.connection) {
                    MachineState.Connection.NONE -> stringResource(R.string.pill_pair)
                    MachineState.Connection.CONNECTING -> stringResource(R.string.pill_connecting)
                    MachineState.Connection.OFFLINE -> stringResource(R.string.pill_offline)
                    MachineState.Connection.CONNECTED -> state.name
                },
                style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1,
            )
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = Infusion.colors
    val display = Infusion.navDisplay
    Column(Modifier.fillMaxWidth().background(c.card).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { t ->
                val on = t == current
                val label = stringResource(t.label)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (on && display == NavDisplay.TEXT) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(t) }
                        .semantics { contentDescription = label }
                        .padding(vertical = if (display == NavDisplay.TEXT) 12.dp else 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (display != NavDisplay.TEXT) {
                        val big = display == NavDisplay.ICONS
                        Box(
                            Modifier.width(if (big) 56.dp else 46.dp).height(if (big) 36.dp else 28.dp).clip(RoundedCornerShape(99.dp))
                                .background(if (on) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(t.icon), contentDescription = null, tint = if (on) c.accentInk else c.muted, modifier = Modifier.size(if (big) 24.dp else 21.dp))
                        }
                    }
                    if (display != NavDisplay.ICONS) {
                        Spacer(Modifier.height(if (display == NavDisplay.BOTH) 4.dp else 0.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) (if (display == NavDisplay.TEXT) c.accentInk else c.ink) else c.muted,
                        )
                    }
                }
            }
        }
    }
}
