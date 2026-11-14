package fr.theworms.infusion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
@Database(entities = [Tea::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teas(): TeaDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "infusion.db").build()
    }
}
