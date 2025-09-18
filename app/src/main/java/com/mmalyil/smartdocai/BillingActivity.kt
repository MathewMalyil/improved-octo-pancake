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




        restoreButton = findViewById(R.id.btnRestore)

        billingManager = BillingManager(this, this)

        val isPro = UsageManager.isPro(this)
        if (!isPro) {
            // Only load price if user can buy
            upgradeButton.isEnabled = false
            loadLocalizedPriceAndSetButton(BillingManager.PRO_PRODUCT_ID, upgradeButton) // use constant
        } else {
            upgradeButton.isEnabled = false
            upgradeButton.text = "Pro Active ✓"
        }

        upgradeButton.setOnClickListener {
            upgradeButton.isEnabled = false
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
        val cap  = UsageManager.getTokenCap(this)
        val isPro = UsageManager.isPro(this)

        val label = if (isPro) "🚀 GPT-4o Pro" else "⚡ Groq Free"
        usageText.text = "$label: $used / $cap tokens used"

        upgradeButton.isEnabled = !isPro
        if (isPro) upgradeButton.text = "Pro Active ✓"
    }

    override fun onPurchasesUpdated(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.products.contains(BillingManager.PRO_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
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
                    billingManager.launchPurchaseFlow(this, BillingManager.PRO_PRODUCT_ID)
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
            if (isFinishing || isDestroyed) return@queryProductDetailsAsync
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details.isEmpty()) {
                button.text = "Upgrade to GPT-4o Pro"
                // leave disabled or enable if you still want manual retry
                return@queryProductDetailsAsync
            }
            val pd = details.first()
            val formatted = pd.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice

            button.text = formatted?.let { "Upgrade to GPT-4o Pro ($it/month)" }
                ?: "Upgrade to GPT-4o Pro"
            button.isEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()
        billingManager.queryPurchases()
        updateUsageUI()
        if (!UsageManager.isPro(this)) upgradeButton.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        try { priceClient?.endConnection() } catch (_: Exception) {}
        priceClient = null
        try { billingManager.destroy() } catch (_: Exception) {}
    }

}