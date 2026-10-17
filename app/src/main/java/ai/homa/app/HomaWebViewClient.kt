package ai.homa.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent

class HomaWebViewClient(private val context: Context) : WebViewClient() {

    companion object {
        // Only these domains are allowed in WebView
        private val ALLOWED_DOMAINS = setOf(
            "capable-aria-chat-flow.base44.app",
            "homa-ai-core.shahramalidazeh6203.workers.dev"
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url ?: return false
        val host = url.host ?: return false

        // Check if domain is allowed
        if (ALLOWED_DOMAINS.any { host == it || host.endsWith(".$it") }) {
            // Same domain — load in WebView
            return false
        }

        // External link — open in external browser (Custom Tabs)
        try {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            intent.launchUrl(context, url)
        } catch (e: Exception) {
            // Fallback to default browser
            val browserIntent = Intent(Intent.ACTION_VIEW, url)
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
        }
        return true
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        // Show offline for main-frame network-level errors (DNS failure,
        // connection refused, timeout, SSL handshake failure) — these are
        // genuine connectivity issues, not server-side problems.
        if (request?.isForMainFrame == true) {
            (context as? MainActivity)?.showOffline(true)
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        // Only show offline for main-frame server errors (5xx).
        // Client errors (4xx) — 401, 403, 404 — are handled by the web app
        // itself; showing "اتصال اینترنت برقرار نیست" for them is misleading.
        val statusCode = errorResponse?.statusCode ?: 0
        if (request?.isForMainFrame == true && statusCode >= 500) {
            (context as? MainActivity)?.showOffline(true)
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        (context as? MainActivity)?.stopSwipeRefresh()
        (context as? MainActivity)?.showOffline(false)

        // Inject safe-area CSS for edge-to-edge
        view?.evaluateJavascript("""
            (function() {
                var style = document.createElement('style');
                style.textContent = `
                    body {
                        padding-top: env(safe-area-inset-top) !important;
                        padding-bottom: env(safe-area-inset-bottom) !important;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent(), null)
    }
}