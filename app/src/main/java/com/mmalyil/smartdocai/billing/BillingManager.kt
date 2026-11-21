package com.mmalyil.smartdocai.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.mmalyil.smartdocai.util.DocumentEntitlement

/**
 * Play Billing for Document Pro:
 * - [DOCUMENT_PRO_LIFETIME] one-time INAPP
 * - [DOCUMENT_PRO_YEARLY] / [DOCUMENT_PRO_MONTHLY] subscriptions
 *
 * Product IDs must match Play Console exactly (see ship notes).
 */
class BillingManager(
    private val context: Context,
    private val listener: BillingUpdateListener
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"

        /** One-time lifetime. Play Console → In-app products. Suggested India: ₹399. */
        const val DOCUMENT_PRO_LIFETIME = "document_pro"
        /** @deprecated use DOCUMENT_PRO_LIFETIME */
        const val DOCUMENT_PRO_PRODUCT_ID = DOCUMENT_PRO_LIFETIME

        /** Subscription. Play Console → Subscriptions. Base plan id: [BASE_PLAN_YEARLY]. Suggested: ₹199/yr. */
        const val DOCUMENT_PRO_YEARLY = "document_pro_yearly"
        const val BASE_PLAN_YEARLY = "yearly"

        /** Subscription. Base plan id: [BASE_PLAN_MONTHLY]. Suggested: ₹29/mo. */
        const val DOCUMENT_PRO_MONTHLY = "document_pro_monthly"
        const val BASE_PLAN_MONTHLY = "monthly"

        /**
         * Deactivated Play SKU (old "SmartDocAI Pro" / GPT-4o AI Pro).
         * Never query ProductDetails for it, never launch it, never grant Document Pro from it.
         * Kept private so Restore / owned-purchase lists can explicitly ignore it (SMA-23).
         */
        private const val LEGACY_AI_SUB_ID = "ai_pro_plan"

        val ALL_PRO_PRODUCT_IDS = setOf(
            DOCUMENT_PRO_LIFETIME,
            DOCUMENT_PRO_YEARLY,
            DOCUMENT_PRO_MONTHLY
        )

        fun isDocumentProSku(productId: String): Boolean = productId in ALL_PRO_PRODUCT_IDS

        fun isLegacyAiProSku(productId: String): Boolean = productId == LEGACY_AI_SUB_ID

        /** India fallback labels when Play price not fetched yet. */
        const val FALLBACK_PRICE_LIFETIME = "₹399"
        const val FALLBACK_PRICE_YEARLY = "₹199/yr"
        const val FALLBACK_PRICE_MONTHLY = "₹29/mo"
    }

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    private var isConnecting = false

    fun startConnection(onReady: (() -> Unit)? = null) {
        if (isReady()) {
            onReady?.invoke()
            return
        }
        if (isConnecting) {
            Log.d(TAG, "startConnection: already connecting")
            return
        }

        isConnecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                isConnecting = false
                if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient ready")
                    onReady?.invoke()
                } else {
                    Log.w(TAG, "Setup failed: ${r.responseCode} ${r.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Service disconnected")
            }
        })
    }

    private fun isReady(): Boolean = try {
        billingClient.isReady
    } catch (_: Exception) {
        false
    }

    /** Query owned INAPP + SUBS and sync Document Pro entitlement. */
    fun queryPurchases(onComplete: (() -> Unit)? = null) {
        if (!isReady()) {
            Log.w(TAG, "queryPurchases: BillingClient not ready yet")
            onComplete?.invoke()
            return
        }

        val collected = mutableListOf<Purchase>()
        var pending = 2

        fun done() {
            pending--
            if (pending > 0) return
            syncEntitlement(collected, clearIfMissing = true)
            listener.onPurchasesUpdated(collected.toList())
            onComplete?.invoke()
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { res, purchases ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                collected.addAll(purchases ?: emptyList())
            } else {
                Log.w(TAG, "INAPP query failed: ${res.debugMessage}")
            }
            done()
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { res, purchases ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                collected.addAll(purchases ?: emptyList())
            } else {
                Log.w(TAG, "SUBS query failed: ${res.debugMessage}")
            }
            done()
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String = DOCUMENT_PRO_LIFETIME) {
        if (!isReady()) {
            Log.w(TAG, "launchPurchaseFlow: BillingClient not ready yet")
            return
        }

        // Hard refuse deactivated AI Pro SKU — never offer / query it.
        if (isLegacyAiProSku(productId) || !isDocumentProSku(productId)) {
            Log.w(TAG, "launchPurchaseFlow refused non-Document-Pro SKU=$productId")
            listener.onProductUnavailable(productId, "Only Document Pro SKUs are offered")
            return
        }

        val isSub = productId == DOCUMENT_PRO_YEARLY || productId == DOCUMENT_PRO_MONTHLY
        val productType = if (isSub) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        val basePlanId = when (productId) {
            DOCUMENT_PRO_YEARLY -> BASE_PLAN_YEARLY
            DOCUMENT_PRO_MONTHLY -> BASE_PLAN_MONTHLY
            else -> null
        }

        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryParams) { result, detailsResult ->
            val details = detailsResult.productDetailsList
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details.isNullOrEmpty()) {
                val unfetched = detailsResult.unfetchedProductList
                    ?.joinToString { "${it.productId}:${it.statusCode}" }
                Log.w(TAG, "No ProductDetails for $productId: ${result.debugMessage} unfetched=$unfetched")
                listener.onProductUnavailable(productId, result.debugMessage)
                return@queryProductDetailsAsync
            }

            val pd = details.first()
            val prodBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(pd)

            if (isSub) {
                val offers = pd.subscriptionOfferDetails
                if (offers.isNullOrEmpty()) {
                    Log.w(TAG, "No subscription offers for $productId")
                    listener.onProductUnavailable(productId, "No base plan / offer")
                    return@queryProductDetailsAsync
                }
                val offer = offers.firstOrNull { it.basePlanId == basePlanId }
                    ?: offers.first()
                prodBuilder.setOfferToken(offer.offerToken)
                Log.d(TAG, "Launching sub $productId basePlan=${offer.basePlanId}")
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(prodBuilder.build()))
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    data class PriceInfo(
        val productId: String,
        val formattedPrice: String?,
        val fallbackLabel: String
    ) {
        fun display(): String = formattedPrice ?: fallbackLabel
    }

    /** Fetch localized prices for lifetime + yearly + monthly. */
    fun queryAllPrices(onResult: (List<PriceInfo>) -> Unit) {
        if (!isReady()) {
            onResult(
                listOf(
                    PriceInfo(DOCUMENT_PRO_LIFETIME, null, FALLBACK_PRICE_LIFETIME),
                    PriceInfo(DOCUMENT_PRO_YEARLY, null, FALLBACK_PRICE_YEARLY),
                    PriceInfo(DOCUMENT_PRO_MONTHLY, null, FALLBACK_PRICE_MONTHLY)
                )
            )
            return
        }

        val out = mutableMapOf<String, PriceInfo>()
        var pending = 2

        fun finish() {
            pending--
            if (pending > 0) return
            onResult(
                listOf(
                    out[DOCUMENT_PRO_YEARLY]
                        ?: PriceInfo(DOCUMENT_PRO_YEARLY, null, FALLBACK_PRICE_YEARLY),
                    out[DOCUMENT_PRO_MONTHLY]
                        ?: PriceInfo(DOCUMENT_PRO_MONTHLY, null, FALLBACK_PRICE_MONTHLY),
                    out[DOCUMENT_PRO_LIFETIME]
                        ?: PriceInfo(DOCUMENT_PRO_LIFETIME, null, FALLBACK_PRICE_LIFETIME)
                )
            )
        }

        // Lifetime INAPP
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(DOCUMENT_PRO_LIFETIME)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()
        ) { res, detailsResult ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                val pd = detailsResult.productDetailsList?.firstOrNull()
                val price = pd?.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()
                    ?.formattedPrice
                    ?: pd?.oneTimePurchaseOfferDetails?.formattedPrice
                out[DOCUMENT_PRO_LIFETIME] =
                    PriceInfo(DOCUMENT_PRO_LIFETIME, price, FALLBACK_PRICE_LIFETIME)
            }
            finish()
        }

        // Yearly + monthly SUBS in one query
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(DOCUMENT_PRO_YEARLY)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build(),
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(DOCUMENT_PRO_MONTHLY)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                )
                .build()
        ) { res, detailsResult ->
            if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsResult.productDetailsList?.forEach { pd ->
                    val offer = when (pd.productId) {
                        DOCUMENT_PRO_YEARLY ->
                            pd.subscriptionOfferDetails?.firstOrNull { it.basePlanId == BASE_PLAN_YEARLY }
                                ?: pd.subscriptionOfferDetails?.firstOrNull()
                        DOCUMENT_PRO_MONTHLY ->
                            pd.subscriptionOfferDetails?.firstOrNull { it.basePlanId == BASE_PLAN_MONTHLY }
                                ?: pd.subscriptionOfferDetails?.firstOrNull()
                        else -> null
                    }
                    val price = offer?.pricingPhases?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice
                    val fallback = when (pd.productId) {
                        DOCUMENT_PRO_YEARLY -> FALLBACK_PRICE_YEARLY
                        else -> FALLBACK_PRICE_MONTHLY
                    }
                    out[pd.productId] = PriceInfo(pd.productId, price, fallback)
                }
            }
            finish()
        }
    }

    /** Localized one-time price from Play (e.g. "₹399"), or null if unavailable. */
    fun queryPrice(productId: String = DOCUMENT_PRO_LIFETIME, onPrice: (String?) -> Unit) {
        queryAllPrices { list ->
            onPrice(list.firstOrNull { it.productId == productId }?.formattedPrice)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases ?: emptyList()
                syncEntitlement(list, clearIfMissing = false)
                listener.onPurchasesUpdated(list)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryPurchases()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "User canceled purchase")
            }
            else -> {
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun syncEntitlement(purchases: List<Purchase>, clearIfMissing: Boolean) {
        var owned = false
        var source = DocumentEntitlement.ProSource.NONE
        purchases.forEach { p ->
            // Explicitly ignore deactivated ai_pro_plan (and any other non-Document-Pro SKU).
            p.products.forEach { id ->
                if (isLegacyAiProSku(id)) {
                    Log.i(TAG, "Ignoring legacy SKU $id — does not grant Document Pro")
                }
            }
            val proId = p.products.firstOrNull { isDocumentProSku(it) } ?: return@forEach
            when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    owned = true
                    source = when (proId) {
                        DOCUMENT_PRO_YEARLY -> DocumentEntitlement.ProSource.YEARLY
                        DOCUMENT_PRO_MONTHLY -> DocumentEntitlement.ProSource.MONTHLY
                        else -> DocumentEntitlement.ProSource.LIFETIME
                    }
                    if (!p.isAcknowledged) {
                        val ack = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(p.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(ack) { br ->
                            if (br.responseCode == BillingClient.BillingResponseCode.OK) {
                                Log.d(TAG, "Purchase acknowledged $proId")
                            } else {
                                Log.w(TAG, "Ack failed: ${br.debugMessage}")
                            }
                        }
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    Log.i(TAG, "Purchase pending $proId")
                }
                else -> Unit
            }
        }
        if (owned) {
            DocumentEntitlement.setDocumentPro(context, true, source)
            // Mirror into legacy UsageManager flag so GPT routing follows Document Pro only.
            com.mmalyil.smartdocai.util.UsageManager.setPro(context, true)
            Log.d(TAG, "Document Pro entitlement active source=$source")
        } else if (clearIfMissing) {
            DocumentEntitlement.setDocumentPro(context, false, DocumentEntitlement.ProSource.NONE)
            // Clear leftover ai_pro_plan-era UsageManager.isPro prefs.
            com.mmalyil.smartdocai.util.UsageManager.setPro(context, false)
        }
    }

    interface BillingUpdateListener {
        fun onPurchasesUpdated(purchases: List<Purchase>)
        fun onProductUnavailable(productId: String, debugMessage: String?) {}
    }

    fun destroy() {
        try {
            billingClient.endConnection()
        } catch (_: Exception) {
        }
    }
}
