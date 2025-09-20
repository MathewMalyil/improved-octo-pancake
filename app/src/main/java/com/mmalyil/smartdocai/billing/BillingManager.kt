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
        /** MUST match the Play Console product ID exactly. */
        const val PRO_PRODUCT_ID = "ai_pro_plan"
    }

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Optional: refresh entitlement once connected
                    queryPurchases()
                } else {
                    Log.w("BillingManager", "Setup failed: ${r.responseCode} ${r.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w("BillingManager", "Service disconnected")
                // Play recommends retrying later; UI can call methods again which will guard on isReady()
            }
        })
    }

    /** Defensive readiness check (BillingClient can be momentarily not-ready). */
    private fun isReady(): Boolean = try {
        billingClient.isReady
    } catch (_: Exception) {
        false
    }

    /** Query current entitlement (subs + inapp). Call on resume or after purchase. */
    fun queryPurchases(onComplete: (() -> Unit)? = null) {
        if (!isReady()) {
            Log.w("BillingManager", "queryPurchases: BillingClient not ready yet")
            onComplete?.invoke()
            return
        }

        var remaining = 2
        fun done() { if (--remaining == 0) onComplete?.invoke() }

        // SUBSCRIPTIONS
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { res, purchases ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                val list = purchases ?: emptyList()
                handlePurchases(list)
                listener.onPurchasesUpdated(list)
            } else {
                Log.w("BillingManager", "SUBS query failed: ${res.debugMessage}")
                listener.onPurchasesUpdated(emptyList())
            }
            done()
        }

        // INAPP (future-proofing; safe even if you have none)
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { res, purchases ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                val list = purchases ?: emptyList()
                handlePurchases(list)
                listener.onPurchasesUpdated(list)
            } else {
                Log.w("BillingManager", "INAPP query failed: ${res.debugMessage}")
                listener.onPurchasesUpdated(emptyList())
            }
            done()
        }
    }

    /** Launch the purchase flow for the Pro subscription. */
    fun launchPurchaseFlow(activity: Activity, productId: String = PRO_PRODUCT_ID) {
        if (!isReady()) {
            Log.w("BillingManager", "launchPurchaseFlow: BillingClient not ready yet")
            return
        }

        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ))
            .build()

        billingClient.queryProductDetailsAsync(queryParams) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details.isNullOrEmpty()) {
                Log.w("BillingManager", "No ProductDetails for $productId: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }

            val pd = details.first()
            val offerToken = pd.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken.isNullOrBlank()) {
                Log.w("BillingManager", "No offerToken for $productId")
                return@queryProductDetailsAsync
            }

            val prodParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(pd)
                .setOfferToken(offerToken)
                .build()

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(prodParams))
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    /** Get localized price string for UI (e.g., “$4.99/month”). */
    fun queryPrice(productId: String = PRO_PRODUCT_ID, onPrice: (String?) -> Unit) {
        if (!isReady()) {
            Log.w("BillingManager", "queryPrice: BillingClient not ready yet")
            onPrice(null)
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ))
            .build()

        billingClient.queryProductDetailsAsync(params) { res, details ->
            if (res.responseCode != BillingClient.BillingResponseCode.OK || details.isNullOrEmpty()) {
                onPrice(null)
                return@queryProductDetailsAsync
            }
            val price = details.first().subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice
            onPrice(price)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases ?: emptyList()
                handlePurchases(list)
                listener.onPurchasesUpdated(list)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Reflect existing entitlement.
                queryPurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i("BillingManager", "User canceled purchase")
            }
            else -> {
                Log.w("BillingManager", "Purchase failed: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        purchases.forEach { p ->
            if (!p.products.contains(PRO_PRODUCT_ID)) return@forEach

            when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    // Grant entitlement
                    UsageManager.setPro(context, true)
                    Log.d("BillingManager", "✅ Pro entitlement active")
                    if (!p.isAcknowledged) {
                        val ack = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(p.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(ack) { br ->
                            if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                                Log.d("BillingManager", "✅ Purchase acknowledged")
                            } else {
                                Log.w("BillingManager", "Ack failed: ${br.debugMessage}")
                            }
                        }
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    Log.i("BillingManager", "⏳ Purchase pending")
                }
                else -> Unit
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