package com.mmalyil.smartdocai
// SettingsFragment.kt

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mmalyil.smartdocai.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        view.findViewById<Button>(R.id.btnReplayOnboarding).setOnClickListener {
            startActivity(
                Intent(requireContext(), OnboardingActivity::class.java)
                    .putExtra("replay", true)
            )
        }

        view.findViewById<Button>(R.id.btnShowGuidedWalkthrough)?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                com.mmalyil.smartdocai.prefs.CoachPrefs.setCoachSeen(requireContext(), false)

                // switch to Tools
                requireActivity()
                    .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
                    .selectedItemId = R.id.nav_tools

                // ask ToolsFragment to show immediately
                requireActivity().supportFragmentManager.setFragmentResult(
                    "replayCoach",
                    bundleOf("replay" to true)
                )
            }
        }

        view.findViewById<Button>(R.id.btnHelpFaq).setOnClickListener {
            startActivity(Intent(requireContext(), HelpFaqActivity::class.java))
        }

        view.findViewById<Button>(R.id.btnFeedback).setOnClickListener {
            startActivity(Intent(requireContext(), FeedbackActivity::class.java))
        }

        // ✅ FIX: Safe open for URLs (prevents ActivityNotFoundException)
        view.findViewById<Button>(R.id.btnPrivacyPolicy).setOnClickListener {
            openUrlSafe("https://mathewmalyil.github.io/smartdocai-privacy/")
        }

        view.findViewById<Button>(R.id.btnTerms).setOnClickListener {
            openUrlSafe("https://docs.google.com/document/d/1AeuZk0xLW6qHw9VABC4Ei37iL0pCsRO4WxXe-P-3RWw/edit?usp=sharing")
        }

        view.findViewById<Button>(R.id.btnManagePlan).setOnClickListener {
            startActivity(
                Intent(requireContext(), BillingActivity::class.java)
                    .putExtra("message", "Unlock Document Pro — unlimited tools, ad-free, clean PDF export.")
            )
        }

        val switchDark = view.findViewById<SwitchMaterial>(R.id.switchDarkMode)

        // Lifecycle-safe collector
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                com.mmalyil.smartdocai.prefs.ThemePrefs
                    .isDarkMode(requireContext())
                    .collect { enabled ->
                        if (switchDark.isChecked != enabled) switchDark.isChecked = enabled
                    }
            }
        }

        switchDark.setOnCheckedChangeListener { _, isChecked ->
            viewLifecycleOwner.lifecycleScope.launch {
                com.mmalyil.smartdocai.prefs.ThemePrefs.setDarkMode(requireContext(), isChecked)
            }
        }

        return view
    }

    private fun openUrlSafe(url: String) {
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        try {
            val pm = requireContext().packageManager
            if (intent.resolveActivity(pm) != null) {
                startActivity(Intent.createChooser(intent, "Open with"))
            } else {
                Toast.makeText(requireContext(), "No browser found to open this link.", Toast.LENGTH_LONG).show()
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No app available to open links on this device.", Toast.LENGTH_LONG).show()
        }
    }
}