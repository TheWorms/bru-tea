package fr.theworms.infusion.ui.teas

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.theworms.infusion.data.Tea
import fr.theworms.infusion.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Ce que la fiche d'un thé garde quand l'écran est recréé (rotation, thème, langue) : la photo et les photos
 * ajoutées, les photos en chemin, l'enregistrement en cours et sa fin. Tenu par l'entrée de navigation : l'écran
 * recréé retrouve le même. Une photo en chemin au moment d'une rotation arrive quand même sur la fiche ; pendant
 * l'enregistrement, « Enregistrer » reste inactif sur l'écran recréé, et c'est l'écran affiché qui se ferme à la fin
 * (avant, l'écran recréé redevenait actif et un second appui créait le thé une seconde fois, sans sa photo).
 *
 * La photo, les photos ajoutées et la fin d'enregistrement tiennent aussi à l'arrêt de l'app en arrière-plan
 * (SavedStateHandle) ; les chargements et l'enregistrement en cours s'arrêtent avec elle.
 */
internal class TeaEditWork(private val handle: SavedStateHandle) : ViewModel() {
    /** Comment la fiche s'est terminée : l'écran affiché revient en arrière quand elle l'est. */
    enum class Finish { SAVED, DELETED }

    /** Photo de la fiche (chemin dans les fichiers de l'app), sa provenance et le crédit d'une photo de la base. */
    var photo: String? by Saved(handle, KEY_PHOTO, null)
        private set
    var photoFromCatalog: Boolean by Saved(handle, KEY_FROM_CATALOG, false)
        private set
    var photoCredit: String by Saved(handle, KEY_CREDIT, "")
        private set

    /** Photos ajoutées pendant la saisie : supprimées si on quitte sans enregistrer. */
    private var added: ArrayList<String> by Saved(handle, KEY_ADDED, ArrayList())
    val newPhotos: List<String> get() = added

    private var finishName: String? by Saved(handle, KEY_FINISH, null)
    val finished: Finish? get() = finishName?.let { name -> Finish.entries.firstOrNull { it.name == name } }

    /** Photos en chemin (galerie, base de thés) : « Enregistrer » les attend un moment au lieu de partir sans elles. */
    private val loads = MutableStateFlow(0)
    val photoLoads: StateFlow<Int> = loads.asStateFlow()

    /** Enregistrement en cours (et fini, jusqu'à la sortie de l'écran) : un second appui ne crée pas un second thé. */
    var saving by mutableStateOf(false)

    /** Thé tel qu'il est dans la base (relu à l'ouverture, puis tel qu'enregistré) ; null : pas encore enregistré. */
    var loaded by mutableStateOf<Tea?>(null)

    /** Erreur affichée près de ce qui l'a causée. */
    var error by mutableStateOf<Pair<ErrorSpot, UiText>?>(null)

    /** Photo du thé enregistré, lue dans la base à l'ouverture de la fiche. */
    fun showStoredPhoto(path: String?) {
        photo = path; photoFromCatalog = false; photoCredit = ""
    }

    /** Photo arrivée (galerie ou base) : posée sur la fiche et comptée parmi les photos ajoutées. */
    fun setPhoto(path: String, fromCatalog: Boolean, credit: String) {
        added = ArrayList(added + path)
        photo = path; photoFromCatalog = fromCatalog; photoCredit = credit
    }

    /** Photos ajoutées, retirées de la liste : à garder (enregistrées) ou à supprimer par l'appelant. */
    fun takeNewPhotos(): List<String> = added.toList().also { added = ArrayList() }

    fun finish(how: Finish) {
        finishName = how.name
    }

    /**
     * Charge une photo hors de l'écran : une rotation ne l'arrête pas. Elle ne s'arrête qu'en quittant la fiche
     * (le fichier éventuellement écrit est alors repris par le ménage des photos).
     */
    fun loadPhoto(load: suspend () -> Unit) {
        viewModelScope.launch {
            loads.update { it + 1 }
            try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Infusion", "photo du thé impossible à charger", e)
            } finally {
                loads.update { it - 1 }
            }
        }
    }

    /** Attend, au plus [timeoutMs], que les photos en chemin soient arrivées. */
    suspend fun awaitPhotos(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { loads.first { it == 0 } }
    }

    /** Valeur lue dans l'état de Compose, recopiée dans le SavedStateHandle à chaque changement. */
    private class Saved<T>(private val handle: SavedStateHandle, private val key: String, initial: T) : ReadWriteProperty<Any?, T> {
        @Suppress("UNCHECKED_CAST")
        private val state = mutableStateOf(if (handle.contains(key)) handle.get<Any?>(key) as T else initial)

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            handle[key] = value
        }
    }

    private companion object {
        const val KEY_PHOTO = "fiche.photo"
        const val KEY_FROM_CATALOG = "fiche.photoDeLaBase"
        const val KEY_CREDIT = "fiche.creditPhoto"
        const val KEY_ADDED = "fiche.photosAjoutees"
        const val KEY_FINISH = "fiche.fin"
    }
}
