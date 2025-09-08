package com.mmalyil.smartdocai
// SettingsFragment.kt


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.Button
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.mmalyil.smartdocai.prefs.CoachPrefs
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.mmalyil.smartdocai.ui.onboarding.OnboardingActivity
import kotlinx.coroutines.launch

// SettingsFragment.kt

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        view.findViewById<Button>(R.id.btnReplayOnboarding).setOnClickListener {
            startActivity(Intent(requireContext(), OnboardingActivity::class.java).putExtra("replay", true))
        }

        view.findViewById<Button>(R.id.btnShowGuidedWalkthrough)?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                CoachPrefs.setCoachSeen(requireContext(), false)
                requireActivity()
                    .findViewById<BottomNavigationView>(R.id.bottomNav)
                    .selectedItemId = R.id.nav_tools
            }
        }

        view.findViewById<Button>(R.id.btnHelpFaq).setOnClickListener {
            startActivity(Intent(requireContext(), HelpFaqActivity::class.java))
        }
        view.findViewById<Button>(R.id.btnFeedback).setOnClickListener {
            startActivity(Intent(requireContext(), FeedbackActivity::class.java))
        }
        view.findViewById<Button>(R.id.btnPrivacyPolicy).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mathewmalyil.github.io/smartdocai-privacy/")))
        }
        view.findViewById<Button>(R.id.btnTerms).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/document/d/1AeuZk0xLW6qHw9VABC4Ei37iL0pCsRO4WxXe-P-3RWw/edit?usp=sharing")))
        }

        val billingButton = view.findViewById<Button>(R.id.btnManagePlan)
        billingButton.setOnClickListener {
            startActivity(Intent(requireContext(), BillingActivity::class.java).putExtra("message", "Upgrade now to unlock GPT-4o Pro 🚀"))
        }

        val switchDark = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchDarkMode)

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
}