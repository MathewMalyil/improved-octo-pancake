package com.mmalyil.smartdocai

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import com.android.billingclient.api.Purchase
import com.mmalyil.smartdocai.billing.BillingManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class SmartDocAIApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // 0) Keep your Woodstox selection
        try {
            System.setProperty("javax.xml.stream.XMLInputFactory", "com.ctc.wstx.stax.WstxInputFactory")
            System.setProperty("javax.xml.stream.XMLOutputFactory", "com.ctc.wstx.stax.WstxOutputFactory")
            System.setProperty("javax.xml.stream.XMLEventFactory", "com.ctc.wstx.stax.WstxEventFactory")
        } catch (_: Throwable) { /* ignore */ }

        // 1) StrictMode (debug only)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }

        // 2) One-time entitlement sync at cold start
        //    Calls queryPurchases and activates Pro if already owned.
        //    We destroy the billing client shortly after to avoid leaks.
        val bm = BillingManager(
            context = this,
            listener = object : BillingManager.BillingUpdateListener {
                override fun onPurchasesUpdated(purchases: List<Purchase>) {
                    // No UI here; BillingManager already grants entitlement via UsageManager.setPro().
                    // Nothing else needed.
                }
            }
        )
        bm.queryPurchases()

        // Tear down the connection a bit later (safe on main)
        Handler(Looper.getMainLooper()).postDelayed({
            try { bm.destroy() } catch (_: Exception) {}
        }, 1500L)
    }
}