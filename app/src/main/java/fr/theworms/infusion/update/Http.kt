package fr.theworms.infusion.update

import fr.theworms.infusion.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Client HTTP commun. L'hébergeur des mises à jour (theworms.fr) refuse la signature par défaut
 * « okhttp/… » : l'app se présente sous son nom.
 */
object Http {
    val userAgent = "Infusion/${BuildConfig.VERSION_NAME} (Android)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build()) }
        .build()
}
