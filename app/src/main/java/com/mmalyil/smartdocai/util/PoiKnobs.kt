package com.mmalyil.smartdocai.util



object PoiKnobs {
    /** Call before parsing DOCX/XLSX/PPTX. Uses reflection so no compile-time imports. */
    fun relax() {
        try {
            val zsf = Class.forName("org.apache.poi.openxml4j.util.ZipSecureFile")
            // setMinInflateRatio(double)
            zsf.getMethod("setMinInflateRatio", java.lang.Double.TYPE)
                .invoke(null, 0.01)
            // setMaxTextSize(long)
            zsf.getMethod("setMaxTextSize", java.lang.Long.TYPE)
                .invoke(null, 50L * 1024 * 1024)
        } catch (_: Throwable) {
            // Safe to ignore; defaults will be used
        }
    }
}