package com.mmalyil.smartdocai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.mmalyil.smartdocai.prefs.OnboardingPrefs
import com.mmalyil.smartdocai.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.mmalyil.smartdocai.util.UsageManager
import android.widget.ProgressBar
import com.mmalyil.smartdocai.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private var pendingNav: Int? = null   // ✅ add this
    private lateinit var adViewBanner: AdView   // 👈 add this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


// Hide banner when keyboard is visible (IME)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            findViewById<View>(R.id.adViewBanner)?.visibility =
                if (imeVisible) View.GONE else View.VISIBLE
            insets
        }


        // Theme observer (unchanged)
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                com.mmalyil.smartdocai.prefs.ThemePrefs
                    .isDarkMode(this@MainActivity)
                    .collect { enabled ->
                        AppCompatDelegate.setDefaultNightMode(
                            if (enabled) AppCompatDelegate.MODE_NIGHT_YES
                            else AppCompatDelegate.MODE_NIGHT_NO
                        )
                    }
            }
        }

        // Action bar – honestly this title looks like it belongs to another screen,
        // but it's not related to the crash so I’m leaving it.
        supportActionBar?.title = "Send Feedback"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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


        // Ads — hidden for Document Pro (and when ADS_ENABLED is false in debug)
        adViewBanner = findViewById(R.id.adViewBanner)
        refreshBannerAd()

        // Restore Document Pro entitlement quietly on launch
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

    private fun refreshBannerAd() {
        if (!::adViewBanner.isInitialized) return
        val pro = com.mmalyil.smartdocai.util.DocumentEntitlement.isDocumentPro(this)
        if (!BuildConfig.ADS_ENABLED || pro) {
            try {
                adViewBanner.destroy()
            } catch (_: Exception) {
            }
            adViewBanner.visibility = View.GONE
            return
        }
        adViewBanner.visibility = View.INVISIBLE
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
        }, 3500)
    }

    // If your Activity is relaunched with a new Intent (rare here),
    // this will still catch the extras.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
        maybeHandleDeepLinkFromOnboarding(intent)
    }

    private fun handleIncomingIntent(i: Intent?) {
        if (i == null) return
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
        android.widget.Toast.makeText(
            this,
            "Opening file from $source…",
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

        if (savedInstanceState == null) {
            // Start with Home; keep bottom-nav selection in sync (listener may not fire).
            bottomNav.selectedItemId = R.id.nav_home
            switchFragment(R.id.nav_home)
        }


        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { showFabActionSheet() }

        // ✅ Hero card views (match your XML IDs)
        val tvUsageText = findViewById<TextView>(R.id.tvUsageText)
        val usageBar =
            findViewById<ProgressBar>(R.id.usageProgressBar) // NOTE: platform ProgressBar
        val tvAiSource = findViewById<TextView>(R.id.tvAiSource)

        // Optional quick buttons if you have them
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

        // 🔗 Bind usage UI (single source of truth)
        UsageManager.bindUsageUI(
            context = this,
            usageTextView = tvUsageText,
            progressBar = usageBar,
            aiSourceTextView = tvAiSource
        )
    }

    private fun switchFragment(itemId: Int) {
        if (supportFragmentManager.isStateSaved) {
            pendingNav = itemId
            return
        }

        val tag = when (itemId) {
            R.id.nav_home -> HomeFragment::class.java.simpleName
            R.id.nav_tools -> ToolsFragment::class.java.simpleName
            R.id.nav_files -> FilesFragment::class.java.simpleName
            R.id.nav_settings -> SettingsFragment::class.java.simpleName
            else -> return
        }

        val currentFragment = supportFragmentManager.findFragmentById(R.id.contentFrame)
        if (currentFragment?.tag == tag) return

        val newFragment = when (itemId) {
            R.id.nav_home -> HomeFragment()
            R.id.nav_tools -> ToolsFragment()
            R.id.nav_files -> FilesFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> return
        }

        // Home owns job CTAs — hide usage strip there so jobs are immediate.
        // Tools keeps the compact AI usage strip.
        // contentFrame is constrained via contentTopBarrier (not directly to heroCard)
        // so GONE heroCard never collapses the fragment host to 0 height.
        val hero = findViewById<View>(R.id.heroCard)
        hero?.visibility = if (itemId == R.id.nav_tools) View.VISIBLE else View.GONE
        findViewById<View>(R.id.contentFrame)?.requestLayout()

        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.contentFrame, newFragment, tag)
            .commitAllowingStateLoss()
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


