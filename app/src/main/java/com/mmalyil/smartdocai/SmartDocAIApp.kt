package com.mmalyil.smartdocai

import android.app.Application
import org.apache.poi.openxml4j.util.ZipSecureFile



class SmartDocAIApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Apache POI zip safety
        ZipSecureFile.setMinInflateRatio(0.0)
        ZipSecureFile.setMaxTextSize(50L * 1024 * 1024) // 50 MB

        // Prefer Woodstox for StAX on Android
        try {
            System.setProperty("javax.xml.stream.XMLInputFactory", "com.ctc.wstx.stax.WstxInputFactory")
            System.setProperty("javax.xml.stream.XMLOutputFactory", "com.ctc.wstx.stax.WstxOutputFactory")
            System.setProperty("javax.xml.stream.XMLEventFactory", "com.ctc.wstx.stax.WstxEventFactory")
        } catch (_: Throwable) {
            // ignore; fallback to whatever is available
        }
    }
}