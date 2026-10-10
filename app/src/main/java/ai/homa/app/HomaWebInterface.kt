package ai.homa.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.result.ActivityResultLauncher
import com.google.android.play.core.review.ReviewManagerFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream

class HomaWebInterface(
    private val activity: Activity,
    private val webView: WebView,
    private val filePickerLauncher: ActivityResultLauncher<Array<String>>,
    private val imagePickerLauncher: ActivityResultLauncher<String>
) {
    private var pendingFileCallback: ((String, String) -> Unit)? = null
    private var pendingImageCallback: ((String, String) -> Unit)? = null

    /**
     * Called from JavaScript: HomaNative.openFilePicker()
     * Opens Android file picker for document upload.
     */
    @JavascriptInterface
    fun openFilePicker() {
        activity.runOnUiThread {
            filePickerLauncher.launch(arrayOf(
                "application/pdf",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "image/*"
            ))
        }
    }

    /**
     * Called from JavaScript: HomaNative.openImagePicker()
     * Opens Android photo picker for image selection.
     */
    @JavascriptInterface
    fun openImagePicker() {
        activity.runOnUiThread {
            imagePickerLauncher.launch("image/*")
        }
    }

    /**
     * Called from JavaScript: HomaNative.shareText(text)
     * Opens Android share sheet.
     */
    @JavascriptInterface
    fun shareText(text: String) {
        activity.runOnUiThread {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            activity.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
        }
    }

    /**
     * Called from JavaScript: HomaNative.openExternal(url)
     * Opens URL in external browser.
     */
    @JavascriptInterface
    fun openExternal(url: String) {
        activity.runOnUiThread {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }
    }

    /**
     * Called from JavaScript: HomaNative.showNotification(title, body, channel)
     * Shows a local notification.
     */
    @JavascriptInterface
    fun showNotification(title: String, body: String, channel: String) {
        activity.runOnUiThread {
            NotificationHelper.showNotification(
                activity,
                title = title,
                body = body,
                channel = channel
            )
        }
    }

    /**
     * Called from JavaScript: HomaNative.getAppInfo()
     * Returns app version and market info as JSON.
     */
    @JavascriptInterface
    fun getAppInfo(): String {
        val version = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
        return """{"version":"$version","market":"${BuildConfig.MARKET}","platform":"android"}"""
    }

    /**
     * Called from JavaScript: HomaNative.startPayment(planKey, period)
     * Initiates native billing (Google Play or Myket depending on flavor).
     */
    @JavascriptInterface
    fun startPayment(planKey: String, period: String) {
        activity.runOnUiThread {
            // Delegates to billing manager
            (activity as? MainActivity)?.let { mainActivity ->
                BillingManager.startPayment(mainActivity, planKey, period)
            }
        }
    }

    /**
     * Called from JavaScript: HomaNative.setAuthToken(token)
     * Stores the user's auth token (from Base44 SDK) so that FcmTokenManager
     * can register the push token with the Worker on the user's behalf.
     */
    @JavascriptInterface
    fun setAuthToken(token: String) {
        if (token.isNotEmpty()) {
            FcmTokenManager.setAuthToken(activity, token)
        } else {
            FcmTokenManager.clearAuthToken(activity)
        }
    }

    /**
     * Called from JavaScript: HomaNative.getFcmToken()
     * Returns the current FCM registration token (or empty string if not yet available).
     * The web app can use this to register the token server-side if it prefers.
     */
    @JavascriptInterface
    fun getFcmToken(): String {
        return FcmTokenManager.getFcmToken(activity) ?: ""
    }

    /**
     * Called from JavaScript: HomaNative.checkNetwork()
     * Returns true if network is available.
     */
    @JavascriptInterface
    fun checkNetwork(): Boolean {
        return NetworkMonitor.isNetworkAvailable(activity)
    }

    /**
     * Called from JavaScript: HomaNative.vibrate(durationMs)
     */
    @JavascriptInterface
    fun vibrate(durationMs: Long) {
        activity.runOnUiThread {
            NotificationHelper.vibrate(activity, durationMs)
        }
    }

    /**
     * Called from JavaScript: HomaNative.requestReview()
     * Triggers the Google Play In-App Review API to prompt the user for a
     * Play Store rating. Gated to once every 90 days per install via
     * SharedPreferences (native-side enforcement; web side also gates).
     * Only available on the googleplay flavor (Review API is Play-specific).
     */
    @JavascriptInterface
    fun requestReview() {
        activity.runOnUiThread {
            try {
                val prefs = activity.getSharedPreferences("homa_review", Context.MODE_PRIVATE)
                val last = prefs.getLong("last_review_ts", 0L)
                val ninetyDaysMs = 90L * 24 * 60 * 60 * 1000L
                if (last != 0L && System.currentTimeMillis() - last < ninetyDaysMs) {
                    // Still within cooldown — skip silently
                    webView.evaluateJavascript(
                        "window.dispatchEvent(new CustomEvent('homa_review_skipped'));", null
                    )
                    return@runOnUiThread
                }
                val manager = ReviewManagerFactory.create(activity)
                val request = manager.requestReviewFlow()
                request.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val reviewInfo = task.result
                        manager.launchReviewFlow(activity, reviewInfo)
                            .addOnCompleteListener {
                                // Mark timestamp after the flow completes (submitted or dismissed)
                                prefs.edit().putLong("last_review_ts", System.currentTimeMillis()).apply()
                                webView.evaluateJavascript(
                                    "window.dispatchEvent(new CustomEvent('homa_review_completed'));", null
                                )
                            }
                    } else {
                        webView.evaluateJavascript(
                            "window.dispatchEvent(new CustomEvent('homa_review_failed'));", null
                        )
                    }
                }
            } catch (e: Exception) {
                webView.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('homa_review_failed'));", null
                )
            }
        }
    }

    // ── Callbacks from ActivityResult ──

    fun onFilePicked(uri: Uri?) {
        if (uri == null) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_file_cancelled'));", null
            )
            return
        }
        try {
            val (base64, mimeType) = readUri(uri)
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_file_selected', {detail:{data:'$base64',type:'$mimeType'}}));",
                null
            )
        } catch (e: Exception) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_file_error',{detail:'${e.message}'}));", null
            )
        }
    }

    fun onImagePicked(uri: Uri?) {
        if (uri == null) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_image_cancelled'));", null
            )
            return
        }
        try {
            val (base64, mimeType) = readUri(uri)
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_image_selected', {detail:{data:'$base64',type:'$mimeType'}}));",
                null
            )
        } catch (e: Exception) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_image_error',{detail:'${e.message}'}));", null
            )
        }
    }

    private fun readUri(uri: Uri): Pair<String, String> {
        val resolver = activity.contentResolver
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        val inputStream: InputStream = resolver.openInputStream(uri)
            ?: throw Exception("Cannot open file")
        val buffer = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var len: Int
        while (inputStream.read(buf).also { len = it } > 0) {
            buffer.write(buf, 0, len)
        }
        inputStream.close()
        val base64 = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
        return Pair(base64, mimeType)
    }
}