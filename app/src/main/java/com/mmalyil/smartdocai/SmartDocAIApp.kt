package com.mmalyil.smartdocai

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import com.android.billingclient.api.Purchase
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.mmalyil.smartdocai.billing.BillingManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class SmartDocAIApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1) PDFBox
        PDFBoxResourceLoader.init(applicationContext)

        // 2) Woodstox factories
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

        // 3) StrictMode (debug-only)
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



        if (BuildConfig.DEBUG) {
            val requestConfig = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("EMULATOR"))
                .build()

            MobileAds.setRequestConfiguration(requestConfig)
        }

        MobileAds.initialize(this) { }


        // 5) One-time entitlement sync at cold start
        val bm = BillingManager(
            context = this,
            listener = object : BillingManager.BillingUpdateListener {
                override fun onPurchasesUpdated(purchases: List<Purchase>) {
                    // No UI here; BillingManager/UsageManager handles entitlement flags.
                }
            }
        )

        // Close BillingClient after queries complete
        bm.queryPurchases(onComplete = {
            Handler(Looper.getMainLooper()).post {
                try {
                    bm.destroy()
                } catch (_: Exception) {
                }
            }
        })
    }
}