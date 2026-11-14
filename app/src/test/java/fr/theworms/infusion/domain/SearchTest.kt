package fr.theworms.infusion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test fun `accents et ponctuation sont ignorés`() {
        assertEquals("the d ete", normalizeForSearch("Thé d’été"))
        assertEquals("aiguilles d argent", normalizeForSearch("  Aiguilles d'argent ! "))
    }

    @Test fun `la recherche trouve un nom sans accent`() {
        assertTrue(normalizeForSearch("Thé de mamie").contains(normalizeForSearch("the de")))
    }

    @Test fun `ligatures décomposées, œ et oe se trouvent l'un l'autre`() {
        assertEquals("coeur de yunnan", normalizeForSearch("Cœur de Yunnan"))
        assertEquals(normalizeForSearch("COEUR"), normalizeForSearch("CŒUR"))
        assertEquals("aegle", normalizeForSearch("Æglé"))
        assertEquals("strasse", normalizeForSearch("Straße"))
    }

    @Test fun `lettres des autres écritures gardées`() {
        assertEquals("玉露", normalizeForSearch("玉露"))
        assertEquals("улун", normalizeForSearch("Улун !"))
        assertEquals("smørbrød 2", normalizeForSearch("Smørbrød (2)"))
        // Une recherche faite de ces lettres n'est plus vide : elle filtre au lieu de tout montrer.
        assertTrue(normalizeForSearch("龍井 de Hangzhou").contains(normalizeForSearch("龍井")))
    }
}
