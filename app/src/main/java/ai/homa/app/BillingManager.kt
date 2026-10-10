package ai.homa.app

import android.app.Activity
import android.util.Log
import ai.homa.app.billing.BillingManager as BillingMgr

/**
 * Billing Manager — delegates to Google Play Billing or Myket Billing
 * based on the current build flavor (BuildConfig.MARKET).
 */
object BillingManager {

    private const val TAG = "HomaBilling"

    fun startPayment(activity: Activity, planKey: String, period: String) {
        val productId = getProductId(planKey, period)
        Log.d(TAG, "Starting payment: $productId (market=${BuildConfig.MARKET})")

        when (BuildConfig.MARKET) {
            "googleplay" -> BillingMgr.startGooglePlayPayment(activity, productId, planKey, period)
            "myket" -> BillingMgr.startMyketPayment(activity, productId, planKey, period)
            "bazaar" -> {
                // Bazaar uses same IAB protocol as Google Play
                BillingMgr.startGooglePlayPayment(activity, productId, planKey, period)
            }
            else -> {
                // Fallback: open web payment page in browser
                val url = "${BuildConfig.WEB_APP_URL}/manual-payment?plan=$planKey&period=$period"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                activity.startActivity(intent)
            }
        }
    }

    /**
     * Maps plan + period to market-specific product ID.
     */
    private fun getProductId(planKey: String, period: String): String {
        return when (BuildConfig.MARKET) {
            "myket" -> when {
                planKey == "plus" && period == "monthly" -> "homa_plus_monthly"
                planKey == "plus" && period == "yearly" -> "homa_plus_yearly"
                planKey == "pro" && period == "monthly" -> "homa_pro_monthly"
                planKey == "pro" && period == "yearly" -> "homa_pro_yearly"
                else -> "homa_${planKey}_$period"
            }
            "googleplay" -> when {
                planKey == "plus" && period == "monthly" -> "homa_plus_monthly"
                planKey == "plus" && period == "yearly" -> "homa_plus_yearly"
                planKey == "pro" && period == "monthly" -> "homa_pro_monthly"
                planKey == "pro" && period == "yearly" -> "homa_pro_yearly"
                else -> "homa_${planKey}_$period"
            }
            else -> "homa_${planKey}_$period"
        }
    }
}