package fr.theworms.infusion.ble

import fr.theworms.infusion.R
import androidx.annotation.StringRes
import fr.theworms.infusion.domain.MachineLimits
import java.security.MessageDigest

/**
 * Trames de la Maker One, d'après BRU Connect 1.13.0 :
 * `FF`, longueur totale, code, charge, bourrage 00, somme de contrôle.
 * Tout est à confirmer sur la machine : les exemples de MakerOneProtocolTest viennent du code de BRU.
 */
object MakerOneProtocol {
    const val SET_TIME = 8
    const val SETUP_READ = 42
    const val SETUP_WRITE = 44
    const val BREW = 64
    const val RESULT_BREW = 65
    const val CANCEL = 66
    const val STATUS_REQUEST = 68
    const val STATUS = 69
    const val BUTTON = 70
    const val NICKNAME = 74
    const val PRESET_READ = 80
    const val PRESET_WRITE = 82
    const val RESULT_PRESET = 83

    const val BUTTON_BACK = 3
    const val BUTTON_OK = 4

    /** Thé hors catalogue BRU. */
    const val NULL_TEA_ID = 0xFFFFFFFFL

    /** Dates de firmware (JJMMAA) à partir desquelles la machine accepte… */
    const val FW_NICKNAME = "181125"
    const val FW_HALF_WATER = "280526"
    const val FW_EXTENDED = "290626"

    /** Somme de contrôle : complément à deux de la somme des octets 0..n-2, XOR 0x3A. */
    fun checksum(frame: ByteArray, length: Int): Byte {
        var s = 0
        for (i in 0 until length - 1) s += frame[i].toInt() and 0xFF
        return (((-s) and 0xFF) xor 0x3A).toByte()
    }

    fun isValid(frame: ByteArray): Boolean =
        frame.size >= 4 && frame[0] == 0xFF.toByte() && (frame[1].toInt() and 0xFF) == frame.size &&
            checksum(frame, frame.size) == frame.last()

    /** Trame générique : la charge est complétée par des 00 jusqu'à `length − 1`, puis la somme. */
    fun frame(code: Int, payload: ByteArray = ByteArray(0), length: Int = payload.size + 4): ByteArray {
        val out = ByteArray(maxOf(length - 1, 3 + payload.size) + 1)
        out[0] = 0xFF.toByte()
        out[1] = length.toByte()
        out[2] = code.toByte()
        payload.copyInto(out, 3)
        out[out.size - 1] = checksum(out, out.size)
        return out
    }

    fun statusRequest(): ByteArray = frame(STATUS_REQUEST)
    fun cancel(): ByteArray = frame(CANCEL)
    fun button(code: Int): ByteArray = frame(BUTTON, byteArrayOf(code.toByte()))

    /** Infusion, réglages en index de la machine. `identity` présent : forme longue (firmware récent). */
    fun brew(settings: BrewCodes, identity: BrewIdentity?): ByteArray {
        val params = byteArrayOf(
            0, settings.temperature.toByte(), settings.time.toByte(), settings.water.toByte(),
            0, 0, 0, 1, 0, 0, 0, // rinçage, intensité, délai de rinçage : non ; bip de fin : oui
        )
        val payload = if (identity == null) params else params + identity.bytes()
        return frame(BREW, payload, payload.size + 4)
    }

    /** Écriture du préréglage `slot` (1 à 10). Ordre : durée, température, eau. */
    fun presetWrite(slot: Int, settings: BrewCodes, name: String, teaId: Long?): ByteArray {
        require(slot in 1..MachineLimits.PRESETS) { "Préréglage $slot hors de 1..10" }
        var payload = byteArrayOf(slot.toByte(), settings.time.toByte(), settings.temperature.toByte(), settings.water.toByte()) + text48(name)
        if (teaId != null) payload += le32(teaId)
        return frame(PRESET_WRITE, payload, if (teaId != null) 60 else 56)
    }

    /** Demande du préréglage `slot` : la commande existe, mais l'app de BRU ne l'envoie jamais (réponse inconnue). */
    fun presetRead(slot: Int): ByteArray = frame(PRESET_READ, byteArrayOf(slot.toByte()))

    /**
     * Essai de lecture d'une réponse de préréglage, en supposant la même disposition que l'écriture :
     * code, n°, durée, température, eau, nom sur 48 octets. Renvoie null si la réponse ne ressemble pas
     * à un préréglage cohérent (rien n'est alors importé).
     */
    fun parsePresetReply(frame: ByteArray, slot: Int): MachinePreset? {
        if (frame.size < 7 + 48 || frame[0] != 0xFF.toByte()) return null
        val code = frame[2].toInt() and 0xFF
        if (code == STATUS || code == RESULT_BREW || code == RESULT_PRESET) return null
        if ((frame[3].toInt() and 0xFF) != slot) return null
        val seconds = MachineLimits.steepDurations.getOrNull(frame[4].toInt() and 0xFF) ?: return null
        val celsius = temperatureFromCode(frame[5].toInt() and 0xFF) ?: return null
        val ml = waterFromCode(frame[6].toInt() and 0xFF) ?: return null
        val name = textFrom48(frame.copyOfRange(7, 55))
        if (name.isBlank() || name.any { it.code < 0x20 }) return null
        return MachinePreset(slot, name, celsius, ml, seconds)
    }

    fun temperatureFromCode(code: Int): Int? = when (code) { in 1..11 -> 40 + 5 * code; 12 -> MachineLimits.TEMP_MAX; else -> null }

    fun waterFromCode(code: Int): Int? = when (code) { in 0..6 -> 50 * (code + 1); in 128..133 -> 75 + 50 * (code - 128); else -> null }

    /** Inverse de text48 : octet bas de chaque paire, jusqu'à 00 00. */
    fun textFrom48(bytes: ByteArray): String = buildString {
        var i = 0
        while (i + 1 < bytes.size && !(bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte())) { append((bytes[i].toInt() and 0xFF).toChar()); i += 2 }
    }.trim()

    fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02X".format(it) }

    fun nickname(name: String): ByteArray = frame(NICKNAME, text48(name.trim().take(MachineLimits.NAME_MAX)), 56)

    /**
     * Langue (en 0, fr 1, de 2, es 3), unités (impérial 0, métrique 1), octet « time » (BRU envoie 0),
     * nombre de préréglages affichés : la machine n'affiche plus que les `presets` premiers.
     */
    fun setup(languageCode: Int, metric: Boolean, presets: Int, time: Int = 0): ByteArray =
        frame(SETUP_WRITE, byteArrayOf(languageCode.toByte(), (if (metric) 1 else 0).toByte(), time.toByte(), presets.coerceIn(0, MachineLimits.PRESETS).toByte()), 8)

    fun setup(language: MachineLanguage, metric: Boolean, presets: Int): ByteArray = setup(language.code, metric, presets)

    /** Lecture des réglages (`FF 04 2A E9`) : définie dans BRU Connect mais jamais appelée ; réponse supposée de même forme que 44. */
    fun setupRead(): ByteArray = frame(SETUP_READ)

    /**
     * Réponse à la lecture des réglages, si elle a la forme de la commande 44 (code 42 ou 43 :
     * langue, unités, time, nombre de préréglages) avec des valeurs plausibles ; sinon null.
     */
    fun parseSetupReply(bytes: ByteArray): MachineSetup? {
        if (bytes.size < 8 || bytes[0] != 0xFF.toByte()) return null
        val code = bytes[2].toInt() and 0xFF
        if (code != SETUP_READ && code != SETUP_READ + 1) return null
        val lang = bytes[3].toInt() and 0xFF
        val units = bytes[4].toInt() and 0xFF
        val presets = bytes[6].toInt() and 0xFF
        if (lang > 3 || units > 1 || presets > MachineLimits.PRESETS) return null
        return MachineSetup(lang, units == 1, bytes[5].toInt() and 0xFF, presets)
    }

    /** Heure locale exprimée comme si c'était de l'UTC, en secondes depuis 1970, sur 32 bits petit-boutistes. */
    fun setTime(localEpochSeconds: Long): ByteArray = frame(SET_TIME, le32(localEpochSeconds), 8)

    /** 48 octets : un caractère par paire (octet bas), Latin-1 ; 24 caractères au plus. */
    fun text48(text: String): ByteArray {
        val out = ByteArray(48)
        latin1(text).take(24).forEachIndexed { i, ch -> out[2 * i] = ch.code.toByte() }
        return out
    }

    /** Ramène un texte au Latin-1 que la machine affiche : apostrophes typographiques remplacées, le reste en « ? ». */
    fun latin1(text: String): String = buildString {
        for (ch in text) append(
            when {
                ch.code in 0x20..0x7E || ch.code in 0xA0..0xFF -> ch
                ch == '’' || ch == '‘' -> '\''
                ch == '“' || ch == '”' -> '"'
                ch == '–' || ch == '—' -> '-'
                ch == '…' -> '.'
                ch == 'œ' -> 'o'
                ch == 'Œ' -> 'O'
                else -> '?'
            },
        )
    }

    fun le32(v: Long): ByteArray = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /**
     * Lit une trame, avec la même tolérance que l'app de BRU (et les versions 0.1.4 à 0.1.15, essayées sur
     * la machine) : `valid` (longueur et somme) est seulement noté, jamais exigé. Un état (69) se lit dès
     * 5 octets, un résultat (65, 83) dès 4 ; plus court, la trame reste « inconnue ».
     */
    fun parse(frame: ByteArray): Incoming {
        if (frame.size < 4) return Incoming.Unknown(-1, frame, false)
        val valid = isValid(frame)
        val code = frame[2].toInt() and 0xFF
        val b3 = frame[3].toInt() and 0xFF
        return when {
            code == STATUS && frame.size >= 5 -> Incoming.Status(b3, frame[4].toInt() and 0xFF, valid)
            code == RESULT_BREW -> Incoming.BrewResult(b3, valid)
            code == RESULT_PRESET -> Incoming.PresetResult(b3, valid)
            else -> Incoming.Unknown(code, frame, valid)
        }
    }

    /**
     * Découpe une notification qui porterait plusieurs trames, seulement si toutes les longueurs (octet 1)
     * sont plausibles : chaque morceau commence par FF, fait au moins 4 octets et tient dans la notification,
     * et le tout est couvert exactement. Sinon la notification entière est une seule trame (cas normal).
     */
    fun split(notification: ByteArray): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var i = 0
        while (i < notification.size) {
            val len = notification.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: return listOf(notification)
            if (notification[i] != 0xFF.toByte() || len < 4 || i + len > notification.size) return listOf(notification)
            out += notification.copyOfRange(i, i + len)
            i += len
        }
        return if (out.isEmpty()) listOf(notification) else out
    }

    // --- Codes des réglages -------------------------------------------------------------------

    /** Index de température : 1..11 = 45..95 °C, 12 = « Maximum » (0 = froid, non utilisé ici). */
    fun temperatureCode(celsius: Int): Int {
        val c = MachineLimits.roundTemperature(celsius)
        return if (c >= MachineLimits.TEMP_MAX) 12 else (c - 40) / 5
    }

    /** Code d'eau : 0..6 = 50..350 ml par 50 ; 128..133 = 75..325 ml (demi-pas, firmware récent). */
    fun waterCode(ml: Int, halfStepsAllowed: Boolean): Int {
        val w = MachineLimits.roundWater(ml)
        return if (w % 50 == 0) w / 50 - 1
        else if (halfStepsAllowed) 128 + (w - 75) / 50
        else (MachineLimits.roundWater50(w)) / 50 - 1
    }

    /** Index de durée (0..109) de la durée de la machine la plus proche. */
    fun timeCode(seconds: Int): Int = MachineLimits.steepIndex(seconds)

    // --- Firmware -------------------------------------------------------------------------------

    /** Clé AAMMJJ d'une version « JJMMAA… » (0x2A26), ou null si illisible. */
    fun firmwareKey(version: String?): Int? {
        val d = version?.replace("\u0000", "")?.trim()?.substringBefore('-') ?: return null
        if (d.length != 6 || !d.all { it.isDigit() }) return null
        return (d.substring(4, 6) + d.substring(2, 4) + d.substring(0, 2)).toInt()
    }

    fun firmwareAtLeast(version: String?, min: String): Boolean {
        val v = firmwareKey(version) ?: return false
        val m = firmwareKey(min) ?: return false
        return v >= m
    }

    /** 8 premiers octets du SHA-256 d'un identifiant d'installation stable. */
    fun installId8(installId: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(installId.toByteArray()).copyOf(8)
}

/** Un préréglage lu sur la machine (si elle répond comme on le suppose). */
data class MachinePreset(val slot: Int, val name: String, val temperature: Int, val waterMl: Int, val steepSeconds: Int)

/** Réglages en codes de la machine. */
data class BrewCodes(val temperature: Int, val time: Int, val water: Int)

/** Complément de la forme longue : identifiant d'installation (8 octets), nom (48 octets), thé (4 octets). */
data class BrewIdentity(val installId8: ByteArray, val name: String, val teaId: Long = MakerOneProtocol.NULL_TEA_ID) {
    fun bytes(): ByteArray = installId8.copyOf(8) + MakerOneProtocol.text48(name) + MakerOneProtocol.le32(teaId)
}

/**
 * Langues de l'app et de l'écran de la machine (code de la commande 44, étiquette de langue d'Android,
 * nom dans sa propre langue). La machine connaît aussi l'allemand (2) et l'espagnol (3) : pas proposés.
 */
/** `label` : nom dans sa propre langue (sélecteur) ; `nameRes` : nom traduit, pour l'écrire dans une phrase. */
enum class MachineLanguage(val code: Int, val tag: String, val label: String, @StringRes val nameRes: Int) {
    FR(1, "fr", "Français", R.string.language_name_fr), EN(0, "en", "English", R.string.language_name_en);

    companion object {
        fun of(code: Int): MachineLanguage? = entries.firstOrNull { it.code == code }
        fun ofTag(tag: String?): MachineLanguage? = entries.firstOrNull { tag != null && tag.lowercase().startsWith(it.tag) }
    }
}

/** Réglages lus sur la machine (commande 42). */
data class MachineSetup(val languageCode: Int, val metric: Boolean, val time: Int, val presets: Int) {
    val language get() = MachineLanguage.of(languageCode)
}

sealed interface Incoming {
    val valid: Boolean
    data class Status(val status: Int, val subStatus: Int, override val valid: Boolean) : Incoming
    data class BrewResult(val code: Int, override val valid: Boolean) : Incoming
    data class PresetResult(val code: Int, override val valid: Boolean) : Incoming
    data class Unknown(val code: Int, val bytes: ByteArray, override val valid: Boolean) : Incoming
}
