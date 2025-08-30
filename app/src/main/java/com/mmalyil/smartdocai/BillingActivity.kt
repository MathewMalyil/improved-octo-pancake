package com.mmalyil.smartdocai




import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.Purchase
import com.mmalyil.smartdocai.billing.BillingManager
import com.mmalyil.smartdocai.billing.BillingManager.BillingUpdateListener
import com.mmalyil.smartdocai.util.UsageManager

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.QueryProductDetailsParams



class BillingActivity : AppCompatActivity(), BillingUpdateListener {

    private lateinit var upgradeButton: Button
    private lateinit var usageText: TextView
    private lateinit var restoreButton: Button
    private lateinit var billingManager: BillingManager

    private var priceClient: BillingClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_billing)

        usageText = findViewById(R.id.tvUsageText)
        upgradeButton = findViewById(R.id.btnUpgrade)
        loadLocalizedPriceAndSetButton(
            productId = "ai_pro_plan", // your Play Console product id
            button = upgradeButton
        )


        restoreButton = findViewById(R.id.btnRestore)

        billingManager = BillingManager(this, this)

        // ✅ Show toast if message was passed
        val message = intent.getStringExtra("message")
        if (!message.isNullOrEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        upgradeButton.setOnClickListener {
            billingManager.launchPurchaseFlow(this, BillingManager.PRO_PRODUCT_ID)
        }

        restoreButton.setOnClickListener {
            Toast.makeText(this, "Restoring...", Toast.LENGTH_SHORT).show()
            billingManager.queryPurchases() // Optional if you want active recheck
        }

        val backButton = findViewById<Button>(R.id.btnBack)
        backButton.setOnClickListener {
            finish() // Close BillingActivity and return
        }

        maybeShowUpgradeDialog()
        updateUsageUI()
    }

    private fun updateUsageUI() {
        val used = UsageManager.getTokensUsed(this)
        val cap = UsageManager.getTokenCap(this)
        val isPro = UsageManager.isPro(this)

        val label = if (isPro) "🚀 GPT-4o Pro" else "⚡ Groq Free"
        usageText.text = "$label: $used / $cap tokens used"
    }

    override fun onPurchasesUpdated(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.products.contains(BillingManager.PRO_PRODUCT_ID)
                && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            ) {
                UsageManager.setPro(this, true)
                Toast.makeText(this, "Pro Activated!", Toast.LENGTH_LONG).show()
                updateUsageUI()
            }
        }
    }

    private fun maybeShowUpgradeDialog() {
        val message = intent.getStringExtra("message")
        if (!message.isNullOrEmpty()) {
            val dialog = android.app.AlertDialog.Builder(this)
                .setTitle("Upgrade to GPT-4o Pro 🚀")
                .setMessage("Unlock 300,000 tokens per month, longer answers, and priority AI access.\n\n${
                    message.trim()
                }")
                .setPositiveButton("Upgrade") { _, _ ->
                    billingManager.launchPurchaseFlow(this, "ai_pro_plan")
                }
                .setNegativeButton("Maybe Later", null)
                .create()
            dialog.show()
        }
    }

    private fun loadLocalizedPriceAndSetButton(productId: String, button: Button) {
        if (priceClient == null) {
            priceClient = BillingClient.newBuilder(this)
                .enablePendingPurchases()
                .setListener { _, _ -> /* no-op */ }
                .build()
        }

        val client = priceClient!!
        if (!client.isReady) {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryPrice(productId, button)
                    } else {
                        button.text = "Upgrade to GPT-4o Pro"
                    }
                }
                override fun onBillingServiceDisconnected() { /* you can retry later if needed */ }
            })
        } else {
            queryPrice(productId, button)
        }
    }

    private fun queryPrice(productId: String, button: Button) {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS) // change to INAPP if one-time
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        priceClient?.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details.isEmpty()) {
                button.text = "Upgrade to GPT-4o Pro"
                return@queryProductDetailsAsync
            }

            val pd = details.first()

            // SUBS: grab current pricing phase's formatted price
            val formatted = pd.subscriptionOfferDetails
                ?.firstOrNull()                    // pick the first offer/base plan; customize if needed
                ?.pricingPhases?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice

            // For INAPP (one-time) instead, use:
            // val formatted = pd.oneTimePurchaseOfferDetails?.formattedPrice

            button.text = formatted?.let { "Upgrade to GPT-4o Pro ($it/month)" }
                ?: "Upgrade to GPT-4o Pro"
        }
    }



}