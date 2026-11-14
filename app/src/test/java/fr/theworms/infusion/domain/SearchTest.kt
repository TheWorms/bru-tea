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
}
