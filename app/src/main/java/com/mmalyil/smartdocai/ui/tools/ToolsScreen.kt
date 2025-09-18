// com/mmalyil/smartdocai/ui/tools/ToolsScreen.kt
package com.mmalyil.smartdocai.ui.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mmalyil.smartdocai.prefs.CoachPrefs
import com.mmalyil.smartdocai.ui.walkthrough.CoachMarkOverlay
import com.mmalyil.smartdocai.ui.walkthrough.Spotlight
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@Composable
fun ToolsScreen(
    showGuidedWalkthrough: Boolean,
    onWalkthroughFinished: () -> Unit
) {
    var uploadSpot by remember { mutableStateOf<Spotlight?>(null) }
    var analyzeSpot by remember { mutableStateOf<Spotlight?>(null) }
    var exportSpot by remember { mutableStateOf<Spotlight?>(null) }
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { /* open picker */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInRoot()
                    uploadSpot = r.toSpotlight(
                        density,
                        tip = "Tap here to upload PDFs, DOCX, PPTX, XLSX or images.",
                        accent = Color(0xFF00BCD4) // cyan
                    )
                }
        ) { Text("Upload") }

        Button(
            onClick = { /* analyze */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInRoot()
                    analyzeSpot = r.toSpotlight(
                        density,
                        tip = "Then analyze with AI to summarize or extract.",
                        accent = Color(0xFF7C4DFF),   // deep purple → LOUD
                        preferAbove = false
                    )
                }
        ) { Text("Analyze with AI") }

        Button(
            onClick = { /* export/share */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInRoot()
                    exportSpot = r.toSpotlight(
                        density,
                        tip = "Export or share your results anytime.",
                        accent = Color(0xFFFF7043),   // orange → LOUD
                        preferAbove = true            // try to keep above so FAB doesn’t cover
                    )
                }
        ) { Text("Export / Share") }
    }

    val steps = remember(uploadSpot, analyzeSpot, exportSpot) {
        listOfNotNull(uploadSpot, analyzeSpot, exportSpot)
    }

    CoachMarkOverlay(
        steps = steps,
        visible = showGuidedWalkthrough && steps.isNotEmpty(),
        onDismissAll = onWalkthroughFinished
    )
}

/** Top-level wrapper that decides when to show the guided walkthrough once. */
@Composable
fun ToolsRoot() {
    val context = LocalContext.current
    var showGuide by remember { mutableStateOf(false) }
    val hasSeenCoach by CoachPrefs.hasSeenCoach(context).collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    LaunchedEffect(hasSeenCoach) {
        if (!hasSeenCoach) showGuide = true
    }

    ToolsScreen(
        showGuidedWalkthrough = showGuide,
        onWalkthroughFinished = {
            showGuide = false
            scope.launch { CoachPrefs.setCoachSeen(context, true) }
        }
    )
}

/** Rect (px) → Spotlight (dp) */
private fun Rect.toSpotlight(
    density: Density,
    tip: String,
    accent: Color,
    preferAbove: Boolean = false
): Spotlight = with(density) {
    Spotlight(
        x = left.toDp(),
        y = top.toDp(),
        width = (right - left).toDp(),
        height = (bottom - top).toDp(),
        tip = tip,
        accent = accent,
        preferAbove = preferAbove
    )
}