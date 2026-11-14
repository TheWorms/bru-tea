package fr.theworms.infusion.domain

import androidx.annotation.StringRes
import fr.theworms.infusion.R

/** Types de thé. `key` est la valeur enregistrée avec le thé (Room) ; la base de thés donne `famille`, ramenée à ces types par Catalog.typeOfFamily. */
enum class TeaType(val key: String, @StringRes val label: Int, val argb: Long) {
    GREEN("vert", R.string.type_green, 0xFF8CA674),
    BLACK("noir", R.string.type_black, 0xFF7A4E3A),
    OOLONG("oolong", R.string.type_oolong, 0xFFB8894A),
    WHITE("blanc", R.string.type_white, 0xFFC7BF9F),
    HERBAL("tisane", R.string.type_herbal, 0xFFA3728D);

    companion object {
        fun fromKey(key: String?): TeaType = entries.firstOrNull { it.key == key } ?: GREEN
    }
}
