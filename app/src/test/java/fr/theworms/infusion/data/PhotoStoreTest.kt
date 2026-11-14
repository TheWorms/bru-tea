package fr.theworms.infusion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoStoreTest {
    @Test fun `photo ramenée à 1600 px sur son plus grand côté`() {
        assertEquals(1600 to 1200, PhotoStore.fitWithin(4000, 3000, 1600))
        // Capture d'écran très haute : c'est la hauteur qui est bornée.
        assertEquals(86 to 1600, PhotoStore.fitWithin(1080, 20_000, 1600))
        assertEquals(800 to 600, PhotoStore.fitWithin(800, 600, 1600))
    }
}
