package fr.theworms.infusion.domain

import java.text.Normalizer

private val MARKS = Regex("\\p{M}+")
// Lettres et chiffres de toutes les écritures gardés (玉露, чай) ; sans (?U) ni UNICODE_CHARACTER_CLASS, refusés par Android.
private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
/** Ligatures que la forme NFD ne décompose pas : « cœur » et « coeur » se trouvent l'un l'autre. */
private val LIGATURES = mapOf('œ' to "oe", 'æ' to "ae", 'ß' to "ss")

/** Minuscules, sans accents ni ligatures, ponctuation remplacée par des espaces : « Thé d’été » → « the d ete ». */
fun normalizeForSearch(text: String): String {
    val lower = text.lowercase()
    val unbound = if (lower.none { it in LIGATURES }) lower else buildString { lower.forEach { c -> append(LIGATURES[c] ?: c) } }
    return Normalizer.normalize(unbound, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace(NON_WORD, " ")
        .trim()
}
