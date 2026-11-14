package fr.theworms.infusion.domain

import kotlinx.coroutines.CancellationException

/**
 * Comme runCatching, mais l'annulation d'une coroutine traverse : un écran quitté ou un travail annulé ne
 * doit pas être pris pour une erreur (« pas de connexion »…).
 */
inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/** Empreinte en hexadécimal minuscule, sans séparateur (sha256 des APK et de la base de thés). */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
