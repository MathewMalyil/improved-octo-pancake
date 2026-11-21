package com.mmalyil.smartdocai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
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
 *
 * When already Pro: one status line, purchase CTAs hidden (not three
 * identical “Document Pro active” buttons), remaining shown as Unlimited.
 */
class BillingActivity : AppCompatActivity(), BillingUpdateListener {

    private lateinit var statusText: TextView
    private lateinit var benefitsText: TextView
    private lateinit var freeNoteText: TextView
    private lateinit var howItWorksText: TextView
    private lateinit var recommendedLabel: TextView
    private lateinit var btnYearly: Button
    private lateinit var btnMonthly: Button
    private lateinit var btnLifetime: Button
    private lateinit var managePlayButton: Button
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
        howItWorksText = findViewById(R.id.tvHowItWorks)
        recommendedLabel = findViewById(R.id.tvRecommended)
        btnYearly = findViewById(R.id.btnYearly)
        btnMonthly = findViewById(R.id.btnMonthly)
        btnLifetime = findViewById(R.id.btnLifetime)
        managePlayButton = findViewById(R.id.btnManagePlay)
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
            • Free AI (Groq) within monthly token cap
        """.trimIndent()

        freeNoteText.text = freeCapsSummary(DocumentEntitlement.isDocumentPro(this))

        managePlayButton.setOnClickListener { openPlaySubscriptions() }

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

    private fun setPurchaseCtasVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        btnYearly.visibility = v
        recommendedLabel.visibility = v
        btnMonthly.visibility = v
        btnLifetime.visibility = v
    }

    private fun freeCapsSummary(pro: Boolean): String {
        val aiUsed = UsageManager.getGroqUsed(this)
        val aiCap = UsageManager.getGroqCap(this)
        return if (pro) {
            "Document tools: Unlimited. Free AI tokens this month: $aiUsed / $aiCap."
        } else {
            fun rem(f: DocumentEntitlement.Feature) =
                DocumentEntitlement.remainingToday(this, f)
            buildString {
                append("Today’s free remaining — ")
                append("Image→Excel ${rem(DocumentEntitlement.Feature.IMAGE_TO_EXCEL)}/${DocumentEntitlement.Feature.IMAGE_TO_EXCEL.dailyFreeLimit}, ")
                append("Scan→PDF ${rem(DocumentEntitlement.Feature.SCAN_TO_PDF)}/${DocumentEntitlement.Feature.SCAN_TO_PDF.dailyFreeLimit}, ")
                append("Clean share ${rem(DocumentEntitlement.Feature.CLEAN_SHARE)}/${DocumentEntitlement.Feature.CLEAN_SHARE.dailyFreeLimit}, ")
                append("AI report ${rem(DocumentEntitlement.Feature.AI_REPORT)}/${DocumentEntitlement.Feature.AI_REPORT.dailyFreeLimit}, ")
                append("Other exports ${rem(DocumentEntitlement.Feature.OTHER_EXPORT)}/${DocumentEntitlement.Feature.OTHER_EXPORT.dailyFreeLimit}. ")
                append("Free AI tokens this month: $aiUsed / $aiCap.")
            }
        }
    }

    private fun howDocumentProWorks(pro: Boolean, planLabel: String): String {
        return if (pro) {
            """
            How Document Pro works:
            • You’re on $planLabel — tools are unlimited; Free AI (Groq) stays on the monthly token cap.
            • Manage or cancel Yearly/Monthly in Google Play → Payments & subscriptions.
            • Lifetime cannot be cancelled (one-time purchase).
            """.trimIndent()
        } else {
            """
            How Document Pro works:
            • Choose Yearly (recommended), Monthly, or Lifetime below.
            • Free AI (Groq) stays free either way — Document Pro unlocks tools, Smaller compress, clean PDF share, and removes ads.
            • Manage or cancel Yearly/Monthly later in Google Play → Payments & subscriptions.
            • Lifetime cannot be cancelled (one-time purchase).
            """.trimIndent()
        }
    }

    private fun openPlaySubscriptions() {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            Toast.makeText(this, "Open Google Play → Payments & subscriptions", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshPurchaseUi() {
        val pro = DocumentEntitlement.isDocumentPro(this)
        freeNoteText.text = freeCapsSummary(pro)
        if (pro) {
            val src = when (DocumentEntitlement.proSource(this)) {
                DocumentEntitlement.ProSource.YEARLY -> "Yearly"
                DocumentEntitlement.ProSource.MONTHLY -> "Monthly"
                DocumentEntitlement.ProSource.LIFETIME -> "Lifetime"
                else -> "active"
            }
            statusText.text = "Document Pro unlocked · $src"
            howItWorksText.text = howDocumentProWorks(pro = true, planLabel = src)
            setPurchaseCtasVisible(false)
            setBuyingEnabled(false)
        } else {
            statusText.text = "Unlimited tools · ad-free · clean PDF export"
            howItWorksText.text = howDocumentProWorks(pro = false, planLabel = "")
            setPurchaseCtasVisible(true)
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
