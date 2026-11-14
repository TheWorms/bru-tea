package fr.theworms.infusion.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** Opérations de l'onglet Machine qui échangent plusieurs trames avec la machine. */
enum class MachineOp { SYNC, READ, SETUP }

/**
 * Une seule de ces opérations à la fois, suivie hors de l'écran. Elles tournent dans le scope de l'app : l'onglet
 * Machine peut être recréé pendant ce temps (changement d'onglet, rotation) et oublier qu'un envoi est en cours. Il
 * lit `current` au lieu d'un `remember`, et un second appui ne lance pas un second envoi entrelacé avec le premier
 * (acquittements mal attribués, thés importés deux fois).
 */
class OperationGate {
    private val lock = Mutex()
    private val _current = MutableStateFlow<MachineOp?>(null)
    val current: StateFlow<MachineOp?> = _current.asStateFlow()

    /** Lance `block`, ou rend `busy` sans rien envoyer si une autre opération est en cours. */
    suspend fun <T> run(op: MachineOp, busy: T, block: suspend () -> T): T {
        if (!lock.tryLock()) return busy
        _current.value = op
        return try {
            block()
        } finally {
            _current.value = null
            lock.unlock()
        }
    }
}
