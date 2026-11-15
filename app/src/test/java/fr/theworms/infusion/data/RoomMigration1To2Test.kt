package fr.theworms.infusion.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 1 → 2 (0.1.22) : les thés gardent tout, et seuls ceux de la base qui ont une photo sont marqués « origine inconnue »
 * (la question Remplacer / Garder) ; une photo sans fiche de la base est une photo de la galerie. La base v1 est créée
 * avec le schéma exporté (schemas/…/1.json), puis ouverte par Room en v2 : Room valide le schéma obtenu par la migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomMigration1To2Test {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    /** Base v1 telle que la 0.1.21 la laisse : tables, table de Room et numéro de version. */
    private fun createV1(file: File) {
        val schema = Json.parseToJsonElement(File("schemas/fr.theworms.infusion.data.AppDatabase/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getValue("entities").jsonArray.forEach { e ->
                db.execSQL(e.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", e.jsonObject.getValue("tableName").jsonPrimitive.content))
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)", arrayOf(schema.getValue("identityHash").jsonPrimitive.content))
            fun insert(name: String, photo: String?, catalog: String?) = db.execSQL(
                "INSERT INTO teas (name, type, origin, temperature, water_ml, steep_seconds, favorite, photo_path, notes, " +
                    "catalog_id, on_machine, machine_slot, synced, created_at) VALUES (?, 'black', '', 90, 250, 180, 1, ?, 'n', ?, 0, NULL, 1, 7)",
                arrayOf(name, photo, catalog),
            )
            insert("Assam", "/p/a.webp", "black-assam")
            insert("Sencha", null, "green-sencha")
            insert("Maison", "/p/m.webp", null)
            db.version = 1
        }
    }

    @Test fun `migration 1 vers 2`() {
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        createV1(file)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val teas = runBlocking { db.teas().observeAll().first() }.associateBy { it.name }
            assertEquals(3, teas.size)
            assertEquals(PHOTO_SOURCE_UNKNOWN, teas.getValue("Assam").photoSource)
            assertEquals("/p/a.webp", teas.getValue("Assam").photoPath)
            assertEquals("black-assam", teas.getValue("Assam").catalogId)
            assertEquals("n", teas.getValue("Assam").notes)
            assertNull(teas.getValue("Sencha").photoSource)
            assertNull(teas.getValue("Maison").photoSource)
            assertNull(teas.getValue("Assam").photoEtag)
            assertEquals(1, runBlocking { db.teas().observeUnknownPhotos().first() })
            runBlocking { db.teas().keepUnknownPhotos() }
            assertEquals(0, runBlocking { db.teas().observeUnknownPhotos().first() })
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
