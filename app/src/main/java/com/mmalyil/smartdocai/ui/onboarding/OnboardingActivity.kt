@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.mmalyil.smartdocai.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mmalyil.smartdocai.MainActivity
import com.mmalyil.smartdocai.ScanActivity
import com.mmalyil.smartdocai.prefs.OnboardingPrefs
import kotlinx.coroutines.launch

/**
 * First-run + Settings “Replay walkthrough” intro.
 * Short slides for current product: Scan / ID / Combine / Compress /
 * Image→Excel / free AI / Document Pro (not ai_pro_plan).
 */
class OnboardingActivity : ComponentActivity() {

    private fun finishToMain(openScan: Boolean) {
        val replay = intent.getBooleanExtra("replay", false)
        val i = Intent(this, MainActivity::class.java).apply {
            putExtra("fromOnboarding", true)
            putExtra("openScan", openScan)
            if (replay) {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } else {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        startActivity(i)
        if (openScan) {
            startActivity(
                Intent(this, ScanActivity::class.java).apply {
                    putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_CAMERA)
                }
            )
        }
        finish()
    }

    private fun markSeenThen(openScan: Boolean) {
        lifecycleScope.launch {
            OnboardingPrefs.setSeen(this@OnboardingActivity, true)
            finishToMain(openScan)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        val isReplay = intent?.getBooleanExtra("replay", false) ?: false
        val slides = OnboardingSlides

        setContent {
            val scope = rememberCoroutineScope()
            val pagerState = rememberPagerState(pageCount = { slides.size })

            OnboardingAtmosphere {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val slide = slides[page]
                    val last = page == slides.lastIndex
                    OnboardingSlidePage(
                        slide = slide,
                        pageIndex = page,
                        pageCount = slides.size,
                        isReplay = isReplay,
                        onPrimary = {
                            if (!last) {
                                scope.launch { pagerState.animateScrollToPage(page + 1) }
                            } else {
                                markSeenThen(openScan = true)
                            }
                        },
                        onSecondary = if (last) {
                            { markSeenThen(openScan = false) }
                        } else null,
                        onSkip = { markSeenThen(openScan = false) }
                    )
                }
            }
        }
    }
}
