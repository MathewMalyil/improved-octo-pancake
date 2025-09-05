package com.mmalyil.smartdocai.ui.walkthrough
// file: com/mmalyil/smartdocai/ui/walkthrough/CoachMarkOverlay.kt


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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spotlight(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val tip: String
)

@Composable
fun CoachMarkOverlay(
    steps: List<Spotlight>,
    visible: Boolean,
    onDismissAll: () -> Unit
) {
    var index by remember(visible) { mutableStateOf(0) }
    if (!visible || steps.isEmpty()) return

    val step = steps[index]
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable {
                if (index < steps.lastIndex) index++ else onDismissAll()
            }
    ) {
        // Hole cutout
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val path = Path()
                    val rx = with(density) { step.x.toPx() }
                    val ry = with(density) { step.y.toPx() }
                    val rw = with(density) { step.width.toPx() }
                    val rh = with(density) { step.height.toPx() }

                    // Dark scrim
                    drawRect(Color(0xCC000000))

                    // Clear a rounded rectangle "spotlight"
                    withTransform({
                        translate(rx, ry)
                    }) {
                        drawRoundRect(
                            color = Color.Transparent,
                            size = Size(rw, rh),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f),
                            blendMode = BlendMode.Clear
                        )
                    }
                }
        )

        // Tooltip
        Surface(
            color = Color(0xFF222222),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(step.x, step.y + step.height + 12.dp)
                .padding(8.dp)
        ) {
            Text(
                step.tip,
                color = Color.White,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}