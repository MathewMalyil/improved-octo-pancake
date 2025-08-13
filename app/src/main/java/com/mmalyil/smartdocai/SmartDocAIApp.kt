package com.mmalyil.smartdocai

import android.app.Application




class SmartDocAIApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Don’t touch Apache POI here. Startup must be clean.
        // Woodstox selection is fine to keep, but guard it.
        try {
            System.setProperty("javax.xml.stream.XMLInputFactory", "com.ctc.wstx.stax.WstxInputFactory")
            System.setProperty("javax.xml.stream.XMLOutputFactory", "com.ctc.wstx.stax.WstxOutputFactory")
            System.setProperty("javax.xml.stream.XMLEventFactory", "com.ctc.wstx.stax.WstxEventFactory")
        } catch (_: Throwable) { /* ignore */ }
    }
}