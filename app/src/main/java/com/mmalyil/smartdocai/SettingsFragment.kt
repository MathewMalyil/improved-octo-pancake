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


import com.mmalyil.smartdocai.ui.onboarding.OnboardingActivity

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

        view.findViewById<Button>(R.id.btnHelpFaq).setOnClickListener {
            startActivity(Intent(requireContext(), HelpFaqActivity::class.java))
        }

        view.findViewById<Button>(R.id.btnFeedback).setOnClickListener {
            startActivity(Intent(requireContext(), FeedbackActivity::class.java))
        }

        view.findViewById<Button>(R.id.btnPrivacyPolicy).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://docs.google.com/document/d/12ymF8jNmpgU9ncWeo7C0aDdIXzvu4jJsY8FTLC_CVTE/edit?usp=sharinglace with your doc link"))

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