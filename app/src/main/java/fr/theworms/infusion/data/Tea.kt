package fr.theworms.infusion.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.theworms.infusion.domain.TeaType

/** Un thé enregistré sur le téléphone. Les réglages restent dans les plages de MachineLimits. */
@Entity(tableName = "teas")
data class Tea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TeaType = TeaType.GREEN,
    val origin: String = "",
    /** 45 à 95 °C par 5, ou 100 pour « Maximum ». */
    val temperature: Int,
    @ColumnInfo(name = "water_ml") val waterMl: Int,
    @ColumnInfo(name = "steep_seconds") val steepSeconds: Int,
    val favorite: Boolean = false,
    /** Photo copiée dans les fichiers de l'app (galerie ou base de thés). */
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    val notes: String = "",
    /**
     * Photo venue de la base de thés : son chemin dans tea-db (« Vérifier » la remplace quand elle y change) ; null pour
     * une photo de la galerie (jamais touchée) ; [PHOTO_SOURCE_UNKNOWN] pour un thé d'avant la 0.1.22, dont on ne sait
     * pas d'où vient la photo (une question à « Vérifier » : remplacer ou garder).
     */
    @ColumnInfo(name = "photo_source") val photoSource: String? = null,
    /** ETag de cette photo sur GitHub : « Vérifier » ne la retélécharge pas si elle n'a pas changé. */
    @ColumnInfo(name = "photo_etag") val photoEtag: String? = null,
    /** `id` de la fiche de la base de thés d'où viennent les infos, s'il y en a une. */
    @ColumnInfo(name = "catalog_id") val catalogId: String? = null,
    /** Fait partie des 10 thés écrits sur la machine. */
    @ColumnInfo(name = "on_machine") val onMachine: Boolean = false,
    /** Position sur la machine (1 à 10) une fois écrit. */
    @ColumnInfo(name = "machine_slot") val machineSlot: Int? = null,
    /** Faux tant que la machine n'a pas confirmé la dernière version des réglages. */
    val synced: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** Origine d'une photo inconnue (thé enregistré avant la 0.1.22). */
const val PHOTO_SOURCE_UNKNOWN = "?"
