package fr.theworms.infusion.ui.machine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Date de la dernière vérification de la base (onglet Machine), au motif de chaque langue. */
class FormatCheckTest {
    private val april11 = LocalDateTime.of(2026, 4, 11, 13, 31).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Motif `date_pattern` tel qu'Android le lit (apostrophes déséchappées). */
    private fun pattern(dir: String): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val strings = doc.getElementsByTagName("string")
        return (0 until strings.length).map { strings.item(it) as Element }.first { it.getAttribute("name") == "date_pattern" }
            .textContent.replace("\\'", "'")
    }

    @Test fun `anglais, le mois en lettres, plus de 04-11 lu comme le 4 novembre`() {
        assertEquals("11 Apr at 13:31", formatCheck(april11, pattern("values-en"), Locale.UK))
        assertEquals("11 Apr at 13:31", formatCheck(april11, pattern("values-en"), Locale.US))
    }

    @Test fun `français, jour puis mois`() {
        assertEquals("11/04 à 13:31", formatCheck(april11, pattern("values"), Locale.FRANCE))
    }

    @Test fun `jamais vérifiée, un tiret`() {
        assertEquals("—", formatCheck(0, pattern("values"), Locale.FRANCE))
    }
}
