package fr.theworms.infusion.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Français (par défaut) et anglais : mêmes textes, mêmes arguments de format. */
class TranslationsTest {
    private val format = Regex("%(\\d+\\$)?[sd]")

    /** Nom → arguments de format (pour les pluriels, ceux de chaque quantité, en une liste triée). */
    private fun read(dir: String): Map<String, List<String>> {
        val f = File("src/main/res/$dir/strings.xml")
        assertTrue("fichier absent : $f", f.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val out = HashMap<String, List<String>>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as Element
            if (e.getAttribute("translatable") == "false") continue
            out[e.getAttribute("name")] = format.findAll(e.textContent).map { it.value }.sorted().toList()
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as Element
            val items = e.getElementsByTagName("item")
            val other = (0 until items.length).map { items.item(it) as Element }.first { it.getAttribute("quantity") == "other" }
            out["plurals:" + e.getAttribute("name")] = format.findAll(other.textContent).map { it.value }.sorted().toList()
        }
        return out
    }

    @Test fun `chaque texte français a sa traduction anglaise, avec les mêmes arguments`() {
        val fr = read("values")
        val en = read("values-en")
        assertTrue(fr.size > 100)
        assertEquals("textes sans traduction ou en trop", fr.keys.sorted(), en.keys.sorted())
        fr.forEach { (name, args) -> assertEquals("arguments de $name", args, en[name]) }
    }
}
