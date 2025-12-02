package com.mmalyil.smartdocai

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.*

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

        // Heavy libs on background
        appScope.launch {
            safeInitPdfBox()
            safeInitXmlFactories()
        }

        // ❗ Do NOT sync billing or ads during cold start.
        // Delay Ads init so WebView/Ads cannot cause ANR on startup.
        Handler(Looper.getMainLooper()).postDelayed(
            { initMobileAdsSafely() },
            3_000L // 3 seconds after launch; tweak as you like
        )
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
        }
    }

    private var adsInitAttempted = false

    private fun initMobileAdsSafely() {
        if (adsInitAttempted) return
        adsInitAttempted = true

        try {
            if (BuildConfig.DEBUG) {
                val requestConfig = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf("EMULATOR"))
                    .build()
                MobileAds.setRequestConfiguration(requestConfig)
            }

            MobileAds.initialize(this) {
                Log.d("SmartDocAIApp", "MobileAds initialized: $it")
            }
        } catch (t: Throwable) {
            // Critical: never crash or ANR the app just because Ads/WebView are broken
            Log.e("SmartDocAIApp", "Failed to initialize MobileAds, disabling ads for this session", t)
            // Optionally: set a global flag if you want to skip loading ad views this run
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        appScope.cancel()
    }
}