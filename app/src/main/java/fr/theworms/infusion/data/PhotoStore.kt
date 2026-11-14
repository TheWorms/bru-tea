package fr.theworms.infusion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Photos des thés, gardées dans les fichiers privés de l'app (jamais dans la galerie). */
class PhotoStore(private val context: Context) {
    private val dir = File(context.filesDir, "photos")

    /**
     * Copie une photo choisie dans la galerie, réduite pour que son plus grand côté fasse au plus 1600 px
     * (une capture d'écran très haute ne garde pas ses 20 000 px), en WebP. Null si elle ne se lit pas ou
     * ne s'écrit pas (fichier partiel supprimé).
     */
    suspend fun importFromGallery(uri: Uri): String? = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val out = File(dir, "${UUID.randomUUID()}.webp")
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = fitWithin(info.size.width, info.size.height, MAX_SIDE)
                if (w != info.size.width) decoder.setTargetSize(w, h)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val written = out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, it) }
            bitmap.recycle()
            if (!written) { out.delete(); null } else out.absolutePath
        } catch (e: kotlinx.coroutines.CancellationException) {
            out.delete(); throw e
        } catch (e: Throwable) { // OutOfMemoryError compris
            out.delete(); null
        }
    }

    /**
     * Ménage : supprime du dossier les photos qu'aucun thé n'utilise et vieilles de plus de 7 jours. Une saisie
     * restaurée après que l'app a été tuée retrouve ainsi ses photos, même plusieurs heures plus tard.
     */
    suspend fun sweep(used: Set<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { f -> if (f.isFile && isStale(f.absolutePath, f.lastModified(), used, now)) f.delete() }
    }

    /** Supprime une photo de l'app ; ne touche à rien en dehors du dossier des photos. */
    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path == null) return@withContext
        val f = File(path)
        if (f.canonicalPath.startsWith(dir.canonicalPath + File.separator)) f.delete()
    }

    companion object {
        const val MAX_SIDE = 1600
        /** Âge au-delà duquel une photo qu'aucun thé n'utilise est supprimée. */
        const val SWEEP_AGE_MS = 7L * 24 * 3600 * 1000

        fun isStale(path: String, modifiedAt: Long, used: Set<String>, now: Long): Boolean =
            path !in used && now - modifiedAt > SWEEP_AGE_MS

        /** Taille ramenée dans un carré de `max` px, proportions gardées. */
        fun fitWithin(w: Int, h: Int, max: Int): Pair<Int, Int> {
            val side = maxOf(w, h)
            if (side <= max || side <= 0) return w to h
            return (w.toLong() * max / side).toInt().coerceAtLeast(1) to (h.toLong() * max / side).toInt().coerceAtLeast(1)
        }
    }
}
