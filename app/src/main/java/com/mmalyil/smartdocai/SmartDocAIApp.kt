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

        // Woodstox factories
        try {
            System.setProperty(
                "javax.xml.stream.XMLInputFactory",
                "com.ctc.wstx.stax.WstxInputFactory"
            )
            System.setProperty(
                "javax.xml.stream.XMLOutputFactory",
                "com.ctc.wstx.stax.WstxOutputFactory"
            )
            System.setProperty(
                "javax.xml.stream.XMLEventFactory",
                "com.ctc.wstx.stax.WstxEventFactory"
            )
        } catch (_: Throwable) {
        }

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectCustomSlowCalls()
                    .permitNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectFileUriExposure()
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
                    // No UI here; BillingManager/UsageManager already handles entitlement.
                }
            }
        )

        // ✅ Close BillingClient only after both SUBS and INAPP queries return
        bm.queryPurchases(onComplete = {
            Handler(Looper.getMainLooper()).post {
                try { bm.destroy() } catch (_: Exception) {}
            }
        })
    }

}