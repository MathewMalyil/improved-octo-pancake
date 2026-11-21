package com.mmalyil.smartdocai

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.Purchase
import com.mmalyil.smartdocai.billing.BillingManager
import com.mmalyil.smartdocai.billing.BillingManager.BillingUpdateListener
import com.mmalyil.smartdocai.util.DocumentEntitlement
import com.mmalyil.smartdocai.util.UsageManager

/**
 * Document Pro paywall: Yearly (default), Monthly, Lifetime.
 * Free Groq AI stays free; document tools unlock with any Pro SKU.
 */
class BillingActivity : AppCompatActivity(), BillingUpdateListener {

    private lateinit var statusText: TextView
    private lateinit var benefitsText: TextView
    private lateinit var freeNoteText: TextView
    private lateinit var btnYearly: Button
    private lateinit var btnMonthly: Button
    private lateinit var btnLifetime: Button
    private lateinit var restoreButton: Button
    private lateinit var billingManager: BillingManager

    private var priceYearly: String = BillingManager.FALLBACK_PRICE_YEARLY
    private var priceMonthly: String = BillingManager.FALLBACK_PRICE_MONTHLY
    private var priceLifetime: String = BillingManager.FALLBACK_PRICE_LIFETIME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_billing)

        statusText = findViewById(R.id.tvStatus)
        benefitsText = findViewById(R.id.tvBenefits)
        freeNoteText = findViewById(R.id.tvFreeNote)
        btnYearly = findViewById(R.id.btnYearly)
        btnMonthly = findViewById(R.id.btnMonthly)
        btnLifetime = findViewById(R.id.btnLifetime)
        restoreButton = findViewById(R.id.btnRestore)

        benefitsText.text = """
            What you unlock with Document Pro:
            • Unlimited Image → Excel, multi-page scan → PDF
            • Unlimited Excel ↔ Word / AI report & other exports
            • Clean PDF Share & Save to Downloads (no free watermark)
            • Ad-free app

            Free forever (no purchase):
            • Open / view docs & share-in
            • Soft daily tool caps while you try Pro
            • Free AI (summary, key points, chat) within monthly token cap
        """.trimIndent()

        freeNoteText.text = freeCapsSummary()

        billingManager = BillingManager(this, this)

        billingManager.startConnection {
            if (isFinishing || isDestroyed) return@startConnection
            refreshPurchaseUi()
            billingManager.queryPurchases()
            if (!DocumentEntitlement.isDocumentPro(this)) {
                setBuyingEnabled(false)
                billingManager.queryAllPrices { prices ->
                    if (isFinishing || isDestroyed) return@queryAllPrices
                    prices.forEach { info ->
                        when (info.productId) {
                            BillingManager.DOCUMENT_PRO_YEARLY -> priceYearly = info.display()
                            BillingManager.DOCUMENT_PRO_MONTHLY -> priceMonthly = info.display()
                            BillingManager.DOCUMENT_PRO_LIFETIME -> priceLifetime = info.display()
                        }
                    }
                    refreshPurchaseUi()
                }
            }
        }

        btnYearly.setOnClickListener {
            launchSku(BillingManager.DOCUMENT_PRO_YEARLY, btnYearly)
        }
        btnMonthly.setOnClickListener {
            launchSku(BillingManager.DOCUMENT_PRO_MONTHLY, btnMonthly)
        }
        btnLifetime.setOnClickListener {
            launchSku(BillingManager.DOCUMENT_PRO_LIFETIME, btnLifetime)
        }

        restoreButton.setOnClickListener {
            Toast.makeText(this, "Restoring…", Toast.LENGTH_SHORT).show()
            billingManager.queryPurchases {
                runOnUiThread {
                    if (DocumentEntitlement.isDocumentPro(this)) {
                        Toast.makeText(this, "Document Pro restored", Toast.LENGTH_LONG).show()
                    } else {
                        // Owned ai_pro_plan (if any) is ignored — does not restore Document Pro.
                        Toast.makeText(
                            this,
                            "No Document Pro purchase found (legacy AI Pro is not restored)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    refreshPurchaseUi()
                }
            }
        }

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        maybeShowLimitDialog()
        refreshPurchaseUi()
    }

    private fun launchSku(productId: String, button: Button) {
        if (DocumentEntitlement.isDocumentPro(this)) return
        if (!button.isEnabled) return
        setBuyingEnabled(false)
        billingManager.launchPurchaseFlow(this, productId)
        button.postDelayed({
            if (!isFinishing && !isDestroyed && !DocumentEntitlement.isDocumentPro(this)) {
                setBuyingEnabled(true)
            }
        }, 1600)
    }

    private fun setBuyingEnabled(enabled: Boolean) {
        btnYearly.isEnabled = enabled
        btnMonthly.isEnabled = enabled
        btnLifetime.isEnabled = enabled
    }

    private fun freeCapsSummary(): String {
        val aiUsed = UsageManager.getGroqUsed(this)
        val aiCap = UsageManager.getGroqCap(this)
        return buildString {
            append("Today’s free remaining — ")
            append("Image→Excel ${DocumentEntitlement.remainingToday(this@BillingActivity, DocumentEntitlement.Feature.IMAGE_TO_EXCEL)}/${DocumentEntitlement.Feature.IMAGE_TO_EXCEL.dailyFreeLimit}, ")
            append("Scan→PDF ${DocumentEntitlement.remainingToday(this@BillingActivity, DocumentEntitlement.Feature.SCAN_TO_PDF)}/${DocumentEntitlement.Feature.SCAN_TO_PDF.dailyFreeLimit}, ")
            append("Clean share ${DocumentEntitlement.remainingToday(this@BillingActivity, DocumentEntitlement.Feature.CLEAN_SHARE)}/${DocumentEntitlement.Feature.CLEAN_SHARE.dailyFreeLimit}, ")
            append("AI report ${DocumentEntitlement.remainingToday(this@BillingActivity, DocumentEntitlement.Feature.AI_REPORT)}/${DocumentEntitlement.Feature.AI_REPORT.dailyFreeLimit}, ")
            append("Other exports ${DocumentEntitlement.remainingToday(this@BillingActivity, DocumentEntitlement.Feature.OTHER_EXPORT)}/${DocumentEntitlement.Feature.OTHER_EXPORT.dailyFreeLimit}. ")
            append("Free AI tokens this month: $aiUsed / $aiCap.")
        }
    }

    private fun refreshPurchaseUi() {
        val pro = DocumentEntitlement.isDocumentPro(this)
        freeNoteText.text = freeCapsSummary()
        if (pro) {
            val src = when (DocumentEntitlement.proSource(this)) {
                DocumentEntitlement.ProSource.YEARLY -> "Yearly"
                DocumentEntitlement.ProSource.MONTHLY -> "Monthly"
                DocumentEntitlement.ProSource.LIFETIME -> "Lifetime"
                else -> "Pro"
            }
            statusText.text = "Document Pro unlocked ✓ ($src)"
            btnYearly.isEnabled = false
            btnMonthly.isEnabled = false
            btnLifetime.isEnabled = false
            btnYearly.text = "Document Pro active"
            btnMonthly.text = "Document Pro active"
            btnLifetime.text = "Document Pro active"
        } else {
            statusText.text = "Unlimited tools · ad-free · clean PDF export"
            setBuyingEnabled(true)
            btnYearly.text = "Yearly — $priceYearly  ·  best value"
            btnMonthly.text = "Monthly — $priceMonthly"
            btnLifetime.text = "Lifetime — $priceLifetime  ·  one-time"
        }
    }

    override fun onPurchasesUpdated(purchases: List<Purchase>) {
        val owned = purchases.any { p ->
            p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                p.products.any { it in BillingManager.ALL_PRO_PRODUCT_IDS }
        }
        if (owned) {
            Toast.makeText(this, "Document Pro unlocked!", Toast.LENGTH_LONG).show()
        }
        refreshPurchaseUi()
    }

    override fun onProductUnavailable(productId: String, debugMessage: String?) {
        runOnUiThread {
            setBuyingEnabled(true)
            Toast.makeText(
                this,
                "Product “$productId” not in Play yet. Create it in Play Console (see ship notes), then try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun maybeShowLimitDialog() {
        val message = intent.getStringExtra("message") ?: return
        if (message.isBlank()) return
        android.app.AlertDialog.Builder(this)
            .setTitle("Document Pro")
            .setMessage(message.trim())
            .setPositiveButton("See plans") { _, _ -> /* already on paywall */ }
            .setNegativeButton("Not now") { _, _ -> finish() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        billingManager.queryPurchases { runOnUiThread { refreshPurchaseUi() } }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            billingManager.destroy()
        } catch (_: Exception) {
        }
    }
}
