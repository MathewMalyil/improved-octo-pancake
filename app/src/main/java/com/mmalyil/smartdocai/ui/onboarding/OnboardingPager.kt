package com.mmalyil.smartdocai.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset

/** Ink-teal studio look — avoids purple gradients and cream/terracotta defaults. */
internal object OnboardingPalette {
    val Ink = Color(0xFF071820)
    val DeepTeal = Color(0xFF0E3A42)
    val Sea = Color(0xFF1F6F78)
    val Accent = Color(0xFF3ECFBF)
    val Paper = Color(0xFFF2F7F6)
    val Mist = Color(0xFFB7CDC9)
    val ButtonInk = Color(0xFF042028)
}

data class OnboardingSlide(
    /** When true, brand name is the hero — headline stays secondary. */
    val brandHero: Boolean = false,
    val title: String,
    val body: String,
    val bullets: List<String> = emptyList(),
    val primaryLabel: String,
    val secondaryLabel: String? = null
)

val OnboardingSlides: List<OnboardingSlide> = listOf(
    OnboardingSlide(
        brandHero = true,
        title = "Scan. Convert. Ask.",
        body = "One place for paper jobs — from camera capture to Excel, Word, and free AI answers.",
        primaryLabel = "Continue"
    ),
    OnboardingSlide(
        title = "Scan that actually fits",
        body = "Edge detect and auto-capture lock the page; Adjust corners before you save. Scan ID captures Front + Back for Aadhaar-style cards.",
        bullets = listOf(
            "Camera scan with edge / auto-capture",
            "Adjust corners on every page",
            "Scan ID → Front + Back PDF"
        ),
        primaryLabel = "Continue"
    ),
    OnboardingSlide(
        title = "Document jobs, ready",
        body = "Combine PDFs, compress on Save, turn table photos into Excel, and pick Excel for an AI Word report — from Home or Analyse.",
        bullets = listOf(
            "Combine PDFs into one file",
            "Compress on Save (Smaller = Document Pro)",
            "Image → Excel · Excel → Word"
        ),
        primaryLabel = "Continue"
    ),
    OnboardingSlide(
        title = "Free AI on Analyse",
        body = "Summarize, key points, and chat use free Groq tokens. Exports and reports land in Files with a Saved to Files toast.",
        bullets = listOf(
            "Analyse & chat — free Groq monthly cap",
            "Exports auto-save into Files",
            "Soft daily tool caps while you try Pro"
        ),
        primaryLabel = "Continue"
    ),
    OnboardingSlide(
        title = "Document Pro when you need more",
        body = "Unlimited tools, ad-free, and clean PDF share/export — not the old AI Pro plan. Free AI stays free either way.",
        bullets = listOf(
            "Yearly · Monthly · Lifetime",
            "Clean share without free watermark",
            "Replay this intro anytime in Settings"
        ),
        primaryLabel = "Start scanning",
        secondaryLabel = "Go to Home"
    )
)

@Composable
fun OnboardingAtmosphere(content: @Composable () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "onbPulse")
    val shift by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        OnboardingPalette.Ink,
                        OnboardingPalette.DeepTeal.copy(alpha = 0.95f + 0.05f * shift),
                        OnboardingPalette.Sea.copy(alpha = 0.55f + 0.2f * (1f - shift))
                    )
                )
            )
    ) {
        // Soft atmospheric orb — presence, not noise (offset, never negative padding)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = 48.dp)
                .size(220.dp)
                .scale(0.92f + 0.08f * shift)
                .alpha(0.22f)
                .clip(CircleShape)
                .background(OnboardingPalette.Accent)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-40).dp, y = (-120).dp)
                .size(180.dp)
                .alpha(0.14f)
                .clip(CircleShape)
                .background(OnboardingPalette.Paper)
        )
        content()
    }
}

@Composable
fun OnboardingSlidePage(
    slide: OnboardingSlide,
    pageIndex: Int,
    pageCount: Int,
    isReplay: Boolean = false,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)? = null,
    onSkip: () -> Unit
) {
    val enter = rememberInfiniteTransition(label = "brandBreath")
    val brandScale by enter.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "brandScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isReplay) {
                Text(
                    text = "Replay",
                    color = OnboardingPalette.Accent.copy(alpha = 0.9f),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
            TextButton(onClick = onSkip) {
                Text("Skip", color = OnboardingPalette.Mist)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (slide.brandHero) {
            Text(
                text = "SmartDocAI",
                modifier = Modifier.scale(brandScale),
                color = OnboardingPalette.Paper,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = slide.title,
                color = OnboardingPalette.Accent,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "SmartDocAI",
                color = OnboardingPalette.Accent,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = slide.title,
                color = OnboardingPalette.Paper,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = slide.body,
            color = OnboardingPalette.Mist,
            fontFamily = FontFamily.SansSerif,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (slide.bullets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                slide.bullets.forEach { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(OnboardingPalette.Accent)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = line,
                            color = OnboardingPalette.Paper,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        PageDots(pageIndex = pageIndex, pageCount = pageCount)

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OnboardingPalette.Accent,
                contentColor = OnboardingPalette.ButtonInk
            )
        ) {
            Text(
                slide.primaryLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        if (slide.secondaryLabel != null && onSecondary != null) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, OnboardingPalette.Mist.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = OnboardingPalette.Paper
                )
            ) {
                Text(slide.secondaryLabel, fontSize = 15.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun PageDots(pageIndex: Int, pageCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { i ->
            val active = i == pageIndex
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (active) 22.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) OnboardingPalette.Accent
                        else OnboardingPalette.Mist.copy(alpha = 0.35f)
                    )
            )
        }
    }
}
