package ai.homa.app

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages FCM token lifecycle: retrieval, local storage, and registration with the Homa Worker.
 *
 * Auth token flow:
 *   1. Web app logs in → Base44 SDK stores token in localStorage
 *   2. Web calls HomaNative.setAuthToken(token) → stored here in SharedPreferences
 *   3. When FCM token arrives (onNewToken or onResume), it is sent to Worker with X-User-Token
 *   4. If no auth token yet, FCM token is stored locally and sent once auth arrives
 */
object FcmTokenManager {

    private const val TAG = "FcmTokenManager"
    private const val PREFS_NAME = "homa_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_LAST_SENT = "last_sent_timestamp"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Store the user's auth token (received from the web bridge).
     * After storing, attempts to send any pending FCM token to the Worker.
     */
    fun setAuthToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_AUTH_TOKEN, token).apply()
        // If we have a cached FCM token, try sending it now
        val fcmToken = prefs(context).getString(KEY_FCM_TOKEN, null)
        if (fcmToken != null) {
            sendTokenToWorker(context, fcmToken)
        }
    }

    /**
     * Clear the auth token (on logout).
     */
    fun clearAuthToken(context: Context) {
        prefs(context).edit().remove(KEY_AUTH_TOKEN).apply()
    }

    /**
     * Retrieve the current FCM token from Firebase and register it with the Worker.
     * Safe to call on every app resume — only sends if token changed or not yet sent.
     */
    fun fetchAndRegisterToken(context: Context) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                if (token.isNullOrEmpty()) return@addOnCompleteListener
                onTokenRetrieved(context, token)
            }
    }

    /**
     * Called by HomaFirebaseMessagingService.onNewToken when Firebase refreshes the token.
     */
    fun onNewToken(context: Context, token: String) {
        onTokenRetrieved(context, token)
    }

    /**
     * Exposed to the JavaScript bridge so the web app can retrieve the FCM token
     * and register it server-side if it prefers to manage registration itself.
     */
    fun getFcmToken(context: Context): String? {
        return prefs(context).getString(KEY_FCM_TOKEN, null)
    }

    // ── Internal ──

    private fun onTokenRetrieved(context: Context, token: String) {
        val stored = prefs(context).getString(KEY_FCM_TOKEN, null)
        val lastSent = prefs(context).getLong(KEY_LAST_SENT, 0)
        val now = System.currentTimeMillis()

        // Store the token locally
        prefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()

        // Send to Worker if: token changed, or never sent, or last sent > 7 days ago
        if (token != stored || lastSent == 0L || (now - lastSent) > 7 * 24 * 60 * 60 * 1000L) {
            sendTokenToWorker(context, token)
        }
    }

    // Network calls must never run on the main thread (Firebase callbacks arrive there).
    private fun sendTokenToWorker(context: Context, fcmToken: String) {
        val appContext = context.applicationContext
        Thread { postTokenToWorker(appContext, fcmToken) }.start()
    }

    private fun postTokenToWorker(context: Context, fcmToken: String) {
        val authToken = prefs(context).getString(KEY_AUTH_TOKEN, null)
        val payload = JSONObject().apply {
            put("fcm_token", fcmToken)
            put("platform", "android")
        }.toString()

        val url = URL("${BuildConfig.WORKER_API_URL}/notifications/register")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Content-Type", "application/json")
            if (!authToken.isNullOrEmpty()) {
                setRequestProperty("X-User-Token", authToken)
            }
            doOutput = true
        }

        try {
            val os: OutputStream = conn.outputStream
            os.write(payload.toByteArray(Charsets.UTF_8))
            os.flush()
            os.close()

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            // Only count it as sent when the Worker confirms the token was stored.
            if (code in 200..299 && body.contains("registered")) {
                prefs(context).edit().putLong(KEY_LAST_SENT, System.currentTimeMillis()).apply()
                Log.d(TAG, "FCM token registered with Worker (HTTP $code)")
            } else if (code in 200..299) {
                Log.w(TAG, "Worker did not confirm FCM token: $body")
            } else {
                Log.w(TAG, "Worker rejected FCM token (HTTP $code)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send FCM token to Worker", e)
        } finally {
            conn.disconnect()
        }
    }
}