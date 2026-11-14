package fr.theworms.infusion.catalog

import fr.theworms.infusion.R
import fr.theworms.infusion.ble.MachineLanguage
import fr.theworms.infusion.domain.TeaType
import fr.theworms.infusion.text.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fiches au format de tea-db (FORMAT.md : docs/thes.json, une liste). */
class CatalogTest {
    private val sample = """
        [
          {"id": "black-assam", "nom": "Assam", "famille": "black", "origine": "Inde (Assam)", "eau_ml": 250, "the_g": 3.0,
           "temperature_c": 95, "duree_s": 240, "duree_max_s": 300, "fiabilite": "legacy_referenced",
           "photo": "assets/photos/catalogue/black-assam.webp", "photo_credit": "Théothèque (IA)", "photo_exacte": false,
           "notes": "Liqueur sombre et maltée.", "source": "https://example.org", "alias": ["Assam (méthode Palais)"], "theine": "oui"},
          {"id": "green-gokou-sencha", "nom": "Gokou Sencha", "famille": "green", "origine": "Japon", "eau_ml": 70, "the_g": 2.0,
           "temperature_c": 82, "duree_s": 75, "duree_max_s": 90, "photo": null, "photo_credit": null, "notes": "Vert japonais.",
           "theine": "oui", "nom_complet": "Sencha Gokou de Kagoshima"},
          {"id": "herbal-verveine", "nom": "Verveine", "famille": "herbal", "origine": null, "eau_ml": 300, "the_g": 2.0,
           "temperature_c": 100, "duree_s": 360, "duree_max_s": 480, "photo": null, "photo_credit": null, "notes": "",
           "theine": "non", "precaution": "Déconseillée aux femmes enceintes."},
          {"id": "matcha-usucha", "nom": "Matcha", "famille": "matcha", "origine": "Japon", "eau_ml": 70, "the_g": 2.0,
           "temperature_c": 80, "duree_s": 0, "duree_max_s": 0, "photo": null, "photo_credit": null, "notes": "", "theine": "oui"},
          {"id": "cold-brew", "nom": "Thé infusé à froid", "famille": "cold", "origine": null, "eau_ml": 350, "the_g": 5.0,
           "temperature_c": null, "duree_s": 7200, "duree_max_s": 7200, "photo": null, "photo_credit": null, "notes": "", "theine": "oui"},
          {"id": "black-assam", "nom": "Doublon", "famille": "black", "eau_ml": 250, "temperature_c": 95, "duree_s": 240},
          {"id": "sans-nom", "famille": "black", "eau_ml": 250, "temperature_c": 95, "duree_s": 240},
          {"id": "evasion", "nom": "Évasion", "famille": "black", "eau_ml": 250, "temperature_c": 95, "duree_s": 240, "photo": "../secret"}
        ]
    """.trimIndent()

    @Test fun `lecture des fiches, corrections pour la machine, fiches écartées`() {
        val p = Catalog.parse(sample)
        assertEquals(6, p.entries.size)
        assertEquals(2, p.rejected)
        val assam = p.entries.first { it.id == "black-assam" }
        assertEquals(TeaType.BLACK, assam.type)
        assertEquals("Inde (Assam)", assam.origin)
        assertEquals(95, assam.temperature); assertEquals(250, assam.waterMl); assertEquals(240, assam.steepSeconds)
        assertEquals("assets/photos/catalogue/black-assam.webp", assam.photo)
        assertEquals("Théothèque (IA)", assam.photoCredit)
        assertFalse(assam.adjusted)
        assertNull(assam.unsuitable)
        val gokou = p.entries.first { it.id == "green-gokou-sencha" }
        assertEquals(80, gokou.temperature)   // 82 → 80
        assertEquals(100, gokou.waterMl)      // 70 → 100 (minimum de BRU)
        assertEquals(70, gokou.steepSeconds)  // 75 → 70 (durées de la machine ; à égalité, la plus courte)
        assertTrue(gokou.adjusted)
        assertNull(gokou.photo)
        assertEquals("", gokou.photoCredit)
        assertNull(p.entries.first { it.id == "evasion" }.photo)
    }

    @Test fun `matcha et infusion à froid sont signalés`() {
        val e = Catalog.parse(sample).entries
        assertNotNull(e.first { it.id == "matcha-usucha" }.unsuitable)
        assertNotNull(e.first { it.id == "cold-brew" }.unsuitable)
        assertFalse(e.first { it.id == "cold-brew" }.adjusted)
    }

    @Test fun `notes de départ avec dose adaptée, théine, précaution, descriptif`() {
        val e = Catalog.parse(sample).entries
        assertEquals("3 g de thé pour 250 ml. Liqueur sombre et maltée.", Catalog.notesFor(e.first { it.id == "black-assam" }))
        // 2 g pour 70 ml, ramenés à 100 ml : 2,86 g arrondis à 3 g.
        assertTrue(Catalog.notesFor(e.first { it.id == "green-gokou-sencha" }).startsWith("3 g de thé pour 100 ml."))
        assertEquals("2 g de thé pour 300 ml. Sans théine. Déconseillée aux femmes enceintes.", Catalog.notesFor(e.first { it.id == "herbal-verveine" }))
    }

    @Test fun `recherche d'après le nom ou un autre nom`() {
        val e = Catalog.parse(sample).entries
        assertEquals("black-assam", Catalog.lookup(e, "Assam du matin")?.id)
        assertEquals("black-assam", Catalog.lookup(e, "assam methode palais")?.id)
        assertEquals("green-gokou-sencha", Catalog.lookup(e, "Sencha Gokou de Kagoshima")?.id)
        assertEquals("herbal-verveine", Catalog.lookup(e, "verveine du soir")?.id)
        assertNull(Catalog.lookup(e, "Thé de mamie"))
        assertNull(Catalog.lookup(e, "th"))
    }

    @Test fun `le nom exact passe avant les noms qui le contiennent`() {
        // Cas relevés dans tea-db : le nom le plus long gagnait, même quand c'était lui qui contenait le texte tapé.
        val e = Catalog.parse("""
            [
              {"id": "black-darjeeling-first-flush", "nom": "Darjeeling First Flush", "famille": "black", "eau_ml": 250,
               "temperature_c": 85, "duree_s": 180, "alias": ["Darjeeling de printemps (méthode Palais)"]},
              {"id": "black-darjeeling", "nom": "Darjeeling", "famille": "black", "eau_ml": 250, "temperature_c": 90, "duree_s": 180},
              {"id": "herbal-rooibos-citron-gingembre", "nom": "Rooibos citron gingembre", "famille": "herbal", "eau_ml": 250,
               "temperature_c": 100, "duree_s": 300},
              {"id": "herbal-rooibos", "nom": "Rooibos", "famille": "herbal", "eau_ml": 250, "temperature_c": 100, "duree_s": 300},
              {"id": "black-the-noir-a-la-cannelle", "nom": "Thé noir à la cannelle", "famille": "black", "eau_ml": 250,
               "temperature_c": 95, "duree_s": 240},
              {"id": "herbal-cannelle", "nom": "Cannelle", "famille": "herbal", "eau_ml": 250, "temperature_c": 100, "duree_s": 600},
              {"id": "cold-the-noir-glace", "nom": "Thé noir glacé", "famille": "cold", "eau_ml": 350, "temperature_c": null, "duree_s": 7200},
              {"id": "cold-the-vert-glace", "nom": "Thé vert glacé", "famille": "cold", "eau_ml": 350, "temperature_c": null, "duree_s": 3600},
              {"id": "green-the-vert-a-la-peche", "nom": "Thé vert à la pêche", "famille": "green", "eau_ml": 250,
               "temperature_c": 80, "duree_s": 180},
              {"id": "herbal-menthe-verte", "nom": "Menthe verte", "famille": "herbal", "eau_ml": 250, "temperature_c": 100, "duree_s": 300},
              {"id": "black-french-earl-grey", "nom": "French Earl Grey", "famille": "black", "eau_ml": 250, "temperature_c": 95, "duree_s": 240},
              {"id": "black-earl-grey", "nom": "Earl Grey", "famille": "black", "eau_ml": 250, "temperature_c": 95, "duree_s": 240},
              {"id": "green-gokou-sencha", "nom": "Gokou Sencha", "famille": "green", "eau_ml": 100, "temperature_c": 80, "duree_s": 70}
            ]
        """.trimIndent()).entries
        assertEquals("black-darjeeling", Catalog.lookup(e, "Darjeeling")?.id)
        assertEquals("black-darjeeling", Catalog.lookup(e, "Darjeeling d'été")?.id)
        assertEquals("black-darjeeling-first-flush", Catalog.lookup(e, "Darjeeling First Flush du jardin")?.id)
        assertEquals("herbal-rooibos", Catalog.lookup(e, "Rooibos")?.id)
        assertEquals("herbal-cannelle", Catalog.lookup(e, "Cannelle")?.id)
        // Un mot seul trop court ne choisit pas au hasard parmi tous les noms qui le contiennent.
        assertNull(Catalog.lookup(e, "Thé"))
        // Début de nom : le plus court gagne ; jamais un milieu de mot (« the vert » dans « menthe verte »).
        assertEquals("black-earl-grey", Catalog.lookup(e, "Earl")?.id)
        assertEquals("green-gokou-sencha", Catalog.lookup(e, "Gokou")?.id)
        // Début de nom de plusieurs mots : rien, ni l'infusion à froid (clé la plus courte, comme dans tea-db), ni une autre.
        assertNull(Catalog.lookup(e, "Thé vert"))
        assertNull(Catalog.lookup(e, "Thé noir"))
        // Un seul mot : jamais une fiche inadaptée devinée (« Glacé » ne remplit pas une infusion à froid).
        assertNull(Catalog.lookup(e, "Glacé"))
        // Nom exact ou contenu dans le texte : la fiche inadaptée reste trouvée (l'avertissement s'affiche).
        assertEquals("cold-the-vert-glace", Catalog.lookup(e, "Thé vert glacé")?.id)
        assertEquals("cold-the-noir-glace", Catalog.lookup(e, "Thé noir glacé maison")?.id)
        assertEquals("green-the-vert-a-la-peche", Catalog.lookup(e, "Thé vert à la pêche")?.id)
    }

    @Test fun `dans le vrai fichier de tea-db, chaque nom retrouve une fiche de ce nom`() {
        // Copie de docs/thes.json de tea-db (~/work/tea-db-ref) ; ignoré si le fichier n'est pas là.
        val f = java.io.File(System.getProperty("user.home"), "work/tea-db-ref/docs/thes.json")
        if (!f.exists()) return
        val entries = Catalog.parse(f.readText()).entries
        val wrong = entries.filter { e ->
            val key = fr.theworms.infusion.domain.normalizeForSearch(e.name)
            key.length >= 3 && Catalog.lookup(entries, e.name)?.searchKeys?.contains(key) != true
        }
        assertTrue(wrong.joinToString { it.name }, wrong.isEmpty())
    }

    @Test fun `familles vers types`() {
        assertEquals(TeaType.GREEN, Catalog.typeOfFamily("yellow"))
        assertEquals(TeaType.BLACK, Catalog.typeOfFamily("pu_erh"))
        assertEquals(TeaType.BLACK, Catalog.typeOfFamily("dark"))
        assertEquals(TeaType.HERBAL, Catalog.typeOfFamily("herbal"))
    }

    @Test fun `adresse du dépôt vers les fichiers bruts`() {
        assertEquals("https://raw.githubusercontent.com/thib/thes/main/", Catalog.rawBase("https://github.com/thib/thes"))
        assertEquals("https://raw.githubusercontent.com/thib/thes/main/", Catalog.rawBase("https://github.com/thib/thes.git"))
        assertEquals("https://raw.githubusercontent.com/thib/thes/dev/base/", Catalog.rawBase("https://github.com/thib/thes/tree/dev/base/"))
        assertEquals("https://raw.githubusercontent.com/TheWorms/tea-db/main/", Catalog.rawBase("https://github.com/TheWorms/tea-db"))
        assertNull(Catalog.rawBase("http://github.com/thib/thes"))
        assertNull(Catalog.rawBase(""))
        assertNull(Catalog.rawBase("https://example.org/thib/thes"))
        assertEquals("https://raw.githubusercontent.com/TheWorms/tea-db/main/", Catalog.RAW_BASE)
    }

    @Test fun `le vrai fichier de tea-db se lit en entier`() {
        // Copie de docs/thes.json de tea-db (~/work/tea-db-ref) ; ignoré si le fichier n'est pas là.
        val f = java.io.File(System.getProperty("user.home"), "work/tea-db-ref/docs/thes.json")
        if (!f.exists()) return
        val text = f.readText()
        val p = Catalog.parse(text)
        assertEquals(0, p.rejected)
        assertEquals(kotlinx.serialization.json.Json.parseToJsonElement(text).let { it as kotlinx.serialization.json.JsonArray }.size, p.entries.size)
    }

    @Test fun `suggestions pendant la saisie du nom`() {
        val entries = Catalog.parse(sample).entries
        assertTrue(Catalog.suggest(entries, "a").isEmpty())
        assertEquals(listOf("Assam"), Catalog.suggest(entries, "ass").map { it.name })
        // Un mot du nom qui commence par le texte, sans tenir compte des accents.
        assertEquals("Gokou Sencha", Catalog.suggest(entries, "sen").first().name)
        assertEquals("Évasion", Catalog.suggest(entries, "eva").first().name)
        assertTrue(Catalog.suggest(entries, "zzzz").isEmpty())
    }

    @Test fun `description reprise même si le thé a déjà des notes`() {
        val assam = Catalog.parse(sample).entries.first { it.id == "black-assam" }
        val fresh = Catalog.notesFor(assam)
        // Vides ou note du préréglage repris de la machine : remplacées.
        assertEquals(fresh, Catalog.mergeNotes("", assam))
        assertEquals(fresh, Catalog.mergeNotes("Repris du préréglage 3 de la machine.", assam))
        // Fiche reprise avant : remplacée.
        assertEquals(fresh, Catalog.mergeNotes("Ancienne fiche.", assam, setOf("Ancienne fiche.")))
        // Écrites à la main : gardées, suivies du descriptif, une seule fois.
        val mine = Catalog.mergeNotes("Mon préféré le matin.", assam)
        assertTrue(mine.startsWith("Mon préféré le matin.\n"))
        if (assam.description.isNotBlank()) assertTrue(mine.endsWith(assam.description))
        assertEquals(mine, Catalog.mergeNotes(mine, assam))
    }

    @Test fun `notes découpées pour la fiche`() {
        val p = Catalog.splitNotes("3 g de thé pour 250 ml. Récolte d'automne, après la mousson.")
        assertEquals(3.0, p.grams!!, 0.0)
        assertEquals(250, p.gramsForMl)
        assertFalse(p.caffeineFree)
        assertEquals("Récolte d'automne, après la mousson.", p.text)
        val herbal = Catalog.splitNotes("2,5 g de thé pour 300 ml. Sans théine. Verveine citronnée.")
        assertEquals(2.5, herbal.grams!!, 0.0)
        assertTrue(herbal.caffeineFree)
        assertEquals("Verveine citronnée.", herbal.text)
        // Notes écrites à la main : rien à découper.
        val mine = Catalog.splitNotes("Mon préféré le matin.")
        assertNull(mine.grams)
        assertEquals("Mon préféré le matin.", mine.text)
        // Dose ramenée au volume du thé.
        assertEquals(3.5, Catalog.scaleDose(3.0, 250, 300), 0.0)
        assertEquals("2,5", Catalog.formatGramsLabel(2.5))
    }

    @Test fun `notes de la base en anglais, relues comme en français`() {
        val e = Catalog.parse(sample).entries
        val assam = e.first { it.id == "black-assam" }
        assertEquals("3 g of tea for 250 ml. Liqueur sombre et maltée.", Catalog.notesFor(assam, MachineLanguage.EN))
        val herbal = Catalog.splitNotes(Catalog.notesFor(e.first { it.id == "herbal-verveine" }, MachineLanguage.EN))
        assertEquals(2.0, herbal.grams!!, 0.0)
        assertEquals(300, herbal.gramsForMl)
        assertTrue(herbal.caffeineFree)
        assertFalse(herbal.text.startsWith("Caffeine-free"))
        assertEquals("2.5", Catalog.formatGramsLabel(2.5, MachineLanguage.EN))
        assertEquals(2.5, Catalog.splitNotes("2.5 g of tea for 300 ml. Caffeine-free.").grams!!, 0.0)
        // Note d'un thé repris de la machine, dans les deux langues : remplacée par la fiche.
        assertEquals(Catalog.notesFor(assam, MachineLanguage.EN), Catalog.mergeNotes("From machine preset 3.", assam, language = MachineLanguage.EN))
        assertEquals(Catalog.notesFor(assam), Catalog.mergeNotes("Repris du préréglage 3 de la machine.", assam))
    }

    @Test fun `un 401 ou un 403 de GitHub n'est qu'un code, sans parler de dépôt privé`() {
        // La base est un dépôt public fixe : ce refus vient d'un proxy, d'un portail Wi-Fi ou d'un blocage.
        assertEquals(text(R.string.catalog_http, 403), Catalog.httpError(403, "thes.json"))
        assertEquals(text(R.string.catalog_http, 401), Catalog.httpError(401, "thes.json"))
        assertEquals(text(R.string.catalog_http, 500), Catalog.httpError(500, "thes.json"))
        assertEquals(text(R.string.catalog_not_found, "thes.json"), Catalog.httpError(404, "thes.json"))
    }

    @Test fun `nouvelles fiches annoncées, sauf au premier chargement`() {
        assertEquals(0, Catalog.countAdded(emptySet(), listOf("a", "b")))
        assertEquals(1, Catalog.countAdded(setOf("a", "b"), listOf("a", "b", "c")))
        assertEquals(0, Catalog.countAdded(setOf("a", "b"), listOf("a")))
    }
}
