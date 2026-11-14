package fr.theworms.infusion.update

/** Version « majeure.mineure.correctif » des APK publiées (Infusion.0.1.2.apk). */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(text: String): Version? {
            val p = text.trim().split('.')
            if (p.size != 3) return null
            val n = p.map { it.toIntOrNull() ?: return null }
            if (n.any { it < 0 }) return null
            return Version(n[0], n[1], n[2])
        }
    }
}

/**
 * Numéros à essayer après `v` : les 10 correctifs suivants, les 2 mineures suivantes (correctifs 0 à 5),
 * la majeure suivante (0.0 à 0.2), soit 25 essais. Une version retirée de l'hébergeur ne bloque donc pas la recherche.
 */
fun Version.window(): List<Version> = buildList {
    for (p in patch + 1..patch + 10) add(Version(major, minor, p))
    for (m in minor + 1..minor + 2) for (p in 0..5) add(Version(major, m, p))
    for (p in 0..2) add(Version(major + 1, 0, p))
}

/**
 * Cherche la dernière version publiée (l'hébergeur ne liste pas son dossier) : essaie une fenêtre de numéros
 * après la version actuelle, garde le plus haut publié, et recommence à partir de lui. `exists` dit si un
 * numéro est publié ; les essais d'une fenêtre peuvent être faits en parallèle par l'appelant.
 */
suspend fun findLatest(current: Version, maxRounds: Int = 5, existing: suspend (List<Version>) -> List<Version>): Version {
    var best = current
    repeat(maxRounds) {
        val found = existing(best.window()).maxOrNull() ?: return best
        if (found <= best) return best
        best = found
    }
    return best
}
