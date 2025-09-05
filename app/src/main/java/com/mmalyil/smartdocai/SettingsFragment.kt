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
            val intent = Intent(requireContext(), OnboardingActivity::class.java)
            intent.putExtra("replay", true)
            startActivity(intent)
        }
        // 2) Show the guided walkthrough (coach-marks) again in Tools
        //    Make sure you added a button with id @+id/btnShowGuidedWalkthrough in fragment_settings.xml
        view.findViewById<Button>(R.id.btnShowGuidedWalkthrough)?.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                // Reset the one-time flag
                CoachPrefs.setCoachSeen(requireContext(), false)

                // Switch to Tools tab so the overlay appears immediately
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mathewmalyil.github.io/smartdocai-privacy/"))

            startActivity(intent)


        }

        view.findViewById<Button>(R.id.btnTerms).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/document/d/1AeuZk0xLW6qHw9VABC4Ei37iL0pCsRO4WxXe-P-3RWw/edit?usp=sharing"))
            startActivity(intent)
        }

        val billingButton = view.findViewById<Button>(R.id.btnManagePlan)
        billingButton.setOnClickListener {
            val intent = Intent(requireContext(), BillingActivity::class.java)
            intent.putExtra("message", "Upgrade now to unlock GPT-4o Pro 🚀")
            startActivity(intent)
        }
        return view
    }
}