package com.mmalyil.smartdocai.util



object PoiSetup {
    @JvmStatic fun prepare() {
        try {
            // Force Woodstox as StAX provider on Android
            System.setProperty("javax.xml.stream.XMLInputFactory", "com.ctc.wstx.stax.WstxInputFactory")
            System.setProperty("javax.xml.stream.XMLOutputFactory", "com.ctc.wstx.stax.WstxOutputFactory")
            System.setProperty("javax.xml.stream.XMLEventFactory", "com.ctc.wstx.stax.WstxEventFactory")

            // Preload key classes so R8 won’t strip them
            Class.forName("com.ctc.wstx.stax.WstxInputFactory")
            Class.forName("org.codehaus.stax2.XMLInputFactory2")
        } catch (_: Throwable) {
            // ignore; fallback might still work
        }



    }
}