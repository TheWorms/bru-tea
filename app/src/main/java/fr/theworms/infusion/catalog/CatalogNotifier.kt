package fr.theworms.infusion.catalog

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fr.theworms.infusion.MainActivity
import fr.theworms.infusion.R

/** « Base de thés mise à jour · 12 nouveaux thés à découvrir. » quand tea-db s'est enrichie. */
object CatalogNotifier {
    private const val CHANNEL = "base_de_thes"
    private const val ID = 43

    fun notify(context: Context, added: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notif_catalog_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.notif_catalog_channel_desc)
        })
        val open = PendingIntent.getActivity(
            context, 3, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_leaf)
            .setContentTitle(context.getString(R.string.notif_catalog_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_catalog_added, added, added))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID, n) }
    }
}
