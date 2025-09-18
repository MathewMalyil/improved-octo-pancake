@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package com.mmalyil.smartdocai.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.mmalyil.smartdocai.MainActivity
import com.mmalyil.smartdocai.prefs.OnboardingPrefs
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {

    private fun goToMain(openUpload: Boolean = false) {
        val i = Intent(this@OnboardingActivity, MainActivity::class.java).apply {
            putExtra("fromOnboarding", true)
            putExtra("openUpload", openUpload)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(i)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isReplay = intent?.getBooleanExtra("replay", false) ?: false

        setContent {
            val darkMode by com.mmalyil.smartdocai.prefs.ThemePrefs
                .isDarkMode(this)
                .collectAsState(initial = false)

            com.mmalyil.smartdocai.ui.theme.SmartDocAITheme(darkTheme = darkMode) {
                val scope = rememberCoroutineScope()
                val pagerState = rememberPagerState(pageCount = { 4 })

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (isReplay) "Tutorial (Replay)" else "Welcome") },
                            actions = {
                                TextButton(onClick = {
                                    scope.launch {
                                        OnboardingPrefs.setSeen(this@OnboardingActivity, true)
                                        goToMain(openUpload = false)
                                    }
                                }) { Text("Skip") }
                            }
                        )
                    }
                ) { padding ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) { page ->
                        when (page) {
                            0 -> OnboardingScreen1(
                                onNextClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                            )
                            1 -> OnboardingScreen2(
                                onNextClick = { scope.launch { pagerState.animateScrollToPage(2) } }
                            )
                            2 -> OnboardingScreen3(
                                onNextClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                                onTryNow = {
                                    scope.launch {
                                        OnboardingPrefs.setSeen(this@OnboardingActivity, true)
                                        goToMain(openUpload = true) // jump straight to Upload flow
                                    }
                                }
                            )
                            3 -> OnboardingScreen4(
                                onGetStarted = {
                                    scope.launch {
                                        OnboardingPrefs.setSeen(this@OnboardingActivity, true)
                                        goToMain(openUpload = false) // finish onboarding
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}