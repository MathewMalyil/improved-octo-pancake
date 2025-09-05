package com.mmalyil.smartdocai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.mmalyil.smartdocai.prefs.OnboardingPrefs
import com.mmalyil.smartdocai.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

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

                // 3) If onboarding asked to open Upload, handle it now
                maybeHandleDeepLinkFromOnboarding(intent)
            }
        }
    }

    // If your Activity is relaunched with a new Intent (rare here),
    // this will still catch the extras.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // keep the new Intent so getIntent() returns it
        setIntent(intent)
        maybeHandleDeepLinkFromOnboarding(intent)
    }

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

        fun goToToolsAndAsk(action: String) {
            val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
            nav.selectedItemId = R.id.nav_tools
            window.decorView.post {
                supportFragmentManager.setFragmentResult(
                    "toolsFabRequest",
                    bundleOf("action" to action)
                )
            }
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