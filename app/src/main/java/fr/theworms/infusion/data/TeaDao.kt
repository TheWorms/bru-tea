package fr.theworms.infusion.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TeaDao {
    /** Par nom, accents compris (UNICODE, collation d'Android) : « Échinacée » avec les E, pas après « Yunnan ». */
    @Query("SELECT * FROM teas ORDER BY name COLLATE UNICODE")
    fun observeAll(): Flow<List<Tea>>

    @Query("SELECT * FROM teas WHERE favorite = 1 ORDER BY name COLLATE UNICODE")
    fun observeFavorites(): Flow<List<Tea>>

    @Query("SELECT * FROM teas WHERE id = :id")
    fun observe(id: Long): Flow<Tea?>

    @Query("SELECT * FROM teas WHERE id = :id")
    suspend fun get(id: Long): Tea?

    /**
     * Thés de la machine dans l'ordre de leurs positions ; ceux qui n'en ont pas encore (ajoutés depuis) à la fin,
     * comme dans l'onglet Machine : SQLite mettrait les NULL en tête et décalerait tous les préréglages.
     */
    @Query("SELECT * FROM teas WHERE on_machine = 1 ORDER BY machine_slot IS NULL, machine_slot, name COLLATE UNICODE")
    suspend fun onMachine(): List<Tea>

    @Query("SELECT COUNT(*) FROM teas WHERE on_machine = 1")
    suspend fun countOnMachine(): Int

    @Insert
    suspend fun insert(tea: Tea): Long

    @Update
    suspend fun update(tea: Tea)

    @Query("UPDATE teas SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /**
     * Résultat de la synchronisation d'un thé : seuls ces deux champs, pour ne pas écraser une modification faite
     * pendant. « Synchronisé » seulement si le thé a encore les valeurs écrites (nom, température, eau, durée) :
     * modifié pendant l'envoi, il reste à synchroniser ; retiré de la machine pendant, il n'est pas touché.
     */
    @Query(
        "UPDATE teas SET machine_slot = :slot, synced = (:synced AND name = :name AND temperature = :temperature " +
            "AND water_ml = :waterMl AND steep_seconds = :steepSeconds) WHERE id = :id AND on_machine = 1",
    )
    suspend fun setMachineSync(id: Long, synced: Boolean, slot: Int, name: String, temperature: Int, waterMl: Int, steepSeconds: Int)

    /** Chemins des photos utilisées, pour le ménage du dossier des photos. */
    @Query("SELECT photo_path FROM teas WHERE photo_path IS NOT NULL")
    suspend fun photoPaths(): List<String>

    @Delete
    suspend fun delete(tea: Tea)
}
