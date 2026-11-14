package fr.theworms.infusion.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TeaDao {
    @Query("SELECT * FROM teas ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tea>>

    @Query("SELECT * FROM teas WHERE favorite = 1 ORDER BY name COLLATE NOCASE")
    fun observeFavorites(): Flow<List<Tea>>

    @Query("SELECT * FROM teas WHERE id = :id")
    fun observe(id: Long): Flow<Tea?>

    @Query("SELECT * FROM teas WHERE id = :id")
    suspend fun get(id: Long): Tea?

    @Query("SELECT * FROM teas WHERE on_machine = 1 ORDER BY machine_slot, name COLLATE NOCASE")
    suspend fun onMachine(): List<Tea>

    @Query("SELECT COUNT(*) FROM teas WHERE on_machine = 1")
    suspend fun countOnMachine(): Int

    @Insert
    suspend fun insert(tea: Tea): Long

    @Update
    suspend fun update(tea: Tea)

    @Query("UPDATE teas SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** Résultat de la synchronisation d'un thé : seuls ces deux champs, pour ne pas écraser une modification faite pendant. */
    @Query("UPDATE teas SET synced = :synced, machine_slot = :slot WHERE id = :id")
    suspend fun setMachineSync(id: Long, synced: Boolean, slot: Int)

    /** Chemins des photos utilisées, pour le ménage du dossier des photos. */
    @Query("SELECT photo_path FROM teas WHERE photo_path IS NOT NULL")
    suspend fun photoPaths(): List<String>

    @Delete
    suspend fun delete(tea: Tea)
}
