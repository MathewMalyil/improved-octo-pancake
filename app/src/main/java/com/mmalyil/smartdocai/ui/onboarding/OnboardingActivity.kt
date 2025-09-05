@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.mmalyil.smartdocai.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mmalyil.smartdocai.MainActivity
import com.mmalyil.smartdocai.prefs.OnboardingPrefs
import com.mmalyil.smartdocai.ui.theme.SmartDocAITheme
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {

    /** Local helper (has access to Activity context) */
    private fun goToMain(openUpload: Boolean = false) {
        val i = Intent(this@OnboardingActivity, MainActivity::class.java).apply {
            putExtra("fromOnboarding", true)
            putExtra("openUpload", openUpload)
            // So Back won’t go back to onboarding
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(i)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isReplay = intent?.getBooleanExtra("replay", false) ?: false

        setContent {
            SmartDocAITheme {
                val scope = rememberCoroutineScope()
                val pagerState = rememberPagerState(pageCount = { 4 })
                var isLastPage by remember { mutableStateOf(false) }

                LaunchedEffect(pagerState.currentPage) {
                    isLastPage = pagerState.currentPage == pagerState.pageCount - 1
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (isReplay) "Tutorial (Replay)" else "Welcome") },
                            actions = {
                                if (!isLastPage) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            OnboardingPrefs.setSeen(this@OnboardingActivity, true)
                                            goToMain(openUpload = false)   // ← don’t finish(), navigate
                                        }
                                    }) { Text("Skip") }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        BottomAppBar {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Dots(
                                    current = pagerState.currentPage,
                                    total = pagerState.pageCount
                                )

                                Button(onClick = {
                                    scope.launch {
                                        if (isLastPage) {
                                            OnboardingPrefs.setSeen(this@OnboardingActivity, true)
                                            goToMain(openUpload = false)   // ← go to main on Done
                                        } else {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                }) { Text(if (isLastPage) "Done" else "Next") }
                            }
                        }
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
                                        goToMain(openUpload = true)       // ← jump into Upload
                                    }
                                }
                            )
                            3 -> OnboardingScreen4(onGetStarted = {
                                scope.launch {
                                    OnboardingPrefs.setSeen(this@OnboardingActivity, true)
                                    goToMain(openUpload = false)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { idx ->
            val filled = idx == current
            val alpha = if (filled) 1f else 0.35f
            Surface(
                modifier = Modifier.size(if (filled) 10.dp else 8.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            ) {}
        }
    }
}