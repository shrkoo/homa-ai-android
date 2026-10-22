package ai.homa.app

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
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
        .put("payment_channel_enabled", paymentChannelEnabled(context))
    private fun paymentChannelEnabled(context: Context): Boolean = Build.VERSION.SDK_INT < 26 ||
        context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(HomaApplication.CHANNEL_PAYMENT)?.importance != NotificationManager.IMPORTANCE_NONE
    fun connect(activity: Activity) {
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled() || !paymentChannelEnabled(activity)) {
            val intent = if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, HomaApplication.CHANNEL_PAYMENT)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.packageName))
            activity.startActivity(intent)
            return
        }
        MobileNotificationManager.activate(activity)
        FcmTokenManager.renewRegistration(activity)
        AlertDialog.Builder(activity)
            .setTitle("آزمایش اعلان گوشی")
            .setMessage("اتصال اعلان در حال نوسازی است. آزمایش زیر فقط نمایش اعلان روی همین گوشی را بررسی می‌کند، نه دریافت از سرور.")
            .setPositiveButton("نمایش اعلان آزمایشی") { _, _ ->
                Handler(Looper.getMainLooper()).postDelayed({
                    NotificationHelper.showNotification(activity.applicationContext, "آزمایش اعلان هُما",
                        "نمایش اعلان روی این گوشی فعال است؛ این پیام آزمایش محلی است.", HomaApplication.CHANNEL_PAYMENT, "/notifications")
                }, 3000)
            }
            .setNegativeButton("بستن", null)
            .show()
    }
}
