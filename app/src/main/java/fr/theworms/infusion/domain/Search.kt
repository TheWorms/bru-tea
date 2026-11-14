package fr.theworms.infusion.domain

import java.text.Normalizer

private val MARKS = Regex("\\p{M}+")
private val NON_WORD = Regex("[^a-z0-9]+")

/** Minuscules, sans accents, ponctuation remplacée par des espaces : « Thé d’été » → « the d ete ». */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace(NON_WORD, " ")
        .trim()
