package fr.theworms.infusion.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun `lecture et ordre des versions`() {
        assertEquals(Version(0, 1, 2), Version.parse("0.1.2"))
        assertNull(Version.parse("0.1"))
        assertNull(Version.parse("0.1.x"))
        assertTrue(Version(0, 2, 0) > Version(0, 1, 9))
        assertTrue(Version(1, 0, 0) > Version(0, 9, 9))
    }
}
