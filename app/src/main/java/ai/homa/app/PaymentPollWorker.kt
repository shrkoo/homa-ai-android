package ai.homa.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

class PaymentPollWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val token = FcmTokenManager.getAuthToken(applicationContext) ?: return Result.success()
        if (token.isEmpty()) return Result.success()
        return try {
            val enabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
            MobileNotificationHttp.request(token, "mobile-register", JSONObject()
                .put("installation_id", MobileNotificationManager.installationId(applicationContext)).put("enabled", enabled))
            if (!enabled) return Result.success()
            val page = MobileNotificationHttp.request(token, "alerts?filter=unread")
            val items = page.optJSONArray("items") ?: return Result.success()
            for (i in items.length() - 1 downTo 0) {
                if (FcmTokenManager.getAuthToken(applicationContext) != token) return Result.success()
                val item = items.getJSONObject(i)
                MobileNotificationDelivery.show(applicationContext, item.getString("id"), item.optString("title", "هُما"),
                    item.optString("body"), item.optString("route", "/notifications"), HomaApplication.CHANNEL_PAYMENT)
            }
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}