package com.mmalyil.smartdocai.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.mmalyil.smartdocai.util.UsageManager

class BillingManager(
    private val context: Context,
    private val listener: BillingUpdateListener
) : PurchasesUpdatedListener {

    companion object {
        const val PRO_PRODUCT_ID = "ai_pro_plan"
    }

    private var billingClient: BillingClient

    init {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "Service disconnected")
            }
        })
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )).build()

        billingClient.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || list.isNullOrEmpty()) {
                Log.w("BillingManager", "No productDetails for $productId: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }

            val productDetails = list[0]

            // If you know your base plan id: choose by it. Otherwise pick first eligible offer.
            val offer = productDetails.subscriptionOfferDetails?.firstOrNull()
            val offerToken = offer?.offerToken ?: run {
                Log.w("BillingManager", "No offerToken for $productId")
                return@queryProductDetailsAsync
            }

            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

            val flowParams = BillingFlowParams.newBuilder()
                // Optional anti-fraud / analytics correlation:
                // .setObfuscatedAccountId(yourUserIdHash)
                .setProductDetailsParamsList(listOf(productParams))
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
                listener.onPurchasesUpdated(purchases)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    handlePurchases(purchases)
                    listener.onPurchasesUpdated(purchases)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i("BillingManager", "User canceled purchase")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // User already subscribed on another device/account state; refresh entitlement
                queryPurchases()
            }
            else -> {
                Log.w("BillingManager", "Purchase failed: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (!purchase.products.contains(PRO_PRODUCT_ID)) continue

            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    UsageManager.setPro(context, true)
                    Log.d("BillingManager", "✅ Pro subscription activated.")

                    if (!purchase.isAcknowledged) {
                        val params = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(params) { br ->
                            if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                                Log.d("BillingManager", "✅ Purchase acknowledged.")
                            } else {
                                Log.w("BillingManager", "Ack failed: ${br.debugMessage}")
                            }
                        }
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    Log.i("BillingManager", "⏳ Purchase pending…")
                    // Optional: notify UI via listener if you want to show “Pending…”
                }
                Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                    Log.w("BillingManager", "Unspecified purchase state")
                }
            }
        }

    }

    interface BillingUpdateListener {
        fun onPurchasesUpdated(purchases: List<Purchase>)
    }

    fun destroy() {
        try { billingClient.endConnection() } catch (_: Exception) {}
    }


}