package fr.theworms.infusion.catalog

import fr.theworms.infusion.text.text
import fr.theworms.infusion.text.UiText
import fr.theworms.infusion.ble.MachineLanguage
import fr.theworms.infusion.R
import fr.theworms.infusion.domain.MachineLimits
import fr.theworms.infusion.domain.TeaType
import fr.theworms.infusion.domain.normalizeForSearch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Une fiche de tea-db (docs/thes.json, voir son FORMAT.md), ramenée à ce que la machine sait faire. */
data class CatalogEntry(
    val id: String,
    val name: String,
    /** Autres noms, dont ceux des doublons retirés : utilisés par « Récupérer ». */
    val aliases: List<String>,
    val type: TeaType,
    val origin: String,
    val temperature: Int,
    val waterMl: Int,
    val steepSeconds: Int,
    /** Dose pour `waterMl` (adaptée si le volume a été arrondi pour la machine). */
    val teaGrams: Double?,
    /** Photo relative à docs/ (assets/photos/catalogue/<id>.webp), ou null : l'app garde sa vignette. */
    val photo: String?,
    val photoCredit: String,
    val description: String,
    /** Théine : « non » seulement si la fiche le dit. */
    val caffeineFree: Boolean,
    /** Plante à usage médicinal, ou recette à revoir (« renvoyer au paquet »). */
    val caution: String?,
    /** Thé qui ne se prépare pas à la machine (matcha fouetté, infusion à froid) : raison, sinon null. */
    val unsuitable: UiText?,
    /** Vrai si une valeur a dû être corrigée pour la machine. */
    val adjusted: Boolean,
) {
    /** Nom et autres noms normalisés une seule fois (recherche et suggestions à chaque lettre tapée). */
    val searchKeys: List<String> = (listOf(name) + aliases).map(::normalizeForSearch).filter { it.isNotEmpty() }
}

object Catalog {
    /** La base de thés : le dépôt public tea-db sur GitHub. */
    const val REPO_URL = "https://github.com/TheWorms/tea-db"

    /** Ce que lit l'app, relatif à la racine du dépôt (FORMAT.md de tea-db). */
    const val TEAS_PATH = "docs/thes.json"
    const val ASSETS_ROOT = "docs/"
    const val MAX_FILE_BYTES = 4_000_000
    const val NAME_MAX = 30
    const val ORIGIN_MAX = 30
    const val NOTES_MAX = 300
    const val ID_MAX = 40
    /** Début de la note des thés repris des préréglages de la machine (français, anglais) : remplacée par la fiche de la base. */
    val IMPORT_NOTE_PREFIXES = listOf("Repris du préréglage", "From machine preset")

    private val json = Json { isLenient = true }

    data class Parsed(val entries: List<CatalogEntry>, val rejected: Int)

    /** Lit docs/thes.json (une liste de fiches) ; une fiche invalide est écartée sans faire tomber les autres. */
    fun parse(text: String): Parsed {
        val root = json.parseToJsonElement(text)
        val items = (root as? JsonArray) ?: ((root as? JsonObject)?.values?.firstOrNull { it is JsonArray } as? JsonArray) ?: JsonArray(emptyList())
        var rejected = 0
        val seen = HashSet<String>()
        val entries = items.mapNotNull { el ->
            val e = (el as? JsonObject)?.let { runCatching { toEntry(it) }.getOrNull() }
            if (e == null || !seen.add(e.id)) { rejected++; null } else e
        }
        return Parsed(entries, rejected)
    }

    private fun toEntry(o: JsonObject): CatalogEntry? {
        val id = o.str("id")?.trim()?.takeIf { it.isNotEmpty() && it.length <= ID_MAX } ?: return null
        val name = o.str("nom")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val family = o.str("famille")
        val rawWater = o.num("eau_ml")?.toInt() ?: return null
        val rawTemp = o.num("temperature_c")?.toInt() // null : infusion à température ambiante
        val rawSteep = o.num("duree_s")?.toInt() ?: return null
        val unsuitable = when {
            family == "matcha" || rawSteep == 0 -> text(R.string.unsuitable_matcha)
            family == "cold" || rawTemp == null -> text(R.string.unsuitable_cold)
            else -> null
        }
        val temp = MachineLimits.roundTemperature(rawTemp ?: MachineLimits.TEMP_MIN)
        val water = MachineLimits.roundWater(rawWater)
        val steep = MachineLimits.roundSteep(rawSteep)
        // Dose : the_g × volume ÷ eau_ml (FORMAT.md), arrondie à 0,5 g.
        val grams = o.num("the_g")?.let { g -> scaleDose(g, rawWater, water) }
        val caution = listOfNotNull(o.str("a_revoir"), o.str("precaution")).joinToString(" ").ifBlank { null }
        return CatalogEntry(
            id = id,
            name = name.take(NAME_MAX),
            aliases = (listOfNotNull(o.str("nom_complet")) + ((o["alias"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()))
                .map { it.trim() }.filter { it.isNotEmpty() },
            type = typeOfFamily(family),
            origin = o.str("origine")?.trim().orEmpty().take(ORIGIN_MAX),
            temperature = temp, waterMl = water, steepSeconds = steep,
            teaGrams = grams,
            photo = o.str("photo")?.trim()?.takeIf { it.isNotEmpty() && !it.contains("..") },
            photoCredit = o.str("photo_credit")?.trim().orEmpty().take(60),
            description = o.str("notes")?.trim().orEmpty().take(NOTES_MAX),
            caffeineFree = o.str("theine") == "non",
            caution = caution?.take(NOTES_MAX),
            unsuitable = unsuitable,
            adjusted = unsuitable == null && (temp != rawTemp || water != rawWater || steep != rawSteep),
        )
    }

    /** Familles de tea-db → types de l'app (les familles voisines rejoignent la plus proche). */
    fun typeOfFamily(family: String?): TeaType = when (family) {
        "green", "yellow", "matcha" -> TeaType.GREEN
        "black", "pu_erh", "dark" -> TeaType.BLACK
        "oolong" -> TeaType.OOLONG
        "white" -> TeaType.WHITE
        "herbal" -> TeaType.HERBAL
        else -> TeaType.GREEN
    }

    /**
     * Notes de départ d'un thé repris de la base, dans la langue de l'app : dose, théine, mise en garde, puis
     * le descriptif (la base n'existe qu'en français). Elles sont enregistrées telles quelles avec le thé :
     * leurs deux premières phrases suivent un modèle fixe par langue, que [splitNotes] sait relire.
     */
    fun notesFor(e: CatalogEntry, language: MachineLanguage = MachineLanguage.FR): String = listOfNotNull(
        e.teaGrams?.let { g ->
            if (language == MachineLanguage.EN) "${formatGramsLabel(g, language)} g of tea for ${e.waterMl} ml."
            else "${formatGramsLabel(g, language)} g de thé pour ${e.waterMl} ml."
        },
        if (e.caffeineFree) CAFFEINE_FREE.getValue(language) else null,
        e.caution,
        e.description.ifBlank { null },
    ).joinToString(" ").take(NOTES_MAX)

    private val CAFFEINE_FREE = mapOf(MachineLanguage.FR to "Sans théine.", MachineLanguage.EN to "Caffeine-free.")
    private val DOSE = Regex("^(\\d+(?:[.,]\\d+)?) g (?:de thé pour|of tea for) (\\d+) ml\\.")

    /** Notes découpées pour la fiche d'un thé : la dose en tête, « Sans théine. » (ou en anglais), puis le reste (descriptif, notes à soi). */
    data class NoteParts(val grams: Double?, val gramsForMl: Int?, val caffeineFree: Boolean, val text: String)

    fun splitNotes(notes: String): NoteParts {
        var rest = notes.trim()
        var grams: Double? = null
        var forMl: Int? = null
        DOSE.find(rest)?.let { m ->
            grams = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            forMl = m.groupValues[2].toIntOrNull()
            rest = rest.substring(m.range.last + 1).trim()
        }
        val freePrefix = CAFFEINE_FREE.values.firstOrNull { rest.startsWith(it) }
        if (freePrefix != null) rest = rest.removePrefix(freePrefix).trim()
        val free = freePrefix != null
        return NoteParts(grams, forMl, free, rest)
    }

    /** Dose ramenée à un autre volume d'eau, arrondie à 0,5 g. */
    fun scaleDose(grams: Double, fromMl: Int, toMl: Int): Double =
        if (fromMl <= 0) grams else Math.round(grams * toMl / fromMl * 2) / 2.0

    /**
     * Notes d'un thé après avoir repris une fiche : vides ou automatiques (préréglage repris de la machine,
     * fiche reprise avant, dans `replaceable`) → celles de la fiche ; écrites à la main → gardées, suivies du
     * descriptif de la fiche s'il n'y est pas déjà.
     */
    fun mergeNotes(current: String, entry: CatalogEntry, replaceable: Set<String> = emptySet(), language: MachineLanguage = MachineLanguage.FR): String {
        val c = current.trim()
        return when {
            c.isEmpty() || IMPORT_NOTE_PREFIXES.any { c.startsWith(it) } || c in replaceable -> notesFor(entry, language)
            entry.description.isBlank() || c.contains(entry.description) -> c
            else -> (c + "\n" + entry.description).take(NOTES_MAX)
        }
    }

    /** « 3 », « 2,5 » en français, « 2.5 » en anglais. */
    fun formatGramsLabel(g: Double, language: MachineLanguage = MachineLanguage.FR): String =
        if (g % 1.0 == 0.0) g.toInt().toString() else if (language == MachineLanguage.EN) g.toString() else g.toString().replace('.', ',')

    /**
     * Fiche qui correspond au nom écrit, d'après son nom ou ses autres noms : « Sencha du matin » trouve
     * « Sencha ». Le nom le plus long gagne (« Darjeeling d'été » préfère « Darjeeling » à plus court).
     */
    fun lookup(entries: List<CatalogEntry>, typed: String): CatalogEntry? {
        val n = normalizeForSearch(typed)
        if (n.length < 3) return null
        var best: CatalogEntry? = null
        var bestLen = 0
        for (e in entries) {
            for (k in e.searchKeys) {
                if ((containsWord(n, k) || containsWord(k, n)) && k.length > bestLen) { best = e; bestLen = k.length }
            }
        }
        return best
    }

    /**
     * Suggestions pendant la saisie du nom (2 lettres au moins) : d'abord les noms ou autres noms qui
     * commencent par le texte, puis ceux dont un mot commence par lui, puis ceux qui le contiennent ;
     * à rang égal, le nom le plus court. Une fiche n'apparaît qu'une fois.
     */
    fun suggest(entries: List<CatalogEntry>, typed: String, limit: Int = 5): List<CatalogEntry> {
        val n = normalizeForSearch(typed)
        if (n.length < 2) return emptyList()
        val ranked = entries.mapNotNull { e ->
            val rank = e.searchKeys.minOfOrNull { k ->
                when {
                    k.startsWith(n) -> 0
                    " $k".contains(" $n") -> 1
                    k.contains(n) -> 2
                    else -> 3
                }
            } ?: 3
            if (rank < 3) Triple(e, rank, e.name.length) else null
        }
        return ranked.sortedWith(compareBy({ it.second }, { it.third }, { it.first.name })).take(limit).map { it.first }
    }

    private fun containsWord(haystack: String, needle: String): Boolean =
        (" $haystack ").contains(" $needle ") || (needle.length >= 4 && haystack.contains(needle))

    /**
     * Adresse de base des fichiers bruts d'un dépôt GitHub :
     * https://github.com/u/r → https://raw.githubusercontent.com/u/r/main/
     * https://github.com/u/r/tree/branche/dossier → https://raw.githubusercontent.com/u/r/branche/dossier/
     * Autre adresse : null.
     */
    fun rawBase(url: String): String? {
        val u = url.trim().removeSuffix("/")
        val gh = GITHUB.find(u) ?: return null
        val (user, repo, branch, path) = gh.destructured
        return "https://raw.githubusercontent.com/$user/$repo/${branch.ifEmpty { "main" }}${path}/"
    }

    private val GITHUB = Regex("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?(?:/(?:tree|blob)/([^/]+)(/.*)?)?$")

    /** Fichiers bruts de la base de thés. */
    val RAW_BASE: String = checkNotNull(rawBase(REPO_URL))

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
}
