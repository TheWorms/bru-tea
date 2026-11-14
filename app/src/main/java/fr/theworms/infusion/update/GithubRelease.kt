package fr.theworms.infusion.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Dernière version publiée en Release GitHub : son APK et son empreinte (obligatoire). */
data class GithubRelease(val version: Version, val apkUrl: String, val sha256Url: String)

object GithubReleases {
    /** Dépôt public des versions : https://github.com/TheWorms/bru-tea/releases. */
    const val LATEST_URL = "https://api.github.com/repos/TheWorms/bru-tea/releases/latest"

    /** Sans compte, GitHub accepte 60 appels par heure : pas plus d'un toutes les 15 minutes. */
    const val MIN_INTERVAL_MS = 15 * 60_000L

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Lit la réponse de `releases/latest` : tag « v0.1.20 » (ou « 0.1.20 »), asset `Infusion.0.1.20.apk` et
     * `Infusion.0.1.20.apk.sha256`. Null si la réponse n'a pas cette forme (brouillon, préversion, asset absent).
     */
    fun parseLatest(text: String): GithubRelease? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        if (o.bool("draft") == true || o.bool("prerelease") == true) return null
        val tag = o.str("tag_name") ?: return null
        val version = Version.parse(tag.trim().removePrefix("v").removePrefix("V")) ?: return null
        val assets = (o["assets"] as? JsonArray)?.mapNotNull { a ->
            val ao = a as? JsonObject ?: return@mapNotNull null
            val name = ao.str("name") ?: return@mapNotNull null
            val url = ao.str("browser_download_url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            name to url
        }?.toMap().orEmpty()
        val apk = assets[apkName(version)] ?: return null
        val sha = assets[apkName(version) + ".sha256"] ?: return null
        GithubRelease(version, apk, sha)
    }.getOrNull()

    fun apkName(v: Version) = "Infusion.$v.apk"

    /** Un nouvel appel à GitHub est-il permis (dernier appel il y a plus de 15 minutes) ? */
    fun mayCall(lastCallAt: Long, now: Long): Boolean = lastCallAt == 0L || now - lastCallAt >= MIN_INTERVAL_MS

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
}
