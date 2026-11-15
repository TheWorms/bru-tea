package fr.theworms.infusion.catalog

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.theworms.infusion.data.AppDatabase
import fr.theworms.infusion.data.PHOTO_SOURCE_UNKNOWN
import fr.theworms.infusion.data.Tea
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * « Vérifier », pour les photos : seules les photos venues de la base suivent tea-db (nouvelle image, ou nouveau
 * chemin) ; la galerie n'est jamais touchée ; une photo d'origine inconnue attend « Remplacer » ; l'ETag évite de
 * retélécharger une image qui n'a pas changé.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CatalogPhotosTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val dao = db.teas()

    /** Faux GitHub : chemin → (contenu, ETag) ; une requête avec le bon If-None-Match reçoit 304. */
    private val served = HashMap<String, Pair<ByteArray, String>>()
    private val requests = ArrayList<String>()
    private val http = OkHttpClient.Builder().addInterceptor { chain ->
        val req = chain.request()
        val path = req.url.encodedPath.substringAfter("/docs/", "")
        requests += path
        val (bytes, etag) = served[path] ?: return@addInterceptor Response.Builder().request(req).protocol(Protocol.HTTP_1_1)
            .code(404).message("Not Found").body(ByteArray(0).toResponseBody()).build()
        val builder = Response.Builder().request(req).protocol(Protocol.HTTP_1_1).header("ETag", etag)
        if (req.header("If-None-Match") == etag) builder.code(304).message("Not Modified").body(ByteArray(0).toResponseBody()).build()
        else builder.code(200).message("OK").body(bytes.toResponseBody("image/webp".toMediaType())).build()
    }.build()
    private val catalog = TeaCatalog(context, http)

    @Before fun base() = runBlocking {
        val dir = File(context.filesDir, "catalogue").apply { mkdirs() }
        File(dir, "thes.json").writeText(
            """[{"id": "black-assam", "nom": "Assam", "famille": "black", "eau_ml": 250, "temperature_c": 95, "duree_s": 240,
                 "photo": "assets/photos/catalogue/black-assam.webp"},
                {"id": "green-sencha", "nom": "Sencha", "famille": "green", "eau_ml": 250, "temperature_c": 80, "duree_s": 90,
                 "photo": "assets/photos/catalogue/green-sencha-v2.webp"}]""",
        )
        catalog.loadCached()
    }

    @After fun close() = db.close()

    private fun photo(name: String, bytes: ByteArray): String =
        File(context.filesDir, "photos/$name").apply { parentFile?.mkdirs(); writeBytes(bytes) }.absolutePath

    private fun tea(name: String, catalogId: String?, path: String, source: String?, etag: String? = null) = runBlocking {
        dao.insert(Tea(name = name, temperature = 90, waterMl = 250, steepSeconds = 180, catalogId = catalogId,
            photoPath = path, photoSource = source, photoEtag = etag))
    }

    private fun get(id: Long) = runBlocking { dao.get(id)!! }

    @Test fun `photo de la base changée dans tea-db, remplacée, la galerie jamais touchée`() = runBlocking {
        val neuve = byteArrayOf(1, 2, 3)
        served["assets/photos/catalogue/black-assam.webp"] = neuve to "\"e2\""
        served["assets/photos/catalogue/green-sencha-v2.webp"] = byteArrayOf(9) to "\"s2\""
        val base = tea("Assam", "black-assam", photo("a.webp", byteArrayOf(0)), "assets/photos/catalogue/black-assam.webp", "\"e1\"")
        val galerie = tea("Sencha", "green-sencha", photo("g.webp", byteArrayOf(7)), null)

        assertEquals(1, catalog.refreshPhotos(dao))

        val a = get(base)
        assertNotEquals(File(context.filesDir, "photos/a.webp").absolutePath, a.photoPath)
        assertArrayEquals(neuve, File(a.photoPath!!).readBytes())
        assertEquals("\"e2\"", a.photoEtag)
        assertEquals(File(context.filesDir, "photos/g.webp").absolutePath, get(galerie).photoPath)
        assertNull(get(galerie).photoSource)
        assertTrue("la galerie n'est même pas demandée", "assets/photos/catalogue/green-sencha-v2.webp" !in requests)
        assertEquals(1, catalog.status.value.photosUpdated)
    }

    @Test fun `même ETag, 304, rien de retéléchargé ni de remplacé`() = runBlocking {
        served["assets/photos/catalogue/black-assam.webp"] = byteArrayOf(1, 2) to "\"e1\""
        val path = photo("a.webp", byteArrayOf(0))
        val id = tea("Assam", "black-assam", path, "assets/photos/catalogue/black-assam.webp", "\"e1\"")
        assertEquals(0, catalog.refreshPhotos(dao))
        assertEquals(path, get(id).photoPath)
        assertArrayEquals(byteArrayOf(0), File(path).readBytes())
    }

    @Test fun `nouveau chemin de photo dans la fiche, la nouvelle image est prise`() = runBlocking {
        served["assets/photos/catalogue/green-sencha-v2.webp"] = byteArrayOf(4, 4) to "\"s2\""
        val id = tea("Sencha", "green-sencha", photo("s.webp", byteArrayOf(0)), "assets/photos/catalogue/green-sencha.webp", "\"s1\"")
        assertEquals(1, catalog.refreshPhotos(dao))
        assertEquals("assets/photos/catalogue/green-sencha-v2.webp", get(id).photoSource)
        assertArrayEquals(byteArrayOf(4, 4), File(get(id).photoPath!!).readBytes())
    }

    @Test fun `origine inconnue, rien sans Remplacer, puis remplacée, image identique gardée telle quelle`() = runBlocking {
        served["assets/photos/catalogue/black-assam.webp"] = byteArrayOf(5) to "\"e1\""
        served["assets/photos/catalogue/green-sencha-v2.webp"] = byteArrayOf(6) to "\"s1\""
        val vieille = photo("a.webp", byteArrayOf(0))
        val identique = photo("s.webp", byteArrayOf(6))
        val a = tea("Assam", "black-assam", vieille, PHOTO_SOURCE_UNKNOWN)
        val s = tea("Sencha", "green-sencha", identique, PHOTO_SOURCE_UNKNOWN)

        assertEquals(0, catalog.refreshPhotos(dao))
        assertEquals(vieille, get(a).photoPath)
        assertTrue("rien demandé sans réponse", requests.isEmpty())

        assertEquals(1, catalog.refreshPhotos(dao, replaceUnknown = true))
        assertArrayEquals(byteArrayOf(5), File(get(a).photoPath!!).readBytes())
        assertEquals("assets/photos/catalogue/black-assam.webp", get(a).photoSource)
        assertEquals(identique, get(s).photoPath)
        assertEquals("assets/photos/catalogue/green-sencha-v2.webp", get(s).photoSource)
        assertEquals("\"s1\"", get(s).photoEtag)
    }

    @Test fun `origine inconnue sans photo dans la base, gardée après Remplacer, la question ne revient pas`() = runBlocking {
        val path = photo("x.webp", byteArrayOf(0))
        val id = tea("Maison", "black-inconnu", path, PHOTO_SOURCE_UNKNOWN)
        assertEquals(0, catalog.refreshPhotos(dao, replaceUnknown = true))
        assertEquals(path, get(id).photoPath)
        assertNull(get(id).photoSource)
    }

    @Test fun `photo introuvable en ligne, la photo du thé reste`() = runBlocking {
        val path = photo("a.webp", byteArrayOf(0))
        val id = tea("Assam", "black-assam", path, "assets/photos/catalogue/black-assam.webp", "\"e1\"")
        assertEquals(0, catalog.refreshPhotos(dao))
        assertEquals(path, get(id).photoPath)
        assertEquals("\"e1\"", get(id).photoEtag)
    }

    @Test fun `photo téléchargée pour une fiche, chemin dans tea-db et ETag rendus`() = runBlocking {
        served["assets/photos/catalogue/black-assam.webp"] = byteArrayOf(3) to "\"e9\""
        val got = catalog.downloadPhoto(catalog.entry("black-assam")!!)!!
        assertEquals("assets/photos/catalogue/black-assam.webp", got.source)
        assertEquals("\"e9\"", got.etag)
        assertArrayEquals(byteArrayOf(3), File(got.path).readBytes())
    }
}
