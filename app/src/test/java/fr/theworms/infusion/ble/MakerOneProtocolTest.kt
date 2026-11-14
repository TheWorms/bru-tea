package fr.theworms.infusion.ble

import fr.theworms.infusion.text.text
import fr.theworms.infusion.R
import fr.theworms.infusion.domain.MachineLimits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exemples calculés d'après le code de BRU Connect 1.13.0 : à confirmer sur la machine. */
class MakerOneProtocolTest {
    private fun hex(s: String): ByteArray = s.split(' ').filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()
    private fun zeros(n: Int) = ByteArray(n)

    @Test fun `demande d'état et petites commandes`() {
        assertArrayEquals(hex("FF 04 44 83"), MakerOneProtocol.statusRequest())
        assertArrayEquals(hex("FF 04 42 81"), MakerOneProtocol.cancel())
        assertArrayEquals(hex("FF 05 46 03 89"), MakerOneProtocol.button(MakerOneProtocol.BUTTON_BACK))
        assertArrayEquals(hex("FF 05 46 04 88"), MakerOneProtocol.button(MakerOneProtocol.BUTTON_OK))
    }

    @Test fun `infusion 80 degrés 250 ml 90 s, forme courte`() {
        val codes = BrewCodes(MakerOneProtocol.temperatureCode(80), MakerOneProtocol.timeCode(90), MakerOneProtocol.waterCode(250, false))
        assertEquals(BrewCodes(8, 8, 4), codes)
        assertArrayEquals(hex("FF 0F 40 00 08 08 04 00 00 00 01 00 00 00 A7"), MakerOneProtocol.brew(codes, null))
    }

    @Test fun `infusion forme longue avec identité`() {
        val id = BrewIdentity(hex("01 02 03 04 05 06 07 08"), "Freestyle")
        val f = MakerOneProtocol.brew(BrewCodes(8, 8, 4), id)
        assertEquals(75, f.size)
        val name = hex("46 00 72 00 65 00 65 00 73 00 74 00 79 00 6C 00 65 00") + zeros(30)
        val expected = hex("FF 4B 40 00 08 08 04 00 00 00 01 00 00 00 01 02 03 04 05 06 07 08") + name + hex("FF FF FF FF B4")
        assertArrayEquals(expected, f)
    }

    @Test fun `préréglage 1 Sencha, formes courte et longue`() {
        val name = hex("53 00 65 00 6E 00 63 00 68 00 61 00") + zeros(36)
        assertArrayEquals(hex("FF 38 52 01 08 08 04") + name + hex("2A"), MakerOneProtocol.presetWrite(1, BrewCodes(8, 8, 4), "Sencha", null))
        assertArrayEquals(
            hex("FF 3C 52 01 08 08 04") + name + hex("34 62 FA D5 9D"),
            MakerOneProtocol.presetWrite(1, BrewCodes(8, 8, 4), "Sencha", 3589956148L),
        )
    }

    @Test fun `surnom, réglages et heure`() {
        val name = hex("4D 00 61 00 6B 00 65 00 72 00 20 00 4F 00 6E 00 65 00") + zeros(30)
        assertArrayEquals(hex("FF 38 4A") + name + zeros(4) + hex("77"), MakerOneProtocol.nickname("Maker One"))
        assertArrayEquals(hex("FF 08 2C 01 01 00 0A FB"), MakerOneProtocol.setup(MachineLanguage.FR, metric = true, presets = 10))
        assertArrayEquals(hex("FF 08 08 40 6C F7 6A DE"), MakerOneProtocol.setTime(1_794_600_000L))
    }

    @Test fun `le surnom est coupé à 12 caractères`() {
        val f = MakerOneProtocol.nickname("Une machine trop longue")
        assertEquals('U'.code.toByte(), f[3])
        assertEquals(0.toByte(), f[3 + 2 * 12]) // 13e caractère absent
    }

    @Test fun `codes de température, d'eau et de durée`() {
        assertEquals(1, MakerOneProtocol.temperatureCode(45))
        assertEquals(11, MakerOneProtocol.temperatureCode(95))
        assertEquals(12, MakerOneProtocol.temperatureCode(100))
        // Moins de 100 ml est ramené à 100 ml (minimum de BRU).
        assertEquals(1, MakerOneProtocol.waterCode(50, true))
        assertEquals(1, MakerOneProtocol.waterCode(100, true))
        assertEquals(6, MakerOneProtocol.waterCode(350, true))
        assertEquals(129, MakerOneProtocol.waterCode(125, true))
        assertEquals(133, MakerOneProtocol.waterCode(325, true))
        // Sans demi-pas, 325 ml devient 350 ml et 75 ml devient 100 ml.
        assertEquals(6, MakerOneProtocol.waterCode(325, false))
        assertEquals(1, MakerOneProtocol.waterCode(75, false))
        assertEquals(0, MakerOneProtocol.timeCode(10))
        assertEquals(5, MakerOneProtocol.timeCode(60))
        assertEquals(8, MakerOneProtocol.timeCode(90))
        assertEquals(11, MakerOneProtocol.timeCode(120))
        assertEquals(58, MakerOneProtocol.timeCode(590))
        assertEquals(59, MakerOneProtocol.timeCode(600))
        assertEquals(109, MakerOneProtocol.timeCode(3600))
        assertEquals(110, MachineLimits.steepDurations.size)
    }

    @Test fun `textes ramenés au Latin-1`() {
        assertEquals("Thé d'été ? ok", MakerOneProtocol.latin1("Thé d’été € ok"))
        val t = MakerOneProtocol.text48("é")
        assertEquals(0xE9.toByte(), t[0])
        assertEquals(0.toByte(), t[1])
    }

    @Test fun `lecture des réponses`() {
        val status = MakerOneProtocol.frame(MakerOneProtocol.STATUS, hex("05 1A 00"), 7)
        val s = MakerOneProtocol.parse(status)
        assertTrue(s is Incoming.Status)
        s as Incoming.Status
        assertEquals(5, s.status)
        assertEquals(26, s.subStatus)
        assertTrue(s.valid)
        val r = MakerOneProtocol.parse(MakerOneProtocol.frame(MakerOneProtocol.RESULT_PRESET, hex("00"), 5))
        assertEquals(Incoming.PresetResult(0, true), r)
        val bad = hex("FF 05 41 10 00")
        val b = MakerOneProtocol.parse(bad)
        assertEquals(16, (b as Incoming.BrewResult).code)
        assertFalse(b.valid)
    }

    @Test fun `dates de firmware`() {
        assertEquals(260629, MakerOneProtocol.firmwareKey("290626-1.10"))
        assertNull(MakerOneProtocol.firmwareKey("v1.2"))
        assertTrue(MakerOneProtocol.firmwareAtLeast("290626", MakerOneProtocol.FW_EXTENDED))
        assertTrue(MakerOneProtocol.firmwareAtLeast("011226", MakerOneProtocol.FW_EXTENDED))
        assertFalse(MakerOneProtocol.firmwareAtLeast("280626", MakerOneProtocol.FW_EXTENDED))
        assertFalse(MakerOneProtocol.firmwareAtLeast(null, MakerOneProtocol.FW_NICKNAME))
    }

    @Test fun `lecture supposée d'un préréglage, et réponses écartées`() {
        // Réponse hypothétique disposée comme l'écriture : code 81, n° 2, durée 8 (1:30), 80 °C, 250 ml, « Sencha ».
        val reply = MakerOneProtocol.frame(81, byteArrayOf(2, 8, 8, 4) + MakerOneProtocol.text48("Sencha"), 56)
        assertEquals(MachinePreset(2, "Sencha", 80, 250, 90), MakerOneProtocol.parsePresetReply(reply, 2))
        // Mauvais numéro, valeurs hors table, nom vide, état : rien n'est importé.
        assertNull(MakerOneProtocol.parsePresetReply(reply, 3))
        assertNull(MakerOneProtocol.parsePresetReply(MakerOneProtocol.frame(81, byteArrayOf(2, 120, 8, 4) + MakerOneProtocol.text48("X"), 56), 2))
        assertNull(MakerOneProtocol.parsePresetReply(MakerOneProtocol.frame(81, byteArrayOf(2, 8, 8, 4) + ByteArray(48), 56), 2))
        assertNull(MakerOneProtocol.parsePresetReply(MakerOneProtocol.frame(MakerOneProtocol.STATUS, byteArrayOf(2, 8, 8, 4) + MakerOneProtocol.text48("X"), 56), 2))
        assertArrayEquals(hex("FF 05 50 01 91"), MakerOneProtocol.presetRead(1))
    }

    @Test fun `décodage des codes de la machine`() {
        assertEquals(45, MakerOneProtocol.temperatureFromCode(1))
        assertEquals(100, MakerOneProtocol.temperatureFromCode(12))
        assertNull(MakerOneProtocol.temperatureFromCode(0))
        assertEquals(350, MakerOneProtocol.waterFromCode(6))
        assertEquals(75, MakerOneProtocol.waterFromCode(128))
        assertNull(MakerOneProtocol.waterFromCode(7))
        assertEquals("Thé vert", MakerOneProtocol.textFrom48(MakerOneProtocol.text48("Thé vert")))
    }

    @Test fun `lecture et écriture des réglages de la machine`() {
        assertArrayEquals(hex("FF 04 2A E9"), MakerOneProtocol.setupRead())
        // Exemple de la doc : fr, métrique, time 0, 10 préréglages.
        assertArrayEquals(hex("FF 08 2C 01 01 00 0A FB"), MakerOneProtocol.setup(1, true, 10))
        assertArrayEquals(MakerOneProtocol.setup(MachineLanguage.FR, true, 10), MakerOneProtocol.setup(1, true, 10))
        // Réponse supposée de même forme (code 43) : anglais, impérial, 9 préréglages.
        val reply = MakerOneProtocol.frame(43, byteArrayOf(0, 0, 0, 9), 8)
        assertEquals(MachineSetup(0, false, 0, 9), MakerOneProtocol.parseSetupReply(reply))
        assertEquals(MachineLanguage.EN, MakerOneProtocol.parseSetupReply(reply)?.language)
        // Machine en allemand (2) : langue non proposée par l'app.
        assertNull(MakerOneProtocol.parseSetupReply(MakerOneProtocol.frame(43, byteArrayOf(2, 1, 0, 9), 8))?.language)
        assertEquals(MachineLanguage.FR, MachineLanguage.ofTag("fr-FR"))
        assertEquals(MachineLanguage.EN, MachineLanguage.ofTag("en"))
        assertNull(MachineLanguage.ofTag("de"))
        // Valeurs impossibles ou autre code : rien n'est repris.
        assertNull(MakerOneProtocol.parseSetupReply(MakerOneProtocol.frame(43, byteArrayOf(7, 1, 0, 9), 8)))
        assertNull(MakerOneProtocol.parseSetupReply(MakerOneProtocol.frame(43, byteArrayOf(1, 1, 0, 30), 8)))
        assertNull(MakerOneProtocol.parseSetupReply(MakerOneProtocol.frame(69, byteArrayOf(1, 1, 0, 9), 8)))
    }

    @Test fun `alertes de la machine pour l'accueil`() {
        assertEquals(AlertKind.WATER, MachineTexts.alert(MachineTexts.WARNING, 124)?.kind)
        assertEquals(text(R.string.alert_lid_close_title), MachineTexts.alert(MachineTexts.WARNING, 122)?.title)
        assertTrue(MachineTexts.alert(MachineTexts.ERROR, 0)!!.error)
        assertFalse(MachineTexts.alert(MachineTexts.WARNING, 120)!!.error)
        // En veille ou en infusion normale : rien à signaler.
        assertNull(MachineTexts.alert(MachineTexts.STANDBY, 4))
        assertNull(MachineTexts.alert(MachineTexts.BREWING, MachineTexts.SUB_STEEPING))
    }

    @Test fun `trames lues avec la tolérance de l'app de BRU`() {
        // Un état se lit dès 5 octets, un résultat dès 4 ; la somme n'est que notée.
        val status = MakerOneProtocol.frame(MakerOneProtocol.STATUS, byteArrayOf(5, 26), 6)
        assertEquals(Incoming.Status(5, 26, true), MakerOneProtocol.parse(status))
        assertEquals(5, (MakerOneProtocol.parse(hex("FF 05 45 05 1A")) as Incoming.Status).status)
        assertEquals(Incoming.BrewResult(0, false), MakerOneProtocol.parse(hex("FF 04 41 00")))
        assertTrue(MakerOneProtocol.parse(hex("FF 04 45 05")) is Incoming.Unknown)
        assertTrue(MakerOneProtocol.parse(hex("FF 41 00")) is Incoming.Unknown)
        // Somme fausse ou longueur annoncée différente : lue quand même, marquée invalide.
        val bad = status.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertEquals(26, (MakerOneProtocol.parse(bad) as Incoming.Status).subStatus)
        assertFalse(MakerOneProtocol.parse(bad).valid)
    }

    @Test fun `notification découpée seulement si toutes les longueurs sont plausibles`() {
        val a = MakerOneProtocol.frame(MakerOneProtocol.STATUS, byteArrayOf(1, 4), 6)
        val b = MakerOneProtocol.frame(MakerOneProtocol.RESULT_BREW, byteArrayOf(0), 5)
        val parts = MakerOneProtocol.split(a + b)
        assertEquals(2, parts.size)
        assertArrayEquals(a, parts[0])
        assertArrayEquals(b, parts[1])
        assertEquals(1, MakerOneProtocol.split(a).size)
        // Reste illisible, ou longueur annoncée plus courte que la trame : la notification reste entière.
        val junk = hex("01 02 03")
        assertArrayEquals(junk, MakerOneProtocol.split(junk).single())
        assertArrayEquals(a + junk, MakerOneProtocol.split(a + junk).single())
        val shortLen = hex("FF 05 45 05 1A 00 00")
        assertArrayEquals(shortLen, MakerOneProtocol.split(shortLen).single())
    }
}
