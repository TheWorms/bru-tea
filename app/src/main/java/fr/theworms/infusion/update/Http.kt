package fr.theworms.infusion.update

import fr.theworms.infusion.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Client HTTP commun : l'app se présente sous son nom (GitHub exige un User-Agent). */
object Http {
    val userAgent = "Infusion/${BuildConfig.VERSION_NAME} (Android)"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build()) }
        .build()
}
