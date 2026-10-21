package ai.homa.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.result.ActivityResultLauncher
import java.io.ByteArrayOutputStream
import java.io.InputStream

class HomaWebInterface(
    private val activity: Activity,
    private val webView: WebView,
    private val filePickerLauncher: ActivityResultLauncher<Array<String>>,
    private val imagePickerLauncher: ActivityResultLauncher<String>
) {
    /**
     * Opens Android file picker for document upload.
     */
    @JavascriptInterface
    fun openFilePicker() {
        activity.runOnUiThread {
            filePickerLauncher.launch(
                arrayOf(
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
                )
            )
        }
    }

    /**
     * Opens Android image picker.
     */
    @JavascriptInterface
    fun openImagePicker() {
        activity.runOnUiThread {
            imagePickerLauncher.launch("image/*")
        }
    }

    /**
     * Opens Android share sheet.
     */
    @JavascriptInterface
    fun shareText(text: String) {
        activity.runOnUiThread {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }

            activity.startActivity(
                Intent.createChooser(intent, "اشتراک‌گذاری")
            )
        }
    }

    /**
     * Opens a URL in an external browser.
     */
    @JavascriptInterface
    fun openExternal(url: String) {
        activity.runOnUiThread {
            try {
                val uri = Uri.parse(url)

                if (uri.scheme == "https" || uri.scheme == "http") {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    activity.startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "HomaWebInterface",
                    "Cannot open external URL",
                    e
                )
            }
        }
    }

    /**
     * Shows a local notification.
     */
    @JavascriptInterface
    fun showNotification(
        title: String,
        body: String,
        channel: String
    ) {
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
     * Returns application version and market as JSON.
     */
    @JavascriptInterface
    fun getAppInfo(): String {
        val version = try {
            activity.packageManager
                .getPackageInfo(activity.packageName, 0)
                .versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }

        return """{"version":"$version","market":"${BuildConfig.MARKET}","platform":"android"}"""
    }

    /**
     * Starts native billing.
     * Payment is handled by BillingManager for the selected market.
     */
    @JavascriptInterface
    fun startPayment(planKey: String, period: String) {
        activity.runOnUiThread {
            (activity as? MainActivity)?.let { mainActivity ->
                BillingManager.startPayment(
                    mainActivity,
                    planKey,
                    period
                )
            }
        }
    }

    /**
     * Saves the user's authentication token.
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
     * Returns the current Firebase Cloud Messaging token.
     */
    @JavascriptInterface
    fun getFcmToken(): String {
        return FcmTokenManager.getFcmToken(activity) ?: ""
    }

    /**
     * Checks network availability.
     */
    @JavascriptInterface
    fun checkNetwork(): Boolean {
        return NetworkMonitor.isNetworkAvailable(activity)
    }

    /**
     * Vibrates the device.
     */
    @JavascriptInterface
    fun vibrate(durationMs: Long) {
        activity.runOnUiThread {
            NotificationHelper.vibrate(activity, durationMs)
        }
    }

    /**
     * Requests an in-app review.
     *
     * Google Play review is deliberately not imported here,
     * so Myket and Bazaar variants can compile without the
     * Google Play Review dependency.
     *
     * The actual review flow can be implemented in a
     * Google Play-specific source file later.
     */
    @JavascriptInterface
    fun requestReview() {
        activity.runOnUiThread {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_review_unavailable'));",
                null
            )
        }
    }

    /**
     * Handles a selected document.
     */
    fun onFilePicked(uri: Uri?) {
        if (uri == null) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_file_cancelled'));",
                null
            )
            return
        }

        try {
            val (base64, mimeType) = readUri(uri)

            webView.evaluateJavascript(
                """
                window.dispatchEvent(
                    new CustomEvent('homa_file_selected', {
                        detail: {
                            data: '$base64',
                            type: '$mimeType'
                        }
                    })
                );
                """.trimIndent(),
                null
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "HomaWebInterface",
                "File selection failed",
                e
            )

            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_file_error'));",
                null
            )
        }
    }

    /**
     * Handles a selected image.
     */
    fun onImagePicked(uri: Uri?) {
        if (uri == null) {
            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_image_cancelled'));",
                null
            )
            return
        }

        try {
            val (base64, mimeType) = readUri(uri)

            webView.evaluateJavascript(
                """
                window.dispatchEvent(
                    new CustomEvent('homa_image_selected', {
                        detail: {
                            data: '$base64',
                            type: '$mimeType'
                        }
                    })
                );
                """.trimIndent(),
                null
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "HomaWebInterface",
                "Image selection failed",
                e
            )

            webView.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('homa_image_error'));",
                null
            )
        }
    }

    /**
     * Reads a selected URI and returns Base64 data and MIME type.
     */
    private fun readUri(uri: Uri): Pair<String, String> {
        val resolver = activity.contentResolver
        val mimeType =
            resolver.getType(uri) ?: "application/octet-stream"

        val inputStream: InputStream =
            resolver.openInputStream(uri)
                ?: throw Exception("Cannot open file")

        val buffer = ByteArrayOutputStream()

        inputStream.use { stream ->
            val buf = ByteArray(8192)

            while (true) {
                val len = stream.read(buf)
                if (len <= 0) break

                buffer.write(buf, 0, len)
            }
        }

        val base64 = Base64.encodeToString(
            buffer.toByteArray(),
            Base64.NO_WRAP
        )

        return Pair(base64, mimeType)
    }
}
