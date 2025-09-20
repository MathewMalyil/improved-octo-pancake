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



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_billing)

        usageText = findViewById(R.id.tvUsageText)
        upgradeButton = findViewById(R.id.btnUpgrade)

        restoreButton = findViewById(R.id.btnRestore)

        billingManager = BillingManager(this, this)

        val isPro = UsageManager.isPro(this)
        if (!isPro) {
            upgradeButton.isEnabled = false
            upgradeButton.text = "Checking price..."
            billingManager.queryPrice(BillingManager.PRO_PRODUCT_ID) { price ->
                if (isFinishing || isDestroyed) return@queryPrice
                upgradeButton.text = price?.let { "Upgrade to GPT-4o Pro ($it/month)" } ?: "Upgrade to GPT-4o Pro"
                upgradeButton.isEnabled = true
            }
        } else {
            upgradeButton.isEnabled = false
            upgradeButton.text = "Pro Active ✓"
        }

        upgradeButton.setOnClickListener {
            if (!upgradeButton.isEnabled) return@setOnClickListener
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





    override fun onResume() {
        super.onResume()
        billingManager.queryPurchases()
        updateUsageUI()
        if (!UsageManager.isPro(this)) upgradeButton.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()

        try { billingManager.destroy() } catch (_: Exception) {}
    }

}