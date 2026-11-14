package fr.theworms.infusion.ble

import fr.theworms.infusion.text.text
import fr.theworms.infusion.text.raw
import fr.theworms.infusion.text.plural
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.R
import fr.theworms.infusion.data.SettingsStore
import fr.theworms.infusion.data.machineDisplayName
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.data.TeaDao
import fr.theworms.infusion.domain.MachineLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId

/** Ce que l'interface sait de la machine. */
data class MachineState(
    val address: String = "",
    val name: String = "Maker One",
    val connection: Connection = Connection.NONE,
    val firmware: String? = null,
    val status: Int? = null,
    val subStatus: Int? = null,
    /** Dernier problème à montrer (appairage, refus de la machine…). */
    val problem: UiText? = null,
    /** Langue, unités et nombre de préréglages lus sur la machine, si elle les donne. */
    val setup: MachineSetup? = null,
) {
    enum class Connection { NONE, OFFLINE, CONNECTING, CONNECTED }
    val connected get() = connection == Connection.CONNECTED
    val supportsNickname get() = MakerOneProtocol.firmwareAtLeast(firmware, MakerOneProtocol.FW_NICKNAME)
    val supportsHalfWater get() = MakerOneProtocol.firmwareAtLeast(firmware, MakerOneProtocol.FW_HALF_WATER)
    val supportsExtended get() = MakerOneProtocol.firmwareAtLeast(firmware, MakerOneProtocol.FW_EXTENDED)
}

/**
 * Une infusion, suivie d'après l'état envoyé par la machine. La machine se tait pendant qu'elle infuse :
 * le décompte part de l'acceptation (réponse 65 = 0) et se recale sur le début réel de l'infusion
 * (sous-état 26) quand il arrive. Les règles de suivi sont dans [BrewRules].
 */
data class BrewSession(
    val name: String,
    val temperature: Int,
    val waterMl: Int,
    val steepSeconds: Int,
    val phase: BrewPhase = BrewPhase.SENDING,
    val message: UiText? = null,
    /** Point de départ du décompte. */
    val steepStartedAt: Long = 0,
    val error: UiText? = null,
    val finished: Boolean = false,
    /** Thé de la liste, pour « Modifier le thé » quand la machine refuse ses réglages. */
    val teaId: Long? = null,
    val problem: BrewProblem? = null,
    /** La machine a accepté la commande d'infusion. */
    val accepted: Boolean = false,
    val acceptedAt: Long = 0,
    /** Un état « infusion » (5) a été vu depuis l'acceptation. */
    val seenBrewing: Boolean = false,
    /** Liaison perdue pendant l'infusion : la session reste ouverte en attendant de la retrouver. */
    val linkDown: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Sans nouvelles de la machine passé cette heure, l'infusion est tenue pour finie (durée + 5 min). */
    val deadline: Long get() = acceptedAt + (steepSeconds + MARGIN_S) * 1000L

    companion object {
        /** Remplissage, service et marge, en plus de la durée d'infusion. */
        const val MARGIN_S = 300
        /** Juste après l'acceptation, la machine peut encore se dire en veille : la clôture attend 10 s. */
        const val STANDBY_GRACE_MS = 10_000L
    }
}

/** Pourquoi une infusion n'a pas abouti : décide des boutons de la fenêtre d'infusion. */
enum class BrewProblem {
    /** Refus au départ, machine pas prête (eau, couvercle, tasse) : relancer quand c'est réglé. */
    NOT_READY,
    /** Réglages refusés par le firmware : modifier le thé. */
    REFUSED,
    /** La machine signale une erreur en cours de route. */
    MACHINE_ERROR,
    /** Machine injoignable : rien n'est parti. */
    OFFLINE,
    /** Commande envoyée sans réponse de la machine : elle infuse peut-être (« Réessayer » relit son état d'abord). */
    NO_ANSWER,
}

/** Règles de suivi d'une infusion, sans Android (testées dans BrewRulesTest). */
object BrewRules {
    /** États qui, après l'acceptation, clôturent l'infusion comme chez BRU : veille, menu, menu Bluetooth. */
    private val CLOSING = setOf(MachineTexts.STANDBY, MachineTexts.MENU, MachineTexts.BLUETOOTH_MENU)

    /** Trame d'état (valide) reçue pendant une session. */
    fun onStatus(b: BrewSession, status: Int, sub: Int, now: Long): BrewSession {
        if (b.finished) return b
        val phase = MachineTexts.phase(sub)
        var next = b.copy(message = MachineTexts.message(sub), linkDown = false, seenBrewing = b.seenBrewing || status == MachineTexts.BREWING)
        when {
            phase == BrewPhase.STEEPING && b.phase != BrewPhase.STEEPING -> next = next.copy(phase = phase, steepStartedAt = now)
            // L'étape « fin » ne s'affiche qu'à la clôture (ci-dessous), pas sur l'écran d'une infusion précédente.
            phase != null && phase != BrewPhase.DONE && phase.ordinal > b.phase.ordinal -> next = next.copy(phase = phase)
            sub in 120..122 && b.phase < BrewPhase.FILLING -> next = next.copy(phase = BrewPhase.CUP)
        }
        // Fin d'infusion (« bonne dégustation », veille, menu) : seulement pour cette infusion-ci, acceptée
        // puis vue en cours ou acceptée depuis plus de 10 s (sinon c'est l'écran de l'infusion précédente).
        val settled = b.accepted && (next.seenBrewing || now - b.acceptedAt > BrewSession.STANDBY_GRACE_MS)
        if (phase == BrewPhase.DONE && settled) return next.copy(phase = BrewPhase.DONE, finished = true, message = null)
        if (status == MachineTexts.ERROR) {
            return next.copy(error = MachineTexts.message(sub) ?: text(R.string.msg_error), problem = BrewProblem.MACHINE_ERROR, finished = true)
        }
        if (settled && status in CLOSING) {
            return next.copy(phase = BrewPhase.DONE, finished = true, message = null)
        }
        return next
    }

    /** Sans nouvelles au-delà de l'échéance : clôture neutre (pas d'erreur, donc pas de bip). */
    fun onTick(b: BrewSession, now: Long): BrewSession =
        if (b.finished || !b.accepted || now <= b.deadline) b
        else b.copy(
            phase = BrewPhase.DONE, finished = true, linkDown = false,
            message = text(if (b.linkDown) R.string.brew_tracking_interrupted else R.string.brew_done_no_news),
        )

    /** Liaison perdue : une infusion acceptée reste ouverte (avant l'acceptation, brew() conclut seul). */
    fun onLinkLost(b: BrewSession): BrewSession =
        if (b.finished || !b.accepted) b else b.copy(linkDown = true, message = text(R.string.notif_reconnecting))

    /**
     * Acceptation par la machine (réponse 65 = 0, ou état « infusion » relu après une absence de réponse).
     * Une session close par une erreur de la machine n'est pas rouverte ; sinon elle repart du remplissage.
     */
    fun onAccepted(b: BrewSession, now: Long, brewing: Boolean = false): BrewSession =
        if (b.finished && b.problem == BrewProblem.MACHINE_ERROR) b
        else b.copy(
            accepted = true, acceptedAt = now, steepStartedAt = now, seenBrewing = b.seenBrewing || brewing,
            phase = BrewPhase.FILLING, finished = false, error = null, problem = null, linkDown = false, message = null,
        )
}

/**
 * Pilote la machine au-dessus de BleTransport : connexion dans l'ordre de l'app d'origine (heure,
 * abonnement, relève d'état toutes les 3 s), infusion, écriture des 10 préréglages, nom, réglages.
 *
 * Écritures vers la machine : seulement sur un geste de l'utilisateur, sauf la mise à l'heure et la lecture
 * des réglages (42), faites à la première connexion du processus (comme l'app d'origine à l'ouverture).
 */
class MachineController(
    private val transport: BleTransport,
    private val settings: SettingsStore,
    private val teas: TeaDao,
    private val scope: CoroutineScope,
    /** Texte dans la langue de l'app, pour ce qui est enregistré tel quel (note d'un thé repris de la machine). */
    private val resolve: (UiText) -> String,
    /** Langue de l'app : envoyée à la machine quand elle ne donne pas la sienne (commande 42 sans réponse). */
    private val appLanguage: () -> MachineLanguage = { MachineLanguage.FR },
) {
    private val _state = MutableStateFlow(MachineState())
    val state: StateFlow<MachineState> = _state.asStateFlow()

    private val _brew = MutableStateFlow<BrewSession?>(null)
    val brew: StateFlow<BrewSession?> = _brew.asStateFlow()

    /** Résultat du dernier changement de langue ou d'unités, gardé ici : changer de langue recrée l'écran. */
    private val _setupMessage = MutableStateFlow<UiText?>(null)
    val setupMessage: StateFlow<UiText?> = _setupMessage.asStateFlow()

    /** Bluetooth du téléphone allumé ou non, en direct. */
    val bluetoothOn: StateFlow<Boolean> = transport.bluetoothOn

    private val responses = MutableSharedFlow<Incoming>(extraBufferCapacity = 32)
    private val connectLock = Mutex()
    private var poller: Job? = null
    private var reconnector: Job? = null
    private var backgroundJob: Job? = null
    private var sessionWatch: Job? = null
    /** Dernière trame reçue (hors infusion, une minute de silence vaut liaison perdue). */
    @Volatile private var lastFrameAt = 0L
    /** Mise à l'heure et lecture des réglages déjà faites dans ce processus. */
    @Volatile private var setupRead = false
    @Volatile private var setupAttempts = 0
    @Volatile private var greeted = false
    /** La machine ne reconnaît plus ce téléphone : plus de tentative automatique, il faut l'associer de nouveau. */
    @Volatile private var pairingLost = false
    @Volatile private var foreground = false

    /** L'app est à l'écran (sinon, seule une infusion en cours garde la liaison). */
    val inForeground get() = foreground

    private fun sessionOpen() = _brew.value?.finished == false

    init {
        scope.launch {
            val s = settings.settings.first()
            _state.update { it.copy(address = s.machineAddress, name = s.machineDisplayName, firmware = s.machineFirmware.ifBlank { null },
                connection = if (s.machineAddress.isBlank()) MachineState.Connection.NONE else MachineState.Connection.OFFLINE) }
        }
        scope.launch { transport.incoming.collect { onFrame(MakerOneProtocol.parse(it)) } }
        scope.launch {
            transport.link.collect { link ->
                if (link is Link.Lost) linkLost()
            }
        }
    }

    /**
     * Liaison perdue (coupure Bluetooth, relèves impossibles, machine muette) : une infusion en cours reste
     * ouverte et attend la liaison, qui est retentée (avant : il fallait se reconnecter à la main).
     */
    private fun linkLost() {
        poller?.cancel()
        _state.update { it.copy(connection = MachineState.Connection.OFFLINE, status = null, subStatus = null) }
        _setupMessage.value = null
        _brew.update { it?.let(BrewRules::onLinkLost) }
        startReconnecting()
    }

    fun clearSetupMessage() { _setupMessage.value = null }

    fun hasPermissions() = transport.hasPermissions()
    fun isBluetoothOn() = transport.isBluetoothOn()
    fun scan() = transport.scan()

    /** Appelé quand l'app revient au premier plan. */
    fun onForeground() {
        foreground = true
        backgroundJob?.cancel()
        scope.launch { if (!ensureConnected()) startReconnecting() }
    }

    /**
     * Retente la liaison tant que l'app est à l'écran (ou qu'une infusion est ouverte) : après 2, 5, 10, 20 s,
     * puis toutes les 30 s. Seulement vers une machine associée dans Android (sinon chaque essai ouvrirait la
     * fenêtre d'appairage), et plus du tout si elle ne reconnaît plus ce téléphone.
     */
    private fun startReconnecting() {
        if (reconnector?.isActive == true) return
        reconnector = scope.launch {
            val waits = listOf(2_000L, 5_000L, 10_000L, 20_000L)
            var attempt = 0
            while (isActive) {
                delay(waits.getOrElse(attempt) { 30_000L })
                attempt++
                if (!foreground && !sessionOpen()) break
                val address = _state.value.address
                if (address.isBlank() || _state.value.connected || pairingLost) break
                if (!transport.hasPermissions() || !transport.isBluetoothOn()) continue
                if (!transport.isBonded(address)) {
                    _state.update { it.copy(problem = text(R.string.problem_pairing_lost)) }
                    break
                }
                if (ensureConnected()) break
            }
        }
    }

    /** Appelé quand l'app passe en arrière-plan : on garde le lien pendant une infusion, puis on le coupe. */
    fun onBackground() {
        foreground = false
        backgroundJob?.cancel()
        backgroundJob = scope.launch {
            delay(5_000)
            while (sessionOpen()) delay(5_000)
            if (!foreground) disconnect()
        }
    }

    fun disconnect() {
        poller?.cancel()
        reconnector?.cancel()
        transport.disconnect()
        if (_state.value.address.isNotBlank()) _state.update { it.copy(connection = MachineState.Connection.OFFLINE, status = null, subStatus = null) }
    }

    /** Associe une machine trouvée par la recherche (la première fois, Android demande de confirmer). */
    suspend fun pair(found: FoundMachine): Boolean {
        val ok = connectTo(found.address, pairing = true, userInitiated = true)
        if (ok) {
            pairingLost = false
            settings.setMachine(found.address, (transport.link.value as? Link.Ready)?.name ?: found.name)
            _state.update { it.copy(address = found.address, name = settings.settings.first().machineDisplayName) }
            // L'app d'origine sort ensuite du menu Bluetooth de la machine par trois « Retour ».
            if (_state.value.status == MachineTexts.BLUETOOTH_MENU) repeat(3) { transport.write(MakerOneProtocol.button(MakerOneProtocol.BUTTON_BACK)); delay(300) }
        }
        return ok
    }

    suspend fun forget() {
        disconnect()
        settings.setMachine("", "")
        _state.value = MachineState()
    }

    /** `userInitiated` : demandé par un geste (pastille, « Se connecter ») : mise à l'heure et relecture des réglages. */
    suspend fun ensureConnected(userInitiated: Boolean = false): Boolean {
        val address = _state.value.address.ifBlank { settings.settings.first().machineAddress }
        if (address.isBlank()) return false
        if (_state.value.connected) return true
        if (userInitiated) pairingLost = false
        // Tentative automatique (ouverture, infusion, reconnecteur) : pas si l'appairage est perdu ou si la
        // machine n'est plus liée à Android, sinon la fenêtre d'appairage reviendrait à chaque ouverture.
        else if (pairingLost || !transport.isBonded(address)) return false
        return connectTo(address, pairing = false, userInitiated = userInitiated)
    }

    private suspend fun connectTo(address: String, pairing: Boolean, userInitiated: Boolean): Boolean = connectLock.withLock {
        if (_state.value.connected && _state.value.address == address) return true
        if (!transport.hasPermissions()) { _state.update { it.copy(problem = text(R.string.problem_permission)) }; return false }
        if (!transport.isBluetoothOn()) { _state.update { it.copy(problem = text(R.string.problem_bluetooth_off)) }; return false }
        _state.update { it.copy(connection = MachineState.Connection.CONNECTING, problem = null) }
        try {
            if (!transport.connect(address)) {
                val reason = (transport.link.value as? Link.Lost)?.reason ?: text(R.string.link_no_answer)
                _state.update { it.copy(connection = MachineState.Connection.OFFLINE, problem = text(R.string.problem_unreachable, reason)) }
                return false
            }
            val link = transport.link.value as? Link.Ready
            val fw = link?.firmware
            if (!fw.isNullOrBlank()) settings.setMachineFirmware(fw)
            // Ordre de l'app d'origine : heure, puis abonnement (peut déclencher l'appairage), puis relève d'état.
            val greet = pairing || userInitiated || !greeted
            if (greet) transport.write(MakerOneProtocol.setTime(localEpochSeconds()))
            when (transport.subscribe()) {
                WriteResult.OK -> Unit
                WriteResult.PairingNeeded -> {
                    transport.disconnect()
                    if (!pairing) pairingLost = true
                    _state.update {
                        it.copy(connection = MachineState.Connection.OFFLINE, problem = if (pairing)
                            text(R.string.problem_pairing_first) else text(R.string.problem_pairing_lost))
                    }
                    return false
                }
                WriteResult.Failed -> {
                    transport.disconnect()
                    _state.update { it.copy(connection = MachineState.Connection.OFFLINE, problem = text(R.string.problem_subscribe)) }
                    return false
                }
            }
            // La machine ne renvoie que son nom Bluetooth (générique) : gardé à part, il ne remplace jamais
            // le nom choisi dans l'app (comme l'app de BRU, qui garde le sien sur le téléphone).
            link?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { settings.setMachineBleName(it) }
            val machineName = settings.settings.first().machineDisplayName
            _state.update { it.copy(address = address, connection = MachineState.Connection.CONNECTED, firmware = fw ?: it.firmware, name = machineName, problem = null) }
            startPolling()
            if (greet) greeted = true
            // Réglages (42) relus tant qu'ils n'ont pas abouti (3 essais au plus par processus) ou sur un geste.
            if (greet || (!setupRead && setupAttempts < MAX_SETUP_ATTEMPTS)) {
                setupAttempts++
                scope.launch { if (readSetup() != null) setupRead = true }
            }
            // Liaison retrouvée pendant une infusion (machine muette) : la session n'est plus « liaison perdue ».
            _brew.update { b -> if (b != null && !b.finished && b.linkDown) b.copy(linkDown = false, message = null) else b }
            // App passée en arrière-plan pendant la connexion, sans infusion : on ne garde pas la liaison.
            if (!foreground && !pairing && !sessionOpen()) {
                poller?.cancel()
                transport.disconnect()
                _state.update { it.copy(connection = MachineState.Connection.OFFLINE, status = null, subStatus = null) }
                return false
            }
            true
        } catch (e: CancellationException) {
            // Écran quitté pendant la connexion : pas de liaison à moitié ouverte ni d'état « Connexion… » figé.
            transport.disconnect()
            _state.update { it.copy(connection = if (it.address.isBlank()) MachineState.Connection.NONE else MachineState.Connection.OFFLINE) }
            throw e
        }
    }

    /**
     * Relève d'état toutes les 3 s. La machine se tait pendant qu'elle infuse : pendant une infusion, son
     * silence est attendu (la session se clôt à son échéance) ; hors infusion, une minute de silence vaut
     * liaison perdue. 3 relèves de suite impossibles à envoyer aussi.
     */
    private fun startPolling() {
        poller?.cancel()
        lastFrameAt = System.currentTimeMillis()
        poller = scope.launch {
            var failures = 0
            while (isActive) {
                failures = if (transport.write(MakerOneProtocol.statusRequest()) == WriteResult.OK) 0 else failures + 1
                val silence = System.currentTimeMillis() - lastFrameAt
                if (failures >= MAX_POLL_FAILURES || (!sessionOpen() && silence > IDLE_SILENCE_MS)) {
                    android.util.Log.i("Infusion", "liaison tenue pour perdue (échecs $failures, silence ${silence / 1000} s)")
                    transport.disconnect()
                    linkLost()
                    break
                }
                delay(POLL_MS)
            }
        }
    }

    /** Clôt une infusion acceptée à son échéance si la machine ne donne plus de nouvelles (liaison tenue ou non). */
    private fun startSessionWatch() {
        sessionWatch?.cancel()
        sessionWatch = scope.launch {
            while (isActive && sessionOpen()) {
                delay(5_000)
                _brew.update { it?.let { b -> BrewRules.onTick(b, System.currentTimeMillis()) } }
            }
        }
    }

    private fun onFrame(f: Incoming) {
        lastFrameAt = System.currentTimeMillis()
        responses.tryEmit(f)
        // Une trame abîmée ne décide de rien (une fausse erreur 13 clôturerait l'infusion).
        if (f is Incoming.Status) {
            _state.update { it.copy(status = f.status, subStatus = f.subStatus) }
            _brew.update { it?.let { b -> BrewRules.onStatus(b, f.status, f.subStatus, System.currentTimeMillis()) } }
        }
    }

    /**
     * Envoie les réglages et lance l'infusion. Renvoie un message d'erreur, ou null si la machine a accepté.
     * Une exception imprévue (stockage, Bluetooth) termine la session avec une erreur, au lieu de la laisser
     * ouverte sans fin.
     */
    suspend fun brew(name: String, temperature: Int, waterMl: Int, steepSeconds: Int, teaId: Long? = null): UiText? = try {
        startBrew(name, temperature, waterMl, steepSeconds, teaId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("Infusion", "infusion interrompue par une erreur", e)
        val why = text(R.string.msg_error)
        _brew.update { it?.copy(error = why, problem = BrewProblem.MACHINE_ERROR, finished = true) }
        why
    }

    private suspend fun startBrew(name: String, temperature: Int, waterMl: Int, steepSeconds: Int, teaId: Long?): UiText? {
        // La fenêtre d'infusion s'ouvre tout de suite, y compris pour dire que rien n'est parti.
        _brew.value = BrewSession(name, temperature, waterMl, MachineLimits.roundSteep(steepSeconds), teaId = teaId)
        if (!ensureConnected(userInitiated = true)) {
            val why = _state.value.problem ?: text(R.string.machine_unreachable)
            _brew.update { it?.copy(error = why, problem = BrewProblem.OFFLINE, finished = true) }
            return why
        }
        val st = _state.value
        val codes = BrewCodes(
            MakerOneProtocol.temperatureCode(temperature),
            MakerOneProtocol.timeCode(steepSeconds),
            MakerOneProtocol.waterCode(waterMl, st.supportsHalfWater),
        )
        val identity = if (st.supportsExtended) BrewIdentity(MakerOneProtocol.installId8(settings.installId()), name) else null
        _brew.update { it?.copy(steepSeconds = MachineLimits.steepDurations[codes.time]) }
        val result = sendAndAwait(MakerOneProtocol.brew(codes, identity)) { it is Incoming.BrewResult || it is Incoming.PresetResult }
        val error = when (result) {
            null -> text(R.string.brew_no_answer)
            is Incoming.BrewResult -> MachineTexts.brewResult(result.code)
            is Incoming.PresetResult -> MachineTexts.presetResult(result.code)?.let { text(R.string.brew_refused_preset, it) }
            else -> null
        }
        val problem = when {
            // Une écriture sans réponse ne prouve pas que rien n'est parti : « pas de réponse ».
            result == null -> BrewProblem.NO_ANSWER
            result is Incoming.BrewResult && result.code == 16 -> BrewProblem.NOT_READY
            else -> BrewProblem.REFUSED
        }
        if (error != null) _brew.update { it?.copy(error = error, problem = problem, finished = true) }
        else {
            _brew.update { it?.let { b -> BrewRules.onAccepted(b, System.currentTimeMillis()) } }
            startSessionWatch()
            if (teaId != null) settings.setLastBrew(teaId)
            delay(300)
            transport.write(MakerOneProtocol.statusRequest())
        }
        return error
    }

    /**
     * « Relancer » ou « Réessayer » : on demande d'abord son état à la machine. Elle infuse (5) après une
     * commande restée sans réponse : on suit cette infusion sans rien renvoyer. Elle infuse autre chose :
     * rien n'est envoyé. Elle ne répond pas : rien n'est renvoyé. Un renvoi n'a lieu qu'après un autre état.
     */
    suspend fun retryBrew(): UiText? {
        val b = _brew.value ?: return null
        if (!ensureConnected(userInitiated = true)) {
            val why = _state.value.problem ?: text(R.string.machine_unreachable)
            _brew.update { it?.copy(error = why) }
            return why
        }
        val st = sendAndAwait(MakerOneProtocol.statusRequest(), timeoutMs = 3_000L) { it is Incoming.Status } as? Incoming.Status
        return when {
            st == null -> text(R.string.brew_no_answer_retry).also { why -> _brew.update { it?.copy(error = why) } }
            st.status == MachineTexts.BREWING && b.problem == BrewProblem.NO_ANSWER -> {
                _brew.update { it?.let { s -> BrewRules.onAccepted(s, System.currentTimeMillis(), brewing = true) } }
                startSessionWatch()
                null
            }
            st.status == MachineTexts.BREWING -> text(R.string.brew_machine_busy).also { why -> _brew.update { it?.copy(error = why) } }
            // Fenêtre fermée (ou autre infusion) pendant la relecture : rien n'est renvoyé.
            _brew.value !== b -> null
            else -> brew(b.name, b.temperature, b.waterMl, b.steepSeconds, b.teaId)
        }
    }

    /** « Arrêter » : la session ne s'arrête que si l'ordre est bien parti vers la machine. */
    suspend fun cancelBrew() {
        val sent = transport.write(MakerOneProtocol.cancel()) == WriteResult.OK
        _brew.update { b ->
            b?.let { if (sent) it.copy(finished = true, message = text(R.string.brew_stop_sent)) else it.copy(message = text(R.string.brew_stop_failed)) }
        }
    }

    fun closeBrew() {
        sessionWatch?.cancel()
        _brew.value = null
    }

    /**
     * Écrit les thés « sur la machine » en positions 1 à 10, dans l'ordre, puis le nombre de préréglages.
     * La machine accuse réception de chacun (elle ne permet pas de les relire). Renvoie un résumé.
     */
    suspend fun syncPresets(): UiText {
        if (!ensureConnected()) return _state.value.problem ?: text(R.string.sync_unreachable)
        val list = teas.onMachine().take(MachineLimits.PRESETS)
        val st = _state.value
        if (list.isEmpty()) { sendSetup(presets = 0); return text(R.string.sync_none) }
        var ok = 0
        val failures = mutableListOf<UiText>()
        list.forEachIndexed { i, tea ->
            val slot = i + 1
            val codes = BrewCodes(MakerOneProtocol.temperatureCode(tea.temperature), MakerOneProtocol.timeCode(tea.steepSeconds), MakerOneProtocol.waterCode(tea.waterMl, st.supportsHalfWater))
            val frame = MakerOneProtocol.presetWrite(slot, codes, tea.name, if (st.supportsExtended) MakerOneProtocol.NULL_TEA_ID else null)
            val r = sendAndAwait(frame) { it is Incoming.PresetResult } as? Incoming.PresetResult
            val problem = if (r == null) text(R.string.sync_no_answer) else MachineTexts.presetResult(r.code)
            // Mise à jour ciblée : une modification du thé faite pendant la synchronisation n'est pas écrasée.
            teas.setMachineSync(tea.id, synced = problem == null, slot = slot)
            if (problem == null) ok++ else failures += text(R.string.sync_failure_item, tea.name, problem)
            delay(200)
        }
        if (ok > 0) sendSetup(presets = ok)
        return when {
            failures.isEmpty() -> plural(R.plurals.sync_done, ok, ok)
            else -> text(R.string.sync_partial, ok, list.size, UiText.Join(failures, " · "))
        }
    }

    /** Résultat de l'essai de lecture des préréglages de la machine. */
    data class ReadResult(val imported: Int, val alreadyKnown: Int, val message: UiText, val unknownReply: String? = null)

    /**
     * Essai : demande à la machine ses préréglages 1 à 10 (lecture seule, rien n'est écrit) et importe
     * ceux qui se comprennent comme thés « sur la machine ». Une réponse inattendue est rendue en
     * hexadécimal, pour comprendre son format.
     */
    suspend fun readMachinePresets(): ReadResult {
        if (!ensureConnected()) return ReadResult(0, 0, _state.value.problem ?: text(R.string.machine_unreachable))
        val known = teas.onMachine().mapNotNull { it.machineSlot }.toSet()
        var imported = 0
        var already = 0
        for (slot in 1..MachineLimits.PRESETS) {
            val reply = sendAndAwait(MakerOneProtocol.presetRead(slot), timeoutMs = 2_000L) {
                it is Incoming.Unknown || it is Incoming.PresetResult
            } ?: return ReadResult(imported, already, if (slot == 1) text(R.string.read_unsupported) else text(R.string.read_done))
            val bytes = (reply as? Incoming.Unknown)?.bytes
            val preset = bytes?.let { MakerOneProtocol.parsePresetReply(it, slot) }
            if (preset == null) {
                val raw = bytes?.let { MakerOneProtocol.hex(it) } ?: "83 · ${(reply as Incoming.PresetResult).code}"
                return ReadResult(imported, already, text(R.string.read_unknown, slot), raw)
            }
            if (slot in known) { already++; continue }
            teas.insert(
                Tea(
                    name = preset.name.take(30), temperature = preset.temperature, waterMl = preset.waterMl, steepSeconds = preset.steepSeconds,
                    notes = resolve(text(R.string.import_note, slot)), onMachine = true, machineSlot = slot, synced = true,
                ),
            )
            imported++
            delay(200)
        }
        val summary = UiText.Join(
            listOf(plural(R.plurals.read_imported, imported, imported), if (already > 0) plural(R.plurals.read_already, already, already) else raw(".")), "",
        )
        return ReadResult(imported, already, summary)
    }

    /**
     * Lit langue, unités et nombre de préréglages de la machine (lecture seule, commande 42) : l'app reprend
     * ses unités ; sa langue est seulement notée (la langue de l'app décide, l'onglet Machine montre l'écart).
     * Null si la machine ne répond pas sous la forme attendue.
     */
    suspend fun readSetup(): MachineSetup? {
        if (!_state.value.connected) return null
        val reply = sendAndAwait(MakerOneProtocol.setupRead(), timeoutMs = 2_000L) { it is Incoming.Unknown && MakerOneProtocol.parseSetupReply(it.bytes) != null }
        val setup = (reply as? Incoming.Unknown)?.let { MakerOneProtocol.parseSetupReply(it.bytes) } ?: return null
        android.util.Log.i("Infusion", "Réglages de la machine : ${MakerOneProtocol.hex((reply as Incoming.Unknown).bytes)} → $setup")
        _state.update { it.copy(setup = setup) }
        settings.setMetric(setup.metric)
        return setup
    }

    /**
     * Change la langue ou les unités de l'écran de la machine sans toucher à son nombre de préréglages
     * (relu avant ; 10 si elle ne le donne pas), puis relit pour confirmer. Renvoie le message à afficher.
     */
    suspend fun changeSetup(language: MachineLanguage? = null, metric: Boolean? = null): UiText =
        applySetup(language, metric).also { _setupMessage.value = it }

    private suspend fun applySetup(language: MachineLanguage?, metric: Boolean?): UiText {
        if (!ensureConnected()) return _state.value.problem ?: text(R.string.machine_unreachable)
        val before = _state.value.setup ?: readSetup()
        val s = settings.settings.first()
        val lang = language?.code ?: before?.languageCode ?: appLanguage().code
        val units = metric ?: before?.metric ?: s.metric
        val presets = before?.presets ?: MachineLimits.PRESETS
        val frame = MakerOneProtocol.setup(lang, units, presets, before?.time ?: 0)
        if (transport.write(frame) != WriteResult.OK) return text(R.string.setup_send_failed)
        // Ce qui vient d'être écrit fait foi jusqu'à la prochaine lecture : une synchronisation ou un
        // changement d'unités ne remet pas l'ancienne langue.
        _state.update { it.copy(setup = MachineSetup(lang, units, before?.time ?: 0, presets)) }
        if (language != null) settings.setMachineLanguage(language.name)
        if (metric != null) settings.setMetric(metric)
        delay(500)
        val after = readSetup()
        return when {
            after == null -> text(R.string.setup_unconfirmed)
            after.languageCode == lang && after.metric == units -> when {
                language != null -> text(R.string.setup_language_ok, text(language.nameRes))
                units -> text(R.string.setup_units_metric_ok)
                else -> text(R.string.setup_units_imperial_ok)
            }
            else -> text(if (language != null) R.string.setup_language_refused else R.string.setup_units_refused)
        }
    }

    /** Fin de synchronisation : `presets` préréglages affichés, langue et unités inchangées (celles de la machine si elle les a données). */
    private suspend fun sendSetup(presets: Int): Boolean {
        val s = settings.settings.first()
        val current = _state.value.setup
        val frame = MakerOneProtocol.setup(current?.languageCode ?: appLanguage().code, current?.metric ?: s.metric, presets, current?.time ?: 0)
        val ok = transport.write(frame) == WriteResult.OK
        if (ok) _state.update { st -> st.copy(setup = st.setup?.copy(presets = presets)) }
        return ok
    }

    /**
     * Renomme la machine (12 caractères) : le nom est gardé dans l'app (la machine ne le renvoie pas) et
     * envoyé à la machine pour son écran quand c'est possible. Renvoie le message à afficher.
     */
    suspend fun rename(name: String): UiText {
        val n = name.trim().take(MachineLimits.NAME_MAX)
        if (n.isEmpty()) return text(R.string.rename_empty)
        settings.setMachineName(n)
        _state.update { it.copy(name = n) }
        if (!_state.value.connected) return text(R.string.rename_offline, n)
        if (!_state.value.supportsNickname && _state.value.firmware != null) return text(R.string.rename_old_firmware, n)
        return if (transport.write(MakerOneProtocol.nickname(n)) == WriteResult.OK) text(R.string.rename_ok, n)
        else text(R.string.rename_failed, n)
    }

    /** « Mettre à l'heure » (geste de l'utilisateur). */
    suspend fun syncClock(): Boolean = ensureConnected() && transport.write(MakerOneProtocol.setTime(localEpochSeconds())) == WriteResult.OK

    private suspend fun sendAndAwait(frame: ByteArray, timeoutMs: Long = RESPONSE_TIMEOUT_MS, match: (Incoming) -> Boolean): Incoming? {
        // Abonnement à la réponse AVANT l'envoi (démarrage immédiat), pour ne pas la manquer.
        val deferred = CompletableDeferred<Incoming?>()
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) { deferred.complete(responses.firstOrNull { match(it) }) }
        try {
            if (transport.write(frame) != WriteResult.OK) return null
            return withTimeoutOrNull(timeoutMs) { deferred.await() }
        } finally {
            job.cancel()
        }
    }

    companion object {
        private const val POLL_MS = 3_000L
        /** Relèves d'état de suite impossibles à envoyer avant de tenir la liaison pour coupée. */
        private const val MAX_POLL_FAILURES = 3
        /** Essais de lecture des réglages (42) sans geste, par processus. */
        private const val MAX_SETUP_ATTEMPTS = 3
        /** Hors infusion, une machine connectée qui ne répond plus depuis une minute est tenue pour perdue. */
        private const val IDLE_SILENCE_MS = 60_000L
        private const val RESPONSE_TIMEOUT_MS = 5_000L

        /** Heure locale comptée comme de l'UTC (ce qu'attend la machine), avec le vrai décalage horaire. */
        fun localEpochSeconds(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
            now.epochSecond + zone.rules.getOffset(now).totalSeconds
    }
}

/** Raccourci pour les écrans : un thé enregistré lancé tel quel. */
suspend fun MachineController.brew(tea: Tea): UiText? = brew(tea.name, tea.temperature, tea.waterMl, tea.steepSeconds, tea.id)
