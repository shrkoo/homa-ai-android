package ai.homa.app

import android.content.Context
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object MobileNotificationDelivery {
    @Synchronized
    fun show(context: Context, id: String, title: String, body: String, route: String, channel: String) {
        if (FcmTokenManager.getAuthToken(context).isNullOrEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return
        }
        val prefs = context.getSharedPreferences("homa_mobile_delivered", Context.MODE_PRIVATE)
        val key = id.removePrefix("payment:")
        val seen = prefs.getStringSet("ids", emptySet())!!.toMutableSet()
        if (seen.contains(key)) return
        NotificationHelper.showNotification(context, title, body, channel, route)
        seen.add(key)
        prefs.edit().putStringSet("ids", seen.toList().takeLast(300).toSet()).apply()
    }
}