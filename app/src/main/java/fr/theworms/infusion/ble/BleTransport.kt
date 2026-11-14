package fr.theworms.infusion.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import fr.theworms.infusion.R
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.text.text
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Service et caractéristiques de la Maker One. */
object MakerOneUuids {
    val SERVICE: UUID = UUID.fromString("aae28f00-71b5-42a1-8c3c-f9cf6ac969d0")
    /** La machine parle sur celle-ci (notifications, une trame entière chacune). */
    val READ: UUID = UUID.fromString("aae28f01-71b5-42a1-8c3c-f9cf6ac969d0")
    /** Le téléphone écrit sur celle-ci, sans réponse. */
    val WRITE: UUID = UUID.fromString("aae28f02-71b5-42a1-8c3c-f9cf6ac969d0")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    /** Service standard « informations sur l'appareil » et version du firmware (JJMMAA…). */
    val DEVICE_INFO: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val FIRMWARE_REVISION: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    /** Service d'accès générique et nom de l'appareil, tel que la machine le donne à la connexion. */
    val GENERIC_ACCESS: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    val DEVICE_NAME: UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
}

sealed interface Link {
    data object Idle : Link
    data class Connecting(val address: String) : Link
    data class Ready(val address: String, val name: String, val firmware: String?) : Link
    data class Lost(val address: String, val reason: UiText) : Link
}

data class FoundMachine(val address: String, val name: String, val rssi: Int, val bonded: Boolean)

/** Résultat d'une écriture : `PairingNeeded` = la machine exige une liaison chiffrée (appairage). */
enum class WriteResult { OK, PairingNeeded, Failed }

/**
 * Lien Bluetooth Low Energy brut avec la machine : recherche, connexion, abonnement, écriture d'une
 * trame à la fois. Ne connaît rien du contenu des trames (voir MakerOneProtocol).
 *
 * Les rappels GATT arrivent sur des fils d'Android : l'état partagé est @Volatile, et chaque appel GATT est
 * protégé (une pile Bluetooth qui tombe ou une permission retirée lèvent des exceptions : on rend Failed).
 */
@SuppressLint("MissingPermission") // hasPermissions() est vérifié avant chaque appel
class BleTransport(private val context: Context) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = manager?.adapter

    private val _link = MutableStateFlow<Link>(Link.Idle)
    val link: StateFlow<Link> = _link.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    /** Bluetooth du téléphone allumé ou non, suivi en direct (écran d'association). */
    private val _bluetoothOn = MutableStateFlow(isBluetoothOn())
    val bluetoothOn: StateFlow<Boolean> = _bluetoothOn.asStateFlow()

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                _bluetoothOn.value = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF) == BluetoothAdapter.STATE_ON
            }
        }
        runCatching {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    /** Échanges écrits dans la console Android seulement (adb logcat -s Infusion), pas dans l'app. */
    fun log(line: String) { android.util.Log.d("Infusion", line) }

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var readChar: BluetoothGattCharacteristic? = null
    @Volatile private var mtu = DEFAULT_MTU
    @Volatile private var firmware: String? = null
    @Volatile private var deviceName: String? = null
    /** Lectures à faire après le MTU : version du firmware, puis nom de l'appareil (enchaînées par les rappels). */
    private val pendingReads = ArrayDeque<BluetoothGattCharacteristic>()
    private val opLock = Mutex()

    /**
     * Opération en attente de son rappel. Une opération abandonnée sur délai laisse un rappel en retard :
     * il est compté dans `late` et ignoré, pour ne pas achever l'opération suivante à sa place. Les
     * compteurs repartent de zéro à chaque connexion (une liaison qui perd ses rappels finit reconnectée).
     */
    private class PendingOp(val result: CompletableDeferred<Int> = CompletableDeferred())
    @Volatile private var pendingOp: PendingOp? = null
    private val late = AtomicInteger(0)
    @Volatile private var pendingSetup: CompletableDeferred<Boolean>? = null

    fun hasPermissions(): Boolean = REQUIRED_PERMISSIONS.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    fun isBluetoothOn(): Boolean = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    fun isBonded(address: String): Boolean =
        hasPermissions() && runCatching { adapter?.bondedDevices?.any { it.address == address } == true }.getOrDefault(false)

    /** Machines à portée dont le nom contient « BRU » (comme l'app d'origine), ou qui annoncent le service. */
    fun scan(): Flow<FoundMachine> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (!hasPermissions() || scanner == null) { close(); return@callbackFlow }
        val bonded = runCatching { adapter?.bondedDevices?.map { it.address }?.toSet() }.getOrNull().orEmpty()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val record = result.scanRecord
                val service = record?.serviceUuids?.contains(ParcelUuid(MakerOneUuids.SERVICE)) == true
                val name = record?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: ""
                if (service || name.contains(NAME_PREFIX, ignoreCase = true)) {
                    trySend(FoundMachine(result.device.address, name.ifBlank { "BRU Machine" }, result.rssi, result.device.address in bonded))
                }
            }
        }
        if (runCatching { scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb) }.isFailure) {
            close(); return@callbackFlow
        }
        awaitClose { runCatching { scanner.stopScan(cb) } }
    }

    /**
     * Connexion, découverte, MTU de 247, priorité haute, lecture de la version du firmware.
     * L'abonnement aux notifications est fait ensuite par [subscribe], après la mise à l'heure.
     */
    suspend fun connect(address: String): Boolean {
        if (!hasPermissions() || !isBluetoothOn()) return false
        disconnect()
        val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return false
        _link.value = Link.Connecting(address)
        log("connexion à $address")
        firmware = null
        deviceName = null
        // Rien n'est gardé de la liaison précédente : MTU de base, numéros d'opération remis à zéro.
        mtu = DEFAULT_MTU
        late.set(0); pendingOp = null
        val setup = CompletableDeferred<Boolean>().also { pendingSetup = it }
        gatt = runCatching { device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
        if (gatt == null) {
            _link.value = Link.Lost(address, text(R.string.link_no_answer))
            return false
        }
        val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { setup.await() } ?: false
        if (!ok) {
            log("pas de réponse à la connexion")
            disconnect()
            _link.value = Link.Lost(address, text(R.string.link_no_answer))
        } else if (mtu < MIN_MTU) {
            log("MTU trop petit : $mtu")
            disconnect()
            _link.value = Link.Lost(address, text(R.string.link_mtu, mtu))
            return false
        }
        return ok && mtu >= MIN_MTU
    }

    fun disconnect() {
        val g = gatt
        gatt = null
        writeChar = null
        readChar = null
        pendingOp?.result?.complete(STATUS_DISCONNECTED)
        pendingOp = null
        pendingSetup?.complete(false)
        if (g != null) runCatching { g.disconnect() }.also { runCatching { g.close() } }
        _link.value = Link.Idle
    }

    /** Active les notifications de la machine. Peut déclencher l'appairage (liaison chiffrée). */
    suspend fun subscribe(): WriteResult = opLock.withLock {
        val g = gatt ?: return WriteResult.Failed
        val ch = readChar ?: return WriteResult.Failed
        if (runCatching { g.setCharacteristicNotification(ch, true) }.getOrDefault(false) != true) return WriteResult.Failed
        val cccd = ch.getDescriptor(MakerOneUuids.CCCD) ?: return WriteResult.OK
        val op = startOp()
        val started = runCatching { g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }.getOrDefault(-1)
        if (started != BluetoothStatusCodes.SUCCESS) { abandon(op); return WriteResult.Failed }
        val status = withTimeoutOrNull(PAIRING_TIMEOUT_MS) { op.result.await() }
        finish(op, status)
        toResult(status).also { log("abonnement : $it") }
    }

    /** Écrit une trame entière, sans réponse (comme l'app d'origine). */
    suspend fun write(frame: ByteArray): WriteResult = opLock.withLock {
        val g = gatt ?: return WriteResult.Failed
        val ch = writeChar ?: return WriteResult.Failed
        if (frame.size > mtu - 3) { log("trame trop longue (${frame.size} octets, MTU $mtu)"); return WriteResult.Failed }
        log("→ " + MakerOneProtocol.hex(frame))
        val op = startOp()
        val started = runCatching { g.writeCharacteristic(ch, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) }.getOrDefault(-1)
        if (started != BluetoothStatusCodes.SUCCESS) { abandon(op); return WriteResult.Failed }
        val status = withTimeoutOrNull(WRITE_TIMEOUT_MS) { op.result.await() }
        finish(op, status)
        toResult(status).also { if (it != WriteResult.OK) log("écriture : $it") }
    }

    private fun startOp(): PendingOp = PendingOp().also { pendingOp = it }

    private fun abandon(op: PendingOp) { if (pendingOp === op) pendingOp = null }

    /** Fin d'une opération ; sans rappel dans le délai, son rappel en retard sera ignoré. */
    private fun finish(op: PendingOp, status: Int?) {
        abandon(op)
        if (status == null && gatt != null) late.incrementAndGet()
    }

    /** Rappel d'une écriture : ignoré s'il appartient à une opération déjà abandonnée. */
    private fun answer(status: Int) {
        if (late.getAndUpdate { if (it > 0) it - 1 else 0 } > 0) return
        pendingOp?.result?.complete(status)
    }

    private fun toResult(status: Int?): WriteResult = when (status) {
        BluetoothGatt.GATT_SUCCESS -> WriteResult.OK
        BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION, GATT_INSUFFICIENT_AUTHORIZATION, BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION -> WriteResult.PairingNeeded
        else -> WriteResult.Failed
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                if (runCatching { g.discoverServices() }.getOrDefault(false) != true) pendingSetup?.complete(false)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                pendingSetup?.complete(false)
                pendingOp?.result?.complete(STATUS_DISCONNECTED)
                runCatching { g.close() }
                if (gatt == g) {
                    log("liaison coupée (statut $status)")
                    gatt = null; writeChar = null; readChar = null
                    _link.value = Link.Lost(g.device.address, text(R.string.link_lost))
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(MakerOneUuids.SERVICE)
            readChar = service?.getCharacteristic(MakerOneUuids.READ)
            writeChar = service?.getCharacteristic(MakerOneUuids.WRITE)
            if (readChar == null || writeChar == null) { log("service de la machine introuvable"); pendingSetup?.complete(false); return }
            if (runCatching { g.requestMtu(REQUESTED_MTU) }.getOrDefault(false) != true) pendingSetup?.complete(false)
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu
            log("MTU $mtu")
            runCatching { g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
            synchronized(pendingReads) {
                pendingReads.clear()
                g.getService(MakerOneUuids.DEVICE_INFO)?.getCharacteristic(MakerOneUuids.FIRMWARE_REVISION)?.let { pendingReads.addLast(it) }
                g.getService(MakerOneUuids.GENERIC_ACCESS)?.getCharacteristic(MakerOneUuids.DEVICE_NAME)?.let { pendingReads.addLast(it) }
            }
            readNext(g)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val text = value.toString(Charsets.UTF_8).replace("\u0000", "").trim()
                when (characteristic.uuid) {
                    MakerOneUuids.FIRMWARE_REVISION -> { firmware = text; log("firmware « $text »") }
                    MakerOneUuids.DEVICE_NAME -> { deviceName = text.ifBlank { null }; log("nom « $text »") }
                }
            }
            readNext(g)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = answer(status)

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = answer(status)

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid != MakerOneUuids.READ) return
            log("← " + MakerOneProtocol.hex(value))
            // Une notification peut, rarement, porter plusieurs trames : chacune est lue à part.
            MakerOneProtocol.split(value).forEach { _incoming.tryEmit(it) }
        }
    }

    private fun readNext(g: BluetoothGatt) {
        while (true) {
            val next = synchronized(pendingReads) { pendingReads.removeFirstOrNull() } ?: break
            if (runCatching { g.readCharacteristic(next) }.getOrDefault(false)) return
        }
        ready(g)
    }

    private fun ready(g: BluetoothGatt) {
        _link.value = Link.Ready(g.device.address, deviceName ?: runCatching { g.device.name }.getOrNull() ?: "BRU Machine", firmware)
        pendingSetup?.complete(true)
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        )
        const val NAME_PREFIX = "BRU"
        private const val DEFAULT_MTU = 23
        private const val REQUESTED_MTU = 247
        /** La plus longue trame (infusion, forme longue) fait 75 octets, plus 3 d'en-tête ATT. */
        private const val MIN_MTU = 78
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val WRITE_TIMEOUT_MS = 3_000L
        /** L'appairage affiche une fenêtre d'Android : on laisse le temps de la valider. */
        private const val PAIRING_TIMEOUT_MS = 30_000L
        private const val GATT_INSUFFICIENT_AUTHORIZATION = 8
        private const val STATUS_DISCONNECTED = -1
    }
}
