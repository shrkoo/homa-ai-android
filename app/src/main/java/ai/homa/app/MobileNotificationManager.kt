package ai.homa.app

import android.content.Context
import androidx.work.*
import java.util.UUID
import java.util.concurrent.TimeUnit

object MobileNotificationManager {
    fun installationId(context: Context): String {
        val prefs = context.getSharedPreferences("homa_mobile_install", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }
    }
    fun activate(context: Context) {
        if (FcmTokenManager.getAuthToken(context).isNullOrEmpty()) return
        val manager = WorkManager.getInstance(context)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        manager.enqueueUniquePeriodicWork("homa-payment-periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PaymentPollWorker>(15, TimeUnit.MINUTES).setConstraints(network).build())
        manager.enqueueUniqueWork("homa-payment-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PaymentPollWorker>().setConstraints(network).build())
    }
    fun deactivate(context: Context, token: String?) {
        WorkManager.getInstance(context).cancelUniqueWork("homa-payment-periodic")
        WorkManager.getInstance(context).cancelUniqueWork("homa-payment-now")
        if (token.isNullOrEmpty()) return
        val id = installationId(context)
        Thread {
            try { MobileNotificationHttp.request(token, "mobile-unregister", org.json.JSONObject().put("installation_id", id)) }
            catch (_: Exception) { /* Offline logout still disables local delivery. */ }
        }.start()
    }
}