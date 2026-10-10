package ai.homa.app.billing

import android.app.Activity
import android.util.Log

/**
 * Billing entry point used by the googleplay/myket/bazaar flavors.
 *
 * The debug build shipped for push-notification testing does not wire a real
 * billing client; payments are handled by the web app's Stripe/Manual flows.
 * The public method signatures are kept stable so the native bridge compiles
 * without changes.
 */
object BillingManager {

    private const val TAG = "HomaBilling"

    fun startGooglePlayPayment(activity: Activity, productId: String, planKey: String, period: String) {
        Log.d(TAG, "Google Play billing is disabled in this build")
        notifyWeb(activity, "error", "Billing not available in debug build", planKey, period)
    }

    fun startMyketPayment(activity: Activity, productId: String, planKey: String, period: String) {
        Log.d(TAG, "Myket billing is disabled in this build")
        notifyWeb(activity, "error", "Billing not available in debug build", planKey, period)
    }

    private fun notifyWeb(activity: Activity, status: String, data: String, planKey: String, period: String) {
        activity.runOnUiThread {
            val js = "window.dispatchEvent(new CustomEvent('homa_payment_result',{detail:{status:'$status',data:'$data',plan:'$planKey',period:'$period'}}));"
            findWebView(activity.window.decorView)?.evaluateJavascript(js, null)
        }
    }

    private fun findWebView(view: android.view.View): android.webkit.WebView? {
        if (view is android.webkit.WebView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i) ?: continue
                findWebView(child)?.let { return it }
            }
        }
        return null
    }
}