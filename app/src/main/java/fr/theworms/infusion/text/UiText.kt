package fr.theworms.infusion.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Texte à afficher, écrit hors de l'interface (machine, base de thés, mises à jour) : une ressource et ses
 * arguments, lue seulement à l'affichage. Il suit donc la langue de l'app, et les tests JVM n'ont pas
 * besoin d'Android pour le comparer.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
    /** Texte qui ne se traduit pas (nom donné par l'utilisateur, réponse brute de la machine…). */
    data class Raw(val text: String) : UiText
    data class Join(val parts: List<UiText>, val separator: String = " ") : UiText
}

fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, args.toList())

fun raw(text: String): UiText = UiText.Raw(text)

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res -> if (args.isEmpty()) context.getString(id) else context.getString(id, *resolveArgs(args, context))
    is UiText.Plural -> context.resources.getQuantityString(id, count, *resolveArgs(args.ifEmpty { listOf(count) }, context))
    is UiText.Raw -> text
    is UiText.Join -> parts.joinToString(separator) { it.resolve(context) }
}

private fun resolveArgs(args: List<Any>, context: Context): Array<Any> =
    args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray()

@Composable
fun UiText.asString(): String {
    LocalConfiguration.current // relu quand la langue change
    return resolve(LocalContext.current)
}
