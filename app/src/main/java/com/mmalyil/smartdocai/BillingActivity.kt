package com.mmalyil.smartdocai



import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.Purchase
import com.mmalyil.smartdocai.billing.BillingManager
import com.mmalyil.smartdocai.billing.BillingManager.BillingUpdateListener
import com.mmalyil.smartdocai.util.UsageManager

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

        upgradeButton.setOnClickListener {
            billingManager.launchPurchaseFlow(this, "ai_pro_plan")
        }

        restoreButton.setOnClickListener {
            Toast.makeText(this, "Restoring...", Toast.LENGTH_SHORT).show()
            // Will re-query on reconnect
        }

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
            if (purchase.products.contains("ai_pro_plan") && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                UsageManager.setPro(this, true)
                Toast.makeText(this, "Pro Activated!", Toast.LENGTH_LONG).show()
                updateUsageUI()
            }
        }
    }
}