// com/mmalyil/smartdocai/ui/walkthrough/CoachMarkOverlay.kt
package com.mmalyil.smartdocai.ui.walkthrough

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity

@Composable
fun CoachMarkOverlay(
    steps: List<Spotlight>,
    visible: Boolean,
    onDismissAll: () -> Unit
) {
    if (!visible || steps.isEmpty()) return
    var index by remember(visible) { mutableStateOf(0) }
    val step = steps[index]

    // Tuning knobs (go wild)
    val scrimColor = Color(0xF0000000)            // much darker
    val cutoutRadius = 26f
    val borderWidthPx = 10f                         // thicker border
    val pulseStrokePx = 42f                        // louder pulse
    val bubblePad = 14.dp
    val bubbleShadow = 10.dp

    // Pulse animation
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        1f, 1.35f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by pulse.animateFloat(
        0.75f, 0.25f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Small scale-in for the tip card
    val tipScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "tipScale"
    )

    val density = LocalDensity.current
    val x = with(density) { step.x.toPx() }
    val y = with(density) { step.y.toPx() }
    val w = with(density) { step.width.toPx() }
    val h = with(density) { step.height.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable { if (index < steps.lastIndex) index++ else onDismissAll() }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // scrim
            drawRect(scrimColor, size = size)

            // cutout
            withTransform({ translate(x, y) }) {
                drawRoundRect(
                    color = Color.Transparent,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(cutoutRadius, cutoutRadius),
                    blendMode = BlendMode.Clear
                )
            }

            // pulse glow
            drawPulseRing(x, y, w, h, pulseScale, step.accent.copy(alpha = pulseAlpha), pulseStrokePx, cutoutRadius + 6f)

            // neon border
            drawRoundRect(
                color = step.accent,
                topLeft = Offset(x - 6, y - 6),
                size = Size(w + 12, h + 12),
                cornerRadius = CornerRadius(cutoutRadius + 10f, cutoutRadius + 10f),
                style = Stroke(width = borderWidthPx)
            )
        }

        // bubble placement (more aggressive avoidance below FAB)
        val bubbleYOffset = 18.dp
        val bubbleX = step.x
        val bubbleY = if (step.preferAbove) step.y - 70.dp else step.y + step.height + bubbleYOffset

        Surface(
            color = step.accent,
            tonalElevation = 0.dp,
            shadowElevation = bubbleShadow,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(bubbleX, bubbleY)
                .graphicsLayer {
                    scaleX = tipScale
                    scaleY = tipScale
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    clip = true
                }
        ) {
            Box(Modifier.padding(bubblePad)) {
                Text(
                    text = step.tip,
                    color = Color.White,
                    fontSize = 20.sp,                // bigger text
                    fontWeight = FontWeight.SemiBold // bolder
                )
            }
        }

        // arrow
        Canvas(
            Modifier
                .align(Alignment.TopStart)
                .offset(step.x + 20.dp, if (bubbleY >= step.y) step.y + step.height else bubbleY + 46.dp)
                .size(26.dp)
        ) {
            val p = Path()
            if (bubbleY >= step.y) { // bubble below -> arrow up
                p.moveTo(0f, size.height)
                p.lineTo(size.width, size.height)
                p.lineTo(size.width / 2f, 0f)
            } else { // bubble above -> arrow down
                p.moveTo(0f, 0f)
                p.lineTo(size.width, 0f)
                p.lineTo(size.width / 2f, size.height)
            }
            p.close()
            drawPath(p, step.accent)
        }
    }
}

private fun DrawScope.drawPulseRing(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    scale: Float,
    color: Color,
    stroke: Float,
    radius: Float
) {
    val growW = (w * (scale - 1f)) / 2f
    val growH = (h * (scale - 1f)) / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(x - growW, y - growH),
        size = Size(w * scale, h * scale),
        cornerRadius = CornerRadius(radius + 10f, radius + 10f),
        style = Stroke(width = stroke)
    )
}