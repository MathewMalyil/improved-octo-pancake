package com.mmalyil.smartdocai

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmartDocAIApp : Application() {

    // App-wide scope for background init work
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

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

        // Heavy libs + light Ads config on background (never block Application.onCreate)
        appScope.launch {
            safeInitPdfBox()
            safeInitXmlFactories()
            // Only light Ads config; no MobileAds.initialize to avoid WebView/Dex ANRs
            initMobileAdsSafely()
        }
    }

    private fun safeInitPdfBox() {
        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (t: Throwable) {
            Log.e("SmartDocAIApp", "Failed to init PDFBox", t)
        }
    }

    private fun safeInitXmlFactories() {
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
            // Ignore – not fatal
        }
    }

    private var adsInitAttempted = false

    private fun initMobileAdsSafely() {
        if (adsInitAttempted) return
        adsInitAttempted = true

        try {
            if (BuildConfig.DEBUG) {
                val requestConfig = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
                MobileAds.setRequestConfiguration(requestConfig)
            }
            // No MobileAds.initialize() here – AdMob will auto-init on first ad load.
        } catch (t: Throwable) {
            // Critical: never crash or ANR the app just because Ads/WebView are broken
            Log.e("SmartDocAIApp", "Failed to configure MobileAds", t)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}