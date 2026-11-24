package com.mmalyil.smartdocai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mmalyil.smartdocai.prefs.OnboardingPrefs
import com.mmalyil.smartdocai.ui.onboarding.OnboardingActivity
import com.mmalyil.smartdocai.util.UsageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var pendingNav: Int? = null
    private lateinit var adViewBanner: AdView
    private var bannerLoadScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Hide banner when keyboard is visible — use INVISIBLE so ConstraintLayout
        // never remasures contentFrame against a GONE ad (Pro users already GONE ads).
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val ad = findViewById<View>(R.id.adViewBanner)
            if (ad != null && ad.visibility != View.GONE) {
                // Only toggle when ads are enabled/shown; never GONE↔VISIBLE thrash layout.
                ad.visibility = if (imeVisible) View.INVISIBLE else View.VISIBLE
            }
            insets
        }

        // Theme observer — only apply when mode actually changes (avoids Activity recreate
        // that left contentFrame empty after cold Home paint).
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                com.mmalyil.smartdocai.prefs.ThemePrefs
                    .isDarkMode(this@MainActivity)
                    .collect { enabled ->
                        val want = if (enabled) {
                            AppCompatDelegate.MODE_NIGHT_YES
                        } else {
                            AppCompatDelegate.MODE_NIGHT_NO
                        }
                        if (AppCompatDelegate.getDefaultNightMode() != want) {
                            AppCompatDelegate.setDefaultNightMode(want)
                        }
                    }
            }
        }

        // ✅ Always init fragments & bottom nav synchronously
        initUi(savedInstanceState)

        // ✅ Then gate onboarding + external intents in a coroutine
        lifecycleScope.launch {
            val seen = OnboardingPrefs.hasSeen(this@MainActivity).first()
            if (!seen) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                // don't finish(), user comes back here afterward
            } else {
                // handle incoming intents once onboarding is done
                handleIncomingIntent(intent)
                maybeHandleDeepLinkFromOnboarding(intent)
            }
        }


        // Ads — deferred off cold-start / first paint (AdMob WebView ANR risk).
        adViewBanner = findViewById(R.id.adViewBanner)
        refreshBannerAd()

        // Restore Document Pro entitlement quietly on launch (after first frame)
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            try {
                val bm = com.mmalyil.smartdocai.billing.BillingManager(
                    this,
                    object : com.mmalyil.smartdocai.billing.BillingManager.BillingUpdateListener {
                        override fun onPurchasesUpdated(purchases: List<com.android.billingclient.api.Purchase>) {
                            refreshBannerAd()
                        }
                    }
                )
                bm.startConnection {
                    bm.queryPurchases {
                        runOnUiThread { refreshBannerAd() }
                        bm.destroy()
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Post-scan Analyse deep-link (soft CTA from ScanActivity)
        maybeOpenAnalysePdf(intent)
    }

    private fun refreshBannerAd() {
        if (!::adViewBanner.isInitialized) return
        val pro = com.mmalyil.smartdocai.util.DocumentEntitlement.isDocumentPro(this)
        if (!BuildConfig.ADS_ENABLED || pro) {
            bannerLoadScheduled = false
            try {
                adViewBanner.destroy()
            } catch (_: Exception) {
            }
            // INVISIBLE keeps a 0-size overlay without ConstraintLayout GONE side-effects
            // on siblings; contentFrame no longer depends on this view either way.
            adViewBanner.visibility = View.GONE
            return
        }
        adViewBanner.visibility = View.INVISIBLE
        if (bannerLoadScheduled) return
        bannerLoadScheduled = true
        // Keep AdMob WebView off the critical path: wait for idle + longer delay than 3.20.
        window.decorView.post {
            if (isFinishing || isDestroyed) return@post
            val queue = android.os.Looper.myQueue()
            queue.addIdleHandler {
                if (isFinishing || isDestroyed) return@addIdleHandler false
                window.decorView.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    if (com.mmalyil.smartdocai.util.DocumentEntitlement.isDocumentPro(this)) {
                        adViewBanner.visibility = View.GONE
                        return@postDelayed
                    }
                    try {
                        adViewBanner.loadAd(AdRequest.Builder().build())
                        adViewBanner.visibility = View.VISIBLE
                    } catch (_: Exception) {
                        adViewBanner.visibility = View.GONE
                    }
                }, 4500)
                false
            }
        }
    }

    // If your Activity is relaunched with a new Intent (rare here),
    // this will still catch the extras.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
        maybeHandleDeepLinkFromOnboarding(intent)
        maybeOpenAnalysePdf(intent)
    }

    /**
     * Soft CTA from Scan save: open Analyse tab and preload the just-saved PDF.
     * Reuses the existing share/open → Tools path (OCR/extract as usual).
     */
    private fun maybeOpenAnalysePdf(i: Intent?) {
        if (i == null) return
        val uriStr = i.getStringExtra(EXTRA_OPEN_ANALYSE_PDF_URI) ?: return
        i.removeExtra(EXTRA_OPEN_ANALYSE_PDF_URI)
        i.removeExtra(ScanActivity.EXTRA_OPEN_ANALYSE_AFTER_SAVE)
        val uri = runCatching { android.net.Uri.parse(uriStr) }.getOrNull() ?: return
        forwardToTools(uri, "application/pdf", "SCAN_SAVE")
    }

    private fun handleIncomingIntent(i: Intent?) {
        if (i == null) return
        if (i.getBooleanExtra(com.mmalyil.smartdocai.util.FilesHistory.EXTRA_OPEN_FILES, false)) {
            findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId = R.id.nav_files
            i.removeExtra(com.mmalyil.smartdocai.util.FilesHistory.EXTRA_OPEN_FILES)
        }
        when (i.action) {
            Intent.ACTION_VIEW -> i.data?.let {
                forwardToTools(it, i.type ?: guessMime(it), "VIEW")
            }

            Intent.ACTION_SEND -> {
                val u = i.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                val text = i.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    u != null -> forwardToTools(u, i.type ?: guessMime(u), "SEND")
                    !text.isNullOrBlank() -> forwardTextToTools(text)   // ← add this
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = i.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    .orEmpty()
                if (uris.isNotEmpty()) {
                    forwardToTools(
                        uris.first(),
                        i.type ?: guessMime(uris.first()),
                        "SEND_MULTIPLE"
                    )
                }
            }
        }
    }

    private fun forwardTextToTools(text: String) {
        android.widget.Toast.makeText(
            this,
            "Opening shared text…",
            android.widget.Toast.LENGTH_SHORT
        ).show()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (supportFragmentManager.isStateSaved) {
            pendingNav = R.id.nav_tools
        } else {
            bottomNav.selectedItemId = R.id.nav_tools
        }

        window.decorView.post {
            supportFragmentManager.setFragmentResult(
                "externalFile",
                bundleOf("text" to text)
            )
        }

        intent?.removeExtra(Intent.EXTRA_TEXT)
        intent?.action = null
    }

    private fun forwardToTools(uri: android.net.Uri, mime: String?, source: String) {
        // Human toast — never surface raw sources like SCAN_SAVE / VIEW.
        android.widget.Toast.makeText(
            this,
            "Opening on Analyse…",
            android.widget.Toast.LENGTH_SHORT
        ).show()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (supportFragmentManager.isStateSaved) {
            // defer until we’re safely resumed
            pendingNav = R.id.nav_tools
        } else {
            bottomNav.selectedItemId = R.id.nav_tools
        }

        // we can still post the result; ToolsFragment will pick it up when active
        window.decorView.post {
            supportFragmentManager.setFragmentResult(
                "externalFile",
                bundleOf(
                    "uri" to uri.toString(),
                    "mime" to (mime ?: "*/*"),
                    "source" to source
                )
            )
        }

        intent?.data = null
        intent?.removeExtra(Intent.EXTRA_STREAM)
    }


    private fun guessMime(uri: android.net.Uri): String {
        val s = uri.toString()
        return when {
            s.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            s.endsWith(
                ".docx",
                ignoreCase = true
            ) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

            s.endsWith(
                ".pptx",
                ignoreCase = true
            ) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"

            s.endsWith(
                ".xlsx",
                ignoreCase = true
            ) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

            s.endsWith(".csv", ignoreCase = true) -> "text/csv"
            else -> "*/*"
        }
    }


    // ... your existing code remains ...


    private fun initUi(savedInstanceState: Bundle?) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            switchFragment(item.itemId)
            true
        }

        // Always ensure a tab is shown (including after process death / theme recreate).
        val startId = when (bottomNav.selectedItemId) {
            R.id.nav_home, R.id.nav_tools, R.id.nav_files, R.id.nav_settings ->
                bottomNav.selectedItemId
            else -> R.id.nav_home
        }
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }
        // selectedItemId may no-op if already home — still attach/show fragment.
        switchFragment(if (savedInstanceState == null) R.id.nav_home else startId)

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { showFabActionSheet() }

        val tvUsageText = findViewById<TextView>(R.id.tvUsageText)
        val usageBar = findViewById<ProgressBar>(R.id.usageProgressBar)
        val tvAiSource = findViewById<TextView>(R.id.tvAiSource)

        findViewById<MaterialButton?>(R.id.btnQuickUpload)?.setOnClickListener {
            goToToolsAndAsk("upload")
        }
        findViewById<MaterialButton?>(R.id.btnQuickScan)?.setOnClickListener {
            startActivity(
                Intent(this, ScanActivity::class.java).apply {
                    putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_CAMERA)
                }
            )
        }
        findViewById<MaterialButton?>(R.id.btnQuickOCR)?.setOnClickListener {
            startActivity(
                Intent(this, ScanActivity::class.java).apply {
                    putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_PHOTOS)
                }
            )
        }

        refreshAiMeter()
    }

    /** Single Tools-tab strip: Free AI (Groq) · used / monthly cap. */
    private fun refreshAiMeter() {
        val tvUsageText = findViewById<TextView>(R.id.tvUsageText) ?: return
        val usageBar = findViewById<ProgressBar>(R.id.usageProgressBar) ?: return
        val tvAiSource = findViewById<TextView>(R.id.tvAiSource) ?: return
        UsageManager.bindUsageUI(
            context = this,
            usageTextView = tvUsageText,
            progressBar = usageBar,
            aiSourceTextView = tvAiSource
        )
    }

    /**
     * Show/hide tab fragments in a permanently-sized [R.id.contentFrame].
     * Never [replace] — replace + GONE hero/ad previously left a blank host after
     * leaving Home until Analyse forced a layout pass (3.19.2–3.19.3).
     */
    private fun switchFragment(itemId: Int) {
        if (supportFragmentManager.isStateSaved) {
            pendingNav = itemId
            return
        }

        val tag = tabTag(itemId) ?: return
        val contentFrame = findViewById<ViewGroup>(R.id.contentFrame) ?: return

        // Overlay only — must not resize contentFrame. Pad Tools content under hero.
        val hero = findViewById<View>(R.id.heroCard)
        val toolsSelected = itemId == R.id.nav_tools
        hero?.visibility = if (toolsSelected) View.VISIBLE else View.GONE
        if (!toolsSelected) {
            contentFrame.setPadding(0, 0, 0, 0)
        }

        contentFrame.visibility = View.VISIBLE
        contentFrame.minimumHeight = (240 * resources.displayMetrics.density).toInt()

        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)

        // Hide every tab fragment we know about.
        for (id in TAB_IDS) {
            val t = tabTag(id) ?: continue
            fm.findFragmentByTag(t)?.let { existing ->
                if (t != tag) {
                    tx.hide(existing)
                    existing.view?.visibility = View.GONE
                }
            }
        }

        var target = fm.findFragmentByTag(tag)
        if (target == null) {
            target = newTabFragment(itemId) ?: return
            tx.add(R.id.contentFrame, target, tag)
            Log.i(TAG, "TAB add $tag")
        } else {
            tx.show(target)
            Log.i(TAG, "TAB show $tag")
        }

        // commitNow so view is attached before we force visibility / log heights.
        try {
            tx.commitNowAllowingStateLoss()
        } catch (e: Exception) {
            Log.e(TAG, "TAB commit failed $tag", e)
            tx.commitAllowingStateLoss()
        }

        target.view?.visibility = View.VISIBLE
        target.view?.requestLayout()
        contentFrame.requestLayout()

        if (toolsSelected) {
            refreshAiMeter()
            if (hero != null) {
                hero.post {
                    val h = hero.height.coerceAtLeast(hero.measuredHeight)
                    contentFrame.setPadding(0, h, 0, 0)
                    logContentFrameHeight(tag)
                }
            }
        } else {
            contentFrame.post { logContentFrameHeight(tag) }
        }
    }

    private fun newTabFragment(itemId: Int): Fragment? = when (itemId) {
        R.id.nav_home -> HomeFragment()
        R.id.nav_tools -> ToolsFragment()
        R.id.nav_files -> FilesFragment()
        R.id.nav_settings -> SettingsFragment()
        else -> null
    }

    private fun tabTag(itemId: Int): String? = when (itemId) {
        R.id.nav_home -> TAG_HOME
        R.id.nav_tools -> TAG_TOOLS
        R.id.nav_files -> TAG_FILES
        R.id.nav_settings -> TAG_SETTINGS
        else -> null
    }

    private fun logContentFrameHeight(tag: String) {
        val frame = findViewById<View>(R.id.contentFrame) ?: return
        val hero = findViewById<View>(R.id.heroCard)
        val ad = findViewById<View>(R.id.adViewBanner)
        Log.i(
            TAG,
            "TAB layout tag=$tag contentFrame=${frame.width}x${frame.height} " +
                "minH=${frame.minimumHeight} vis=${frame.visibility} " +
                "heroVis=${hero?.visibility} adVis=${ad?.visibility} " +
                "childCount=${(frame as? ViewGroup)?.childCount}"
        )
    }

    companion object {
        private const val TAG = "MainTabs"
        private const val TAG_HOME = "tab_home"
        private const val TAG_TOOLS = "tab_tools"
        private const val TAG_FILES = "tab_files"
        private const val TAG_SETTINGS = "tab_settings"
        private val TAB_IDS = intArrayOf(
            R.id.nav_home, R.id.nav_tools, R.id.nav_files, R.id.nav_settings
        )

        /** ScanActivity → Analyse with PDF preloaded. */
        const val EXTRA_OPEN_ANALYSE_PDF_URI = "openAnalysePdfUri"
    }


    fun goToToolsAndAsk(action: String) {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (supportFragmentManager.isStateSaved) {
            pendingNav = R.id.nav_tools
        } else {
            nav.selectedItemId = R.id.nav_tools
        }
        window.decorView.post {
            supportFragmentManager.setFragmentResult(
                "toolsFabRequest",
                bundleOf("action" to action)
            )
        }
    }

    private fun maybeHandleDeepLinkFromOnboarding(intent: Intent) {
        val openUpload = intent.getBooleanExtra("openUpload", false)
        if (!openUpload) return

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (supportFragmentManager.isStateSaved) {
            pendingNav = R.id.nav_tools
        } else {
            bottomNav.selectedItemId = R.id.nav_tools
        }

        window.decorView.post {
            supportFragmentManager.setFragmentResult(
                "toolsFabRequest",
                bundleOf("action" to "upload")
            )
        }

        intent.removeExtra("openUpload")
    }

    private fun showFabActionSheet() {
        val bottomSheet = layoutInflater.inflate(R.layout.dialog_fab_actions, null)
        val dialog = BottomSheetDialog(this).apply { setContentView(bottomSheet) }

        bottomSheet.findViewById<LinearLayout>(R.id.actionUpload)?.setOnClickListener {
            goToToolsAndAsk("upload")
            dialog.dismiss()
        }
        bottomSheet.findViewById<LinearLayout>(R.id.actionScan)?.setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, ScanActivity::class.java).apply {
                    putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_CAMERA)
                }
            )
        }
        bottomSheet.findViewById<LinearLayout>(R.id.actionPickImage)?.setOnClickListener {
            dialog.dismiss()
            startActivity(
                Intent(this, ScanActivity::class.java).apply {
                    putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_PHOTOS)
                }
            )
        }

        dialog.show()
    }


    override fun onPause() {
        try { adViewBanner.pause() } catch (_: Exception) {}
        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        refreshAiMeter()
        refreshBannerAd()
        if (com.mmalyil.smartdocai.util.DocumentEntitlement.isDocumentPro(this)) {
            try { adViewBanner.pause() } catch (_: Exception) {}
        } else {
            try { adViewBanner.resume() } catch (_: Exception) {}
        }

        pendingNav?.let { id ->
            pendingNav = null
            window.decorView.post {
                if (!supportFragmentManager.isStateSaved) {
                    findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = id
                }
            }
        }
    }

    override fun onDestroy() {
        try { adViewBanner.destroy() } catch (_: Exception) {}
        super.onDestroy()
    }






}


