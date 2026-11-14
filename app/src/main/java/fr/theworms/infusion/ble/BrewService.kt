package fr.theworms.infusion.ble

import fr.theworms.infusion.text.resolve
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fr.theworms.infusion.InfusionApp
import fr.theworms.infusion.MainActivity
import fr.theworms.infusion.R
import fr.theworms.infusion.domain.MachineLimits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Infusion suivie hors de l'app : service au premier plan (type « appareil connecté ») qui garde la
 * liaison et affiche « Infusion en cours » (étape, temps restant, demande de la machine), puis « Thé prêt »
 * ou l'échec si l'app n'est pas à l'écran. Lancé par [start] quand une infusion commence ; s'arrête seul.
 */
class BrewService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var machine: MachineController
    private var collector: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        machine = (application as InfusionApp).container.machine
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { machine.cancelBrew() }
            return START_NOT_STICKY
        }
        // Une infusion commence (ou reprend) : la fin de la précédente (« Thé prêt », « Infusion arrêtée ») est retirée.
        getSystemService(NotificationManager::class.java).cancel(DONE_ID)
        // Toujours au premier plan d'abord (Android l'exige après startForegroundService), même si
        // l'infusion a déjà échoué : la première valeur suivie ci-dessous arrête alors le service.
        val session = machine.brew.value
        val first = if (session != null && !session.finished) ongoing(session, null)
            else NotificationCompat.Builder(this, CHANNEL_ONGOING).setSmallIcon(R.drawable.ic_leaf).setContentTitle(getString(R.string.notif_brewing_placeholder)).setSilent(true).build()
        startForeground(ONGOING_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        collector?.cancel()
        collector = scope.launch {
            // collectLatest : un nouvel état annule l'attente de la fin du décompte (ci-dessous).
            combine(machine.brew, machine.state) { b, s -> b to s }.collectLatest { (b, s) ->
                val nm = getSystemService(NotificationManager::class.java)
                when {
                    b == null -> finish(null)
                    // Infusion close : le service s'arrête (notification de fin si l'app n'est pas à l'écran).
                    b.finished -> finish(b)
                    // Liaison perdue pendant l'infusion : on reste au premier plan, le temps de la retrouver.
                    b.linkDown -> nm.notify(ONGOING_ID, reconnecting(b))
                    else -> {
                        val alert = if (s.connected) MachineTexts.alert(s.status, s.subStatus) else null
                        nm.notify(ONGOING_ID, ongoing(b, alert))
                        // La machine se tait en infusant : rien d'autre ne republie la notification, dont le décompte
                        // passerait sous zéro. À sa fin, elle passe au texte d'étape.
                        val left = b.steepEnd - System.currentTimeMillis()
                        if (alert == null && left > 0) {
                            delay(left + 1_000)
                            nm.notify(ONGOING_ID, ongoing(b, null))
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Fin de l'infusion : le service s'arrête ; si l'app n'est pas à l'écran, une notification dit la suite. */
    private fun finish(session: BrewSession?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (session != null && !machine.inForeground) {
            val nm = getSystemService(NotificationManager::class.java)
            val detail = session.error?.resolve(this) ?: session.message?.resolve(this) ?: getString(R.string.notif_enjoy)
            // Arrêt demandé (souvent depuis cette notification) : « Infusion arrêtée », sans son ni bannière.
            val stopped = session.problem == BrewProblem.STOPPED
            val n = NotificationCompat.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_leaf)
                .setContentTitle(getString(if (session.error == null && !stopped) R.string.notif_ready_title else R.string.notif_stopped_title, session.name))
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .setSilent(stopped)
                .setCategory(if (session.error == null) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_ERROR)
                .build()
            nm.notify(DONE_ID, n)
        }
        stopSelf()
    }

    private fun reconnecting(b: BrewSession): Notification = NotificationCompat.Builder(this, CHANNEL_ONGOING)
        .setSmallIcon(R.drawable.ic_leaf)
        .setContentTitle(getString(R.string.notif_brewing_title, b.name))
        .setContentText(getString(R.string.notif_reconnecting))
        .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
        .setContentIntent(openApp())
        .setProgress(0, 0, true)
        .build()

    private fun ongoing(b: BrewSession, alert: MachineAlert?): Notification {
        val phase = if (b.phase == BrewPhase.CUP) BrewPhase.SENDING else b.phase
        val builder = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_leaf)
            .setContentTitle(getString(R.string.notif_brewing_title, b.name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(0, getString(R.string.action_stop), PendingIntent.getService(this, 1, Intent(this, BrewService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        // Décompte local depuis l'acceptation (la machine se tait en infusant), recalé sur le début réel de l'infusion.
        val steepEnd = b.steepEnd
        when {
            alert != null -> builder.setContentText(getString(R.string.notif_alert, alert.title.resolve(this), alert.detail.resolve(this)))
            steepEnd > System.currentTimeMillis() -> {
                // Décompte affiché par Android, sans réveiller l'app chaque seconde.
                builder.setContentText(getString(R.string.notif_steeping, MachineLimits.steepLabel(b.steepSeconds)))
                    .setUsesChronometer(true).setChronometerCountDown(true).setWhen(steepEnd).setShowWhen(true)
            }
            else -> builder.setContentText(getString(R.string.notif_phase, getString(phase.label)))
        }
        val progress = when (phase) {
            BrewPhase.SENDING, BrewPhase.CUP -> 5
            BrewPhase.FILLING -> 20
            BrewPhase.STEEPING -> 60
            BrewPhase.SERVING -> 90
            BrewPhase.DONE -> 100
        }
        builder.setProgress(100, progress, phase == BrewPhase.SENDING || phase == BrewPhase.FILLING)
        return builder.build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ONGOING = "infusion_en_cours"
        private const val CHANNEL_DONE = "infusion_finie"
        private const val ONGOING_ID = 41
        private const val DONE_ID = 42
        private const val ACTION_STOP = "fr.theworms.infusion.ARRETER"

        /** Appelé quand une infusion commence (l'app est alors à l'écran : Android l'autorise). */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, BrewService::class.java)) }
                .onFailure { android.util.Log.w("Infusion", "service d'infusion non lancé : ${it.message}") }
        }

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.channel_brewing), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_brewing_desc)
            })
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_done_desc)
            })
        }
    }
}
