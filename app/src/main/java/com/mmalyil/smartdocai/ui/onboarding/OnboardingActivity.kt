package com.mmalyil.smartdocai.ui.onboarding



import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.mmalyil.smartdocai.ui.theme.SmartDocAITheme
import androidx.compose.runtime.setValue
import com.mmalyil.smartdocai.MainActivity

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmartDocAITheme {

                // ✅ MUST be inside setContent
                var screen by remember { mutableStateOf(1) }

                when (screen) {
                    1 -> OnboardingScreen1 { screen = 2 }
                    2 -> OnboardingScreen2 { screen = 3 }
                    3 -> OnboardingScreen3 { screen = 4 }
                    4 -> OnboardingScreen4 {
                        // ✅ Optional: Save flag to skip onboarding next time
                        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
                        prefs.edit().putBoolean("onboarding_complete", true).apply()

                        // ✅ Go to main app
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }
}