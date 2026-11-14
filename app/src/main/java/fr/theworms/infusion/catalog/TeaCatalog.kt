package fr.theworms.infusion.catalog

import android.content.Context
import fr.theworms.infusion.R
import fr.theworms.infusion.domain.catching
import fr.theworms.infusion.domain.toHex
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.text.text
import fr.theworms.infusion.update.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** État de la base de thés, affiché dans Machine → Options de l'app. */
data class CatalogStatus(
    val busy: Boolean = false,
    /** Empreinte courte de thes.json (le fichier n'a pas de numéro de version). */
    val version: String = "",
    val count: Int = 0,
    val checkedAt: Long = 0,
    val error: UiText? = null,
)

/**
 * Base de thés tea-db : docs/thes.json (environ 230 Ko) est gardé sur le téléphone et revérifié à
 * chaque ouverture de l'app (requête conditionnelle : rien n'est retéléchargé s'il n'a pas changé) ;
 * une photo n'est téléchargée que quand on récupère son thé.
 */
class TeaCatalog(private val context: Context) {
    private val http = Http.client
    private val dir = File(context.filesDir, "catalogue")
    private val teasFile = File(dir, "thes.json")
    private val versionFile = File(dir, "version.txt")
    /** ETag de GitHub pour thes.json : la vérification à l'ouverture ne coûte qu'une réponse 304. */
    private val etagFile = File(dir, "etag.txt")
    private val lock = Mutex()
    @Volatile private var entries: List<CatalogEntry> = emptyList()

    private val _status = MutableStateFlow(CatalogStatus())

    /** Nombre de fiches nouvelles depuis la copie précédente (pas au premier chargement) : notifié à l'ouverture. */
    private val _added = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val added: SharedFlow<Int> = _added.asSharedFlow()
    val status: StateFlow<CatalogStatus> = _status.asStateFlow()

    /** Relit la copie locale (au démarrage, sans réseau). */
    suspend fun loadCached() = withContext(Dispatchers.IO) { lock.withLock {
        val parsed = runCatching { if (teasFile.exists()) Catalog.parse(teasFile.readText()) else null }.getOrNull()
        if (parsed != null) entries = parsed.entries
        _status.value = _status.value.copy(
            version = if (versionFile.exists()) versionFile.readText().trim() else "",
            count = entries.size, checkedAt = if (teasFile.exists()) teasFile.lastModified() else 0,
        )
    } }

    /** À chaque ouverture de l'app : la base a-t-elle changé sur GitHub ? (au plus une fois par minute). */
    suspend fun refreshOnOpen(minIntervalMs: Long = 60_000L) {
        if (!_status.value.busy && System.currentTimeMillis() - lastAttempt > minIntervalMs) refresh()
    }

    @Volatile private var lastAttempt = 0L

    /** Vérifie la base en ligne ; garde la version précédente si quelque chose ne va pas. */
    suspend fun refresh(): CatalogStatus = withContext(Dispatchers.IO) { lock.withLock {
        lastAttempt = System.currentTimeMillis()
        _status.value = _status.value.copy(busy = true, error = null)
        val result = catching {
            val etag = if (entries.isNotEmpty() && teasFile.exists() && etagFile.exists()) etagFile.readText().trim().ifEmpty { null } else null
            val (bytes, newEtag) = download(Catalog.RAW_BASE + Catalog.TEAS_PATH, Catalog.MAX_FILE_BYTES, etag)
            // 304 : la copie du téléphone est déjà la dernière.
            if (bytes == null) return@catching _status.value.version to entries.size
            val version = MessageDigest.getInstance("SHA-256").digest(bytes).toHex().take(8)
            val parsed = Catalog.parse(bytes.toString(Charsets.UTF_8))
            if (parsed.entries.isEmpty()) throw CatalogException(text(R.string.catalog_empty))
            store(bytes, version, newEtag)
            val before = entries.map { it.id }.toSet()
            entries = parsed.entries
            val fresh = Catalog.countAdded(before, parsed.entries.map { it.id })
            if (fresh > 0) _added.tryEmit(fresh)
            version to parsed.entries.size
        }
        _status.value = result.fold(
            onSuccess = { (v, n) -> CatalogStatus(false, v, n, System.currentTimeMillis(), null) },
            onFailure = { _status.value.copy(busy = false, error = it.toUserMessage()) },
        )
        _status.value
    } }

    /**
     * Copie locale remplacée d'un coup : écrite à côté puis renommée. Une app tuée en route laisse l'ancienne
     * copie intacte ; version et ETag ne sont écrits qu'ensuite.
     */
    private fun store(bytes: ByteArray, version: String, etag: String?) {
        try {
            dir.mkdirs()
            val tmp = File(dir, "thes.json.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(teasFile)) { tmp.delete(); throw IOException("renommage") }
            versionFile.writeText(version)
            if (etag != null) etagFile.writeText(etag) else etagFile.delete()
        } catch (e: IOException) {
            throw StorageException(e)
        }
    }

    fun lookup(typed: String): CatalogEntry? = Catalog.lookup(entries, typed)

    fun suggest(typed: String): List<CatalogEntry> = Catalog.suggest(entries, typed)

    fun entry(id: String): CatalogEntry? = entries.firstOrNull { it.id == id }

    /** Télécharge la photo d'une fiche (1200 px) dans les fichiers de l'app ; null si elle manque ou dépasse 2 Mo. */
    suspend fun downloadPhoto(entry: CatalogEntry): String? = withContext(Dispatchers.IO) {
        val rel = entry.photo ?: return@withContext null
        val ext = rel.substringAfterLast('.', "webp").lowercase().take(5)
        val out = File(context.filesDir, "photos/${UUID.randomUUID()}.$ext")
        catching {
            val bytes = download(Catalog.RAW_BASE + Catalog.ASSETS_ROOT + rel, 2_000_000).first ?: return@catching null
            out.parentFile?.mkdirs()
            out.writeBytes(bytes)
            out.absolutePath
        }.onFailure { out.delete() }.getOrNull()
    }

    /** Contenu et ETag ; contenu null si le serveur répond 304 à `etag`. */
    private fun download(url: String, maxBytes: Int, etag: String? = null): Pair<ByteArray?, String?> {
        val request = Request.Builder().url(url).apply { if (etag != null) header("If-None-Match", etag) }.build()
        http.newCall(request).execute().use { resp ->
            if (resp.code == 304) return null to etag
            if (resp.code == 404) throw CatalogException(text(R.string.catalog_not_found, url.substringAfterLast('/')))
            if (resp.code == 401 || resp.code == 403) throw CatalogException(text(R.string.catalog_private))
            if (!resp.isSuccessful) throw CatalogException(text(R.string.catalog_http, resp.code))
            val body = resp.body ?: throw CatalogException(text(R.string.catalog_empty_reply))
            val length = body.contentLength()
            if (length > maxBytes) throw CatalogException(text(R.string.catalog_too_big_kb, (length / 1000).toInt()))
            // Lecture bornée.
            val out = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw CatalogException(text(R.string.catalog_too_big))
                }
            }
            return out.toByteArray() to resp.header("ETag")
        }
    }

    private class CatalogException(val uiText: UiText) : IOException()

    /** Écriture de la copie locale impossible (téléphone plein…) : ce n'est pas un problème de réseau. */
    private class StorageException(cause: IOException) : Exception(cause)

    private fun Throwable.toUserMessage(): UiText = when (this) {
        is CatalogException -> uiText
        is StorageException -> text(R.string.catalog_storage)
        is kotlinx.serialization.SerializationException, is IllegalArgumentException -> text(R.string.catalog_bad_format)
        is javax.net.ssl.SSLException -> text(R.string.catalog_ssl)
        is IOException -> text(R.string.catalog_offline)
        else -> text(R.string.error_unexpected)
    }
}
