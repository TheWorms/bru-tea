package fr.theworms.infusion.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.net.toUri
import fr.theworms.infusion.BuildConfig
import fr.theworms.infusion.R
import fr.theworms.infusion.domain.catching
import fr.theworms.infusion.domain.toHex
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.text.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class UpdateStatus(
    val current: String = BuildConfig.VERSION_NAME,
    val available: String? = null,
    val checking: Boolean = false,
    /** 0..100 pendant le téléchargement. */
    val progress: Int? = null,
    /** Contrôle du fichier téléchargé (empreinte, paquet, version). */
    val verifying: Boolean = false,
    /** Fichier confié à Android, installation en cours. */
    val installing: Boolean = false,
    val error: UiText? = null,
    /** Échec de la dernière installation, en quelques mots pour le bouton de l'accueil (null : pas d'échec). */
    val failure: UiText? = null,
    val checkedAt: Long = 0,
)

/**
 * Mises à jour depuis les Releases GitHub de TheWorms/bru-tea : Infusion.<version>.apk et son .sha256 (obligatoire).
 * L'APK téléchargée est vérifiée (empreinte, nom de paquet, numéro de version) puis installée par une
 * session PackageInstaller : pas d'écran « Ouvrir » d'Android à la fin. Avec UPDATE_PACKAGES_WITHOUT_USER_ACTION,
 * Android ne redemande plus de confirmation une fois qu'Infusion s'est installée elle-même. Il refuse toute
 * autre signature.
 */
class Updater(
    private val context: Context,
    private val http: OkHttpClient,
    /** Attend qu'aucune infusion ne soit en cours : l'installation tue l'app, donc son suivi. */
    private val waitForIdleMachine: suspend () -> Unit = {},
) {
    private val _status = MutableStateFlow(UpdateStatus())
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** Fenêtre de confirmation demandée par Android, à ouvrir une fois par MainActivity (au premier plan). */
    private val _confirmation = MutableStateFlow<Intent?>(null)
    val confirmation: StateFlow<Intent?> = _confirmation.asStateFlow()
    private val dir = File(context.cacheDir, "updates")
    private val installer get() = context.packageManager.packageInstaller

    /** Un seul téléchargement à la fois (un double appui en lançait deux sur le même fichier). */
    private val busy = Mutex()
    @Volatile private var sessionId: Int? = null
    /** Dernière release trouvée (adresses de l'APK et de son empreinte) et heure du dernier appel à GitHub. */
    @Volatile private var release: GithubRelease? = null
    @Volatile private var lastGithubCall = 0L
    @Volatile private var confirmationShownAt = 0L

    /**
     * Demande à GitHub la dernière release. Au plus un appel toutes les 15 minutes (60 par heure sans compte) ;
     * `force` (bouton « Vérifier ») passe outre. GitHub injoignable : « Pas de connexion au canal de mise à jour ».
     */
    suspend fun check(force: Boolean = false) = withContext(Dispatchers.IO) {
        val current = Version.parse(BuildConfig.VERSION_NAME) ?: return@withContext
        val now = System.currentTimeMillis()
        if (!force && !GithubReleases.mayCall(lastGithubCall, now)) return@withContext
        lastGithubCall = now
        _status.update { it.copy(checking = true, error = null) }
        val latest = catching { fetchLatestRelease() }
        _status.update {
            latest.fold(
                onSuccess = { r ->
                    release = r?.takeIf { x -> x.version > current }
                    it.copy(checking = false, available = release?.version?.toString(), checkedAt = System.currentTimeMillis())
                },
                onFailure = { _ -> it.copy(checking = false, error = text(R.string.update_offline)) },
            )
        }
    }

    /** `releases/latest` : la release, null s'il n'y en a pas encore (404) ; toute autre erreur est levée. */
    private fun fetchLatestRelease(): GithubRelease? {
        val req = Request.Builder().url(GithubReleases.LATEST_URL).header("Accept", "application/vnd.github+json").build()
        return http.newCall(req).execute().use { resp ->
            when {
                resp.code == 404 -> null
                !resp.isSuccessful -> throw IOException("HTTP ${resp.code}")
                else -> GithubReleases.parseLatest(resp.body?.string().orEmpty())
            }
        }
    }

    /** À chaque ouverture de l'app (au plus une fois par minute), sauf pendant une installation. */
    suspend fun checkIfStale(maxAgeMs: Long = 60_000L) {
        val s = _status.value
        if (!s.checking && s.progress == null && !s.verifying && !s.installing && System.currentTimeMillis() - s.checkedAt > maxAgeMs) check()
    }

    /** Télécharge, vérifie puis installe ; un second appui pendant ce temps est ignoré. */
    suspend fun downloadAndInstall() {
        if (!busy.tryLock()) return
        try {
            val file = download() ?: return
            // Infusion lancée pendant le téléchargement : on installe seulement une fois le thé prêt.
            waitForIdleMachine()
            install(file)
        } finally {
            busy.unlock()
        }
    }

    /** Télécharge et vérifie la version disponible ; renvoie le fichier prêt, ou null (erreur dans le statut). */
    private suspend fun download(): File? = withContext(Dispatchers.IO) {
        val r = release ?: return@withContext null
        val v = r.version
        _status.update { it.copy(progress = 0, error = null, failure = null) }
        val out = File(dir, "Infusion.$v.apk")
        val result = catching {
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val digest = MessageDigest.getInstance("SHA-256")
            http.newCall(Request.Builder().url(r.apkUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw UpdateRejected(text(R.string.update_http, resp.code))
                val body = resp.body ?: throw UpdateRejected(text(R.string.update_empty))
                val total = body.contentLength().takeIf { it > 0 }
                out.outputStream().use { os ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n); digest.update(buf, 0, n); done += n
                            if (total != null) _status.update { it.copy(progress = (done * 100 / total).toInt()) }
                        }
                    }
                }
            }
            _status.update { it.copy(progress = null, verifying = true) }
            // L'empreinte publiée est obligatoire : sans elle, on ne sait pas ce qu'on installerait.
            val expected = expectedSha256(fetchText(r.sha256Url)) ?: throw UpdateRejected(text(R.string.update_no_hash))
            if (expected != digest.digest().toHex()) throw UpdateRejected(text(R.string.update_bad_hash))
            val info = context.packageManager.getPackageArchiveInfo(out.path, PackageManager.PackageInfoFlags.of(0))
                ?: throw UpdateRejected(text(R.string.update_unreadable))
            if (info.packageName != context.packageName) throw UpdateRejected(text(R.string.update_wrong_app))
            if (info.longVersionCode <= BuildConfig.VERSION_CODE) throw UpdateRejected(text(R.string.update_not_newer))
            out
        }
        val e = result.exceptionOrNull()
        if (e != null) out.delete()
        _status.update {
            it.copy(
                progress = null, verifying = false,
                error = e?.let { x -> if (x is UpdateRejected) x.uiText else if (x is IOException) text(R.string.update_offline) else text(R.string.error_unexpected) },
                failure = e?.let { x -> if (x is IOException) text(R.string.update_short_offline) else text(R.string.update_short_rejected) },
            )
        }
        result.getOrNull()
    }

    /** L'autorisation d'installer n'a pas été donnée dans le réglage d'Android. */
    fun permissionRefused() = _status.update {
        it.copy(error = text(R.string.update_permission), failure = text(R.string.update_short_permission))
    }

    /** Android doit autoriser Infusion à installer des applications (réglage demandé une fois). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /**
     * Confie l'APK vérifiée à une session d'installation. Sans confirmation si Infusion est déjà son propre
     * installateur ; sinon Android demande une fois « Mettre à jour cette appli ? ».
     */
    private suspend fun install(file: File) = withContext(Dispatchers.IO) {
        _status.update { it.copy(installing = true, error = null, failure = null) }
        abandonOldSessions()
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        var id: Int? = null
        catching {
            val created = installer.createSession(params).also { id = it; sessionId = it }
            installer.openSession(created).use { session ->
                session.openWrite("Infusion.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
                // Mutable : Android ajoute le résultat aux extras ; l'intent vise notre récepteur, non exporté.
                val pending = PendingIntent.getBroadcast(context, created, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
            confirmationShownAt = 0L
            file.delete()
        }.onFailure {
            // La session n'a pas pu être confiée à Android : on la libère (sinon elle resterait sur le téléphone).
            id?.let { s -> runCatching { installer.abandonSession(s) } }
            sessionId = null
            onInstallFailed(text(R.string.update_start_failed))
        }
    }

    /** Sessions laissées par une installation précédente (app tuée en route) : libérées. */
    fun abandonOldSessions() {
        runCatching { installer.mySessions }.getOrNull()?.forEach { info ->
            if (info.sessionId != sessionId || !_status.value.installing) runCatching { installer.abandonSession(info.sessionId) }
        }
    }

    /**
     * Retour dans l'app : une installation dont la session a disparu (refusée, échouée sans retour), ou dont la
     * fenêtre d'Android a été quittée sans répondre depuis plus d'une minute, ne bloque plus le bouton.
     */
    fun onForeground() {
        // Copie de l'APK encore en cours dans la session : rien à conclure.
        if (!_status.value.installing || busy.isLocked) return
        val id = sessionId
        val info = id?.let { runCatching { installer.getSessionInfo(it) }.getOrNull() }
        val abandoned = confirmationShownAt > 0 && System.currentTimeMillis() - confirmationShownAt > CONFIRMATION_GRACE_MS
        if (info == null || abandoned) {
            sessionId = null
            _status.update { it.copy(installing = false, failure = text(R.string.update_short_install_failed)) }
        }
    }

    fun onConfirmationNeeded(confirm: Intent) { _confirmation.value = confirm }

    /** Prend la fenêtre de confirmation à ouvrir : une seule fois (deux fenêtres font échouer l'installation). */
    fun takeConfirmation(): Intent? = _confirmation.getAndUpdate { null }?.also { confirmationShownAt = System.currentTimeMillis() }

    fun onInstalled() = _status.update { it.copy(installing = false, available = null, error = null, failure = null) }

    fun onInstallFailed(message: UiText, short: UiText = text(R.string.update_short_install_failed)) {
        _confirmation.value = null
        sessionId = null
        _status.update { it.copy(installing = false, error = message, failure = short) }
    }

    private fun fetchText(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string()?.take(512) else null }
    }.getOrNull()

    companion object {
        private const val CONFIRMATION_GRACE_MS = 60_000L

        /** Empreinte du fichier .sha256 (« <64 hexa>  Infusion.x.y.z.apk ») ; null si absente ou mal formée. */
        fun expectedSha256(file: String?): String? =
            file?.trim()?.substringBefore(' ')?.substringBefore('\t')?.lowercase()?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }
}

/** APK écartée (téléphone, serveur ou vérification) : le message dit pourquoi. */
private class UpdateRejected(val uiText: UiText) : Exception()
