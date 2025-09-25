package com.mmalyil.smartdocai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mmalyil.smartdocai.util.UsageManager

import android.widget.ProgressBar



class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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

        supportActionBar?.title = "Send Feedback"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 1) Gate with DataStore once (no legacy SharedPreferences)
        lifecycleScope.launch {
            val seen = OnboardingPrefs.hasSeen(this@MainActivity).first()
            if (!seen) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                // Don't finish() — let user come back here after onboarding
                return@launch
            } else {
                // 2) Normal UI init only after onboarding is seen
                initUi(savedInstanceState)


                // ✅ handle external file intents on first launch
                handleIncomingIntent(intent)
                // keep your existing onboarding deep link:

                // 3) If onboarding asked to open Upload, handle it now
                maybeHandleDeepLinkFromOnboarding(intent)
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
                val uris = i.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM).orEmpty()
                if (uris.isNotEmpty()) {
                    forwardToTools(uris.first(), i.type ?: guessMime(uris.first()), "SEND_MULTIPLE")
                }
            }
        }
    }

    private fun forwardTextToTools(text: String) {
        // visible feedback
        android.widget.Toast.makeText(this, "Opening shared text…", android.widget.Toast.LENGTH_SHORT).show()

        // Switch to Tools tab
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
            .selectedItemId = R.id.nav_tools

        // deliver to ToolsFragment
        window.decorView.post {
            supportFragmentManager.setFragmentResult(
                "externalFile",
                androidx.core.os.bundleOf("text" to text)
            )
        }

        // clear so it doesn’t retrigger
        intent?.removeExtra(Intent.EXTRA_TEXT)
        intent?.action = null
    }

    private fun forwardToTools(uri: android.net.Uri, mime: String?, source: String) {
        // visible feedback so it never feels “dead”
        android.widget.Toast.makeText(this, "Opening file from $source…", android.widget.Toast.LENGTH_SHORT).show()

        // switch to Tools tab
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
            .selectedItemId = R.id.nav_tools

        // deliver to ToolsFragment
        window.decorView.post {
            supportFragmentManager.setFragmentResult(
                "externalFile",
                androidx.core.os.bundleOf(
                    "uri" to uri.toString(),
                    "mime" to (mime ?: "*/*"),
                    "source" to source
                )
            )
        }

        // clear so back/rotate doesn't retrigger
        intent?.data = null
        intent?.removeExtra(Intent.EXTRA_STREAM)
    }



    private fun guessMime(uri: android.net.Uri): String {
        val s = uri.toString()
        return when {
            s.endsWith(".pdf", ignoreCase = true)  -> "application/pdf"
            s.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            s.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            s.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            s.endsWith(".csv",  ignoreCase = true) -> "text/csv"
            else                                   -> "*/*"
        }
    }



    // ... your existing code remains ...



    private fun initUi(savedInstanceState: Bundle?) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            val selectedFragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_tools -> ToolsFragment()
                R.id.nav_files -> FilesFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> null
            }
            selectedFragment?.let {
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .replace(R.id.contentFrame, it, it::class.java.simpleName)
                    .commit()
                true
            } ?: false
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.contentFrame, HomeFragment(), HomeFragment::class.java.simpleName)
                .commit()
        }

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener { showFabActionSheet() }

        // ✅ Hero card views (match your XML IDs)
        val tvUsageText  = findViewById<TextView>(R.id.tvUsageText)
        val usageBar     = findViewById<ProgressBar>(R.id.usageProgressBar) // NOTE: platform ProgressBar
        val tvAiSource   = findViewById<TextView>(R.id.tvAiSource)

        // Optional quick buttons if you have them
        findViewById<MaterialButton?>(R.id.btnQuickUpload)?.setOnClickListener { goToToolsAndAsk("upload") }
        findViewById<MaterialButton?>(R.id.btnQuickScan)?.setOnClickListener   { goToToolsAndAsk("scan") }
        findViewById<MaterialButton?>(R.id.btnQuickOCR)?.setOnClickListener    { goToToolsAndAsk("pickImage") }

        // 🔗 Bind usage UI (single source of truth)
        UsageManager.bindUsageUI(
            context = this,
            usageTextView = tvUsageText,
            progressBar = usageBar,
            aiSourceTextView = tvAiSource
        )
    }

    fun goToToolsAndAsk(action: String) {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_tools
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

        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_tools

        window.decorView.post {
            supportFragmentManager.setFragmentResult(
                "toolsFabRequest",
                bundleOf("action" to "upload")
            )
        }

        // clear the flag so it doesn't retrigger
        intent.removeExtra("openUpload")
    }

    private fun showFabActionSheet() {
        val bottomSheet = layoutInflater.inflate(R.layout.dialog_fab_actions, null)
        val dialog = BottomSheetDialog(this).apply { setContentView(bottomSheet) }

        fun choose(action: String) {
            goToToolsAndAsk(action)
            dialog.dismiss()
        }

        bottomSheet.findViewById<LinearLayout>(R.id.actionUpload)?.setOnClickListener {
            goToToolsAndAsk("upload")
        }
        bottomSheet.findViewById<LinearLayout>(R.id.actionScan)?.setOnClickListener {
            goToToolsAndAsk("scan")
        }
        bottomSheet.findViewById<LinearLayout>(R.id.actionPickImage)?.setOnClickListener {
            goToToolsAndAsk("pickImage")
        }

        dialog.show()
    }


}