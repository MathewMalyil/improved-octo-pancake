package com.mmalyil.smartdocai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import com.mmalyil.smartdocai.ui.onboarding.OnboardingActivity
import androidx.core.os.bundleOf

// This is the main activity for the SmartDocAI application


// MainActivity.kt


// Constants for API keys















class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge()  // ← solves the issue instantly

        setContentView(R.layout.activity_main)

        supportActionBar?.title = "Send Feedback"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ✅ Use updated preference key
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("onboarding_complete", false)

        if (!hasCompletedOnboarding) {
            // ✅ Launch your new Compose-based onboarding
            val intent = Intent(this, OnboardingActivity::class.java)
            intent.putExtra("replay", true)
            startActivity(intent)
            finish()
            return
        }


        // Initialize BottomNavigationView and set up item selection listener

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

        // Load HomeFragment on first launch
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.contentFrame, HomeFragment(), HomeFragment::class.java.simpleName)
                .commit()
        }


        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            showFabActionSheet()
        }
    }


    private fun showFabActionSheet() {
        val bottomSheet = layoutInflater.inflate(R.layout.dialog_fab_actions, null)
        val dialog = BottomSheetDialog(this).apply { setContentView(bottomSheet) }

        // helper to switch to Tools and then send the action
        fun goToToolsAndAsk(action: String) {
            // 1) switch tabs first so ToolsFragment is (re)created
            val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
            nav.selectedItemId = R.id.nav_tools

            // 2) post result on next loop so listener in ToolsFragment(onCreate) is ready
            window.decorView.post {
                supportFragmentManager.setFragmentResult(
                    "toolsFabRequest",
                    bundleOf("action" to action)
                )
            }

            dialog.dismiss()
        }

        // ⬇️ these MUST be OUTSIDE the function
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



