package ai.homa.app.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*

/**
 * Google Play Billing implementation.
 * Used by "googleplay" and "bazaar" flavors (Bazaar supports same IAB v3 protocol).
 */
object BillingManager {

    private const val TAG = "GooglePlayBilling"
    private lateinit var billingClient: BillingClient
    private var pendingProductId: String = ""
    private var pendingPlanKey: String = ""
    private var pendingPeriod: String = ""
    private var currentActivity: Activity? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (result.responseCode == BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled purchase")
            notifyWeb("cancelled", "")
        } else {
            Log.e(TAG, "Purchase error: ${result.debugMessage}")
            notifyWeb("error", result.debugMessage)
        }
    }

    fun startGooglePlayPayment(activity: Activity, productId: String, planKey: String, period: String) {
        currentActivity = activity
        pendingProductId = productId
        pendingPlanKey = planKey
        pendingPeriod = period

        billingClient = BillingClient.newBuilder(activity)
            .setPurchasesUpdatedListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    queryAndLaunchProduct()
                } else {
                    Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                    notifyWeb("error", "Billing setup failed")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "Billing service disconnected")
            }
        })
    }

    private fun queryAndLaunchProduct() {
        val productList = ArrayList<SkuDetailsParams.SkuDetailsParam>()
        productList.add(
            SkuDetailsParams.SkuDetailsParam.newBuilder()
                .setType(BillingClient.SkuType.SUBS)
                .setSku(pendingProductId)
                .build()
        )

        val params = SkuDetailsParams.newBuilder()
            .setSkusList(listOf(pendingProductId))
            .setType(BillingClient.SkuType.SUBS)

        billingClient.querySkuDetailsAsync(params.build()) { result, skuDetailsList ->
            if (result.responseCode == BillingResponseCode.OK && skuDetailsList != null) {
                for (details in skuDetailsList) {
                    launchBillingFlow(details)
                    return@querySkuDetailsAsync
                }
                Log.e(TAG, "Product not found: $pendingProductId")
                notifyWeb("error", "Product not found")
            } else {
                Log.e(TAG, "SkuDetails error: ${result.debugMessage}")
                notifyWeb("error", result.debugMessage)
            }
        }
    }

    private fun launchBillingFlow(skuDetails: SkuDetails) {
        val flowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .build()

        currentActivity?.let { activity ->
            val responseCode = billingClient.launchBillingFlow(activity, flowParams)
            Log.d(TAG, "Billing flow launched: ${responseCode.responseCode}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Verify purchase on server (Homa Worker)
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }
            // Send purchase token to Worker for verification
            notifyWeb("success", purchase.purchaseToken)
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            Log.d(TAG, "Acknowledged: ${result.responseCode}")
        }
    }

    private fun notifyWeb(status: String, data: String) {
        currentActivity?.runOnUiThread {
            val js = "window.dispatchEvent(new CustomEvent('homa_payment_result',{detail:{status:'$status',data:'$data',plan:'$pendingPlanKey',period:'$pendingPeriod'}}));"
            (currentActivity as? android.webkit.WebView)?.let { it.evaluateJavascript(js, null) }
                ?: run {
                    // Find WebView via activity
                    val webView = currentActivity?.findViewById<android.webkit.WebView>(android.view.View.generateViewId())
                    webView?.evaluateJavascript(js, null)
                }
        }
    }

    fun startMyketPayment(activity: Activity, productId: String, planKey: String, period: String) {
        // Myket SDK implementation
        // Uses ir.myket:myketsdk
        try {
            val myketPayment = ir.myket.billing.MyketPayment(activity)
            myketPayment.startPurchase(productId, "homa_payload", object : ir.myket.billing.MyketPaymentCallback {
                override fun onSuccess(purchase: ir.myket.billing.Purchase) {
                    // Verify on Worker
                    notifyWebMyket(activity, "success", purchase.purchaseToken, planKey, period)
                }

                override fun onError(error: ir.myket.billing.MyketError) {
                    notifyWebMyket(activity, "error", error.message, planKey, period)
                }

                override fun onCancel() {
                    notifyWebMyket(activity, "cancelled", "", planKey, period)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Myket billing error: ${e.message}")
            notifyWebMyket(activity, "error", e.message ?: "Myket error", planKey, period)
        }
    }

    private fun notifyWebMyket(activity: Activity, status: String, data: String, planKey: String, period: String) {
        activity.runOnUiThread {
            val js = "window.dispatchEvent(new CustomEvent('homa_payment_result',{detail:{status:'$status',data:'$data',plan:'$planKey',period:'$period'}}));"
            // Find WebView
            val rootView = activity.window.decorView
            findWebView(rootView)?.evaluateJavascript(js, null)
        }
    }

    private fun findWebView(view: android.view.View): android.webkit.WebView? {
        if (view is android.webkit.WebView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                findWebView(child)?.let { return it }
            }
        }
        return null
    }
}