package ai.homa.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

object NotificationSettings {
    fun status(context: Context): JSONObject = JSONObject()
        .put("installation_id", MobileNotificationManager.installationId(context))
        .put("permission_granted", NotificationManagerCompat.from(context).areNotificationsEnabled())
        .put("fcm_available", !FcmTokenManager.getFcmToken(context).isNullOrEmpty())
        .put("background_supported", true)
    fun connect(activity: Activity) {
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
            val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.packageName))
            activity.startActivity(intent)
        }
        MobileNotificationManager.activate(activity)
        FcmTokenManager.fetchAndRegisterToken(activity)
    }
}