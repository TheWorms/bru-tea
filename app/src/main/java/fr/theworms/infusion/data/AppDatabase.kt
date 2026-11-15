package fr.theworms.infusion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.theworms.infusion.domain.TeaType

class Converters {
    @TypeConverter fun teaTypeToKey(type: TeaType): String = type.key
    @TypeConverter fun keyToTeaType(key: String): TeaType = TeaType.fromKey(key)
}

/**
 * Base locale. Tout changement d'entité = version +1 et migration écrite (jamais de
 * destruction : les thés n'existent que sur le téléphone), schéma exporté dans
 * app/schemas.
 */
@Database(entities = [Tea::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teas(): TeaDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "infusion.db").addMigrations(MIGRATION_1_2).build()
    }
}

/**
 * 1 → 2 (0.1.22) : origine des photos (photo_source, photo_etag). Les thés de la base qui ont déjà une photo ne disent
 * pas si elle vient de la base ou de la galerie : marqués inconnus, « Vérifier » posera la question une fois.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE teas ADD COLUMN photo_source TEXT")
        db.execSQL("ALTER TABLE teas ADD COLUMN photo_etag TEXT")
        db.execSQL("UPDATE teas SET photo_source = '$PHOTO_SOURCE_UNKNOWN' WHERE photo_path IS NOT NULL AND catalog_id IS NOT NULL")
    }
}
