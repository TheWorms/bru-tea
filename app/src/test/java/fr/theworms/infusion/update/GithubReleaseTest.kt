package fr.theworms.infusion.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lecture de `releases/latest` de GitHub et rythme des appels. */
class GithubReleaseTest {
    private fun release(tag: String, vararg assets: String, extra: String = "") = """
        {"tag_name": "$tag", "draft": false, "prerelease": false $extra,
         "assets": [${assets.joinToString(",") { """{"name": "$it", "browser_download_url": "https://github.com/TheWorms/bru-tea/releases/download/$tag/$it", "size": 3}""" }}]}
    """.trimIndent()

    @Test fun `release complète - version, APK et empreinte`() {
        val r = GithubReleases.parseLatest(release("v0.1.20", "Infusion.0.1.20.apk", "Infusion.0.1.20.apk.sha256"))!!
        assertEquals(Version(0, 1, 20), r.version)
        assertTrue(r.apkUrl.endsWith("/v0.1.20/Infusion.0.1.20.apk"))
        assertTrue(r.sha256Url.endsWith("/v0.1.20/Infusion.0.1.20.apk.sha256"))
        // Tag sans « v » accepté ; la version suit le tag, pas l'ordre des assets.
        assertEquals(Version(1, 0, 0), GithubReleases.parseLatest(release("1.0.0", "Infusion.1.0.0.apk.sha256", "Infusion.1.0.0.apk"))!!.version)
    }

    @Test fun `release inutilisable - rien à installer`() {
        // Empreinte absente, APK d'une autre version, tag illisible, brouillon, préversion, JSON cassé.
        assertNull(GithubReleases.parseLatest(release("v0.1.20", "Infusion.0.1.20.apk")))
        assertNull(GithubReleases.parseLatest(release("v0.1.20", "Infusion.0.1.19.apk", "Infusion.0.1.19.apk.sha256")))
        assertNull(GithubReleases.parseLatest(release("latest", "Infusion.0.1.20.apk", "Infusion.0.1.20.apk.sha256")))
        assertNull(GithubReleases.parseLatest(release("v0.1.20", "Infusion.0.1.20.apk", "Infusion.0.1.20.apk.sha256").replace("\"draft\": false", "\"draft\": true")))
        assertNull(GithubReleases.parseLatest(release("v0.1.20", "Infusion.0.1.20.apk", "Infusion.0.1.20.apk.sha256").replace("\"prerelease\": false", "\"prerelease\": true")))
        assertNull(GithubReleases.parseLatest("<html>rate limited</html>"))
    }

    @Test fun `au plus un appel à GitHub toutes les 15 minutes`() {
        assertTrue(GithubReleases.mayCall(0, 1_000))
        assertFalse(GithubReleases.mayCall(1_000, 1_000 + 14 * 60_000))
        assertTrue(GithubReleases.mayCall(1_000, 1_000 + 15 * 60_000))
    }

    @Test fun `une version plus récente se compare correctement`() {
        assertTrue(Version(0, 1, 20) > Version(0, 1, 19))
        assertTrue(Version(0, 2, 0) > Version(0, 1, 20))
        assertFalse(Version(0, 1, 19) > Version(0, 1, 19))
    }
}
