package com.mmalyil.smartdocai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import android.widget.Toast

import android.content.Intent

import com.google.android.material.bottomnavigation.BottomNavigationView

import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.LinearLayout

// This is the main activity for the SmartDocAI application


// MainActivity.kt


// Constants for API keys









val groqApiKey= com.mmalyil.smartdocai.BuildConfig.GROQ_API_KEY





class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.title = "Send Feedback"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("onboarding", MODE_PRIVATE)
        val hasSeenIntro = prefs.getBoolean("seen", false)
        if (!hasSeenIntro) {
            startActivity(Intent(this, IntroActivity::class.java))
            prefs.edit().putBoolean("seen", true).apply()
            finish()
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
                    .replace(R.id.contentFrame, it, it::class.java.simpleName)
                    .commit()
                true
            } ?: false
        }

        // Load HomeFragment on first launch
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
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

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(bottomSheet)

        bottomSheet.findViewById<LinearLayout>(R.id.actionUpload)?.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.contentFrame)

            val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
            nav.selectedItemId = R.id.nav_tools

// Delay trigger just a bit to let fragment load
            nav.postDelayed({
                val toolsFragment = supportFragmentManager.findFragmentByTag(ToolsFragment::class.java.simpleName)
                if (toolsFragment is ToolsFragment) {
                    toolsFragment.triggerUploadFromFab()
                } else {
                    Toast.makeText(this, "Please try again", Toast.LENGTH_SHORT).show()
                }
            }, 150)
            dialog.dismiss()
        }

        bottomSheet.findViewById<LinearLayout>(R.id.actionScan)?.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.contentFrame)
            val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
            nav.selectedItemId = R.id.nav_tools
// Delay trigger just a bit to let fragment load
            nav.postDelayed({
                val toolsFragment = supportFragmentManager.findFragmentByTag(ToolsFragment::class.java.simpleName)
                if (toolsFragment is ToolsFragment) {
                    toolsFragment.triggerScanFromFab()
                } else {
                    Toast.makeText(this, "Please try again", Toast.LENGTH_SHORT).show()
                }
            }, 150)
            dialog.dismiss()
        }

        bottomSheet.findViewById<LinearLayout>(R.id.actionPickImage)?.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.contentFrame)
            val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
            nav.selectedItemId = R.id.nav_tools
// Delay trigger just a bit to let fragment load
            nav.postDelayed({
                val toolsFragment = supportFragmentManager.findFragmentByTag(ToolsFragment::class.java.simpleName)
                if (toolsFragment is ToolsFragment) {
                    toolsFragment.triggerPickImageFromFab()
                } else {
                    Toast.makeText(this, "Please try again", Toast.LENGTH_SHORT).show()
                }
            }, 150)
            dialog.dismiss()
        }

        dialog.show()
    }
}




