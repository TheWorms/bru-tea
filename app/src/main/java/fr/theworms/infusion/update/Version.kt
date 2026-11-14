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
