package fr.theworms.infusion.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class MachineClockTest {
    @Test fun `heure locale comptée comme de l'UTC, avec le vrai décalage`() {
        val paris = ZoneId.of("Europe/Paris")
        // 13/11/2026 19:00 UTC = 20:00 à Paris (hiver, +1 h) : la machine attend 20:00 « UTC ».
        val now = Instant.parse("2026-11-13T19:00:00Z")
        assertEquals(1_794_600_000L, MachineController.localEpochSeconds(now, paris))
        // Juste après minuit à Paris (23:30 UTC la veille) : l'app d'origine se trompait ici, pas nous.
        val midnight = Instant.parse("2026-11-13T23:30:00Z")
        assertEquals(Instant.parse("2026-11-14T00:30:00Z").epochSecond, MachineController.localEpochSeconds(midnight, paris))
    }
}
