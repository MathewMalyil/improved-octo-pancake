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
                        "Tap here to upload PDFs, DOCX, PPTX, XLSX or images."
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
                        "Then analyze with AI to summarize or extract."
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
                        "Export or share your results anytime."
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

/** Rect (px) → Spotlight (dp) using Compose Density */
private fun Rect.toSpotlight(
    density: Density,
    tip: String
): Spotlight = with(density) {
    Spotlight(
        x = left.toDp(),
        y = top.toDp(),
        width = (right - left).toDp(),
        height = (bottom - top).toDp(),
        tip = tip
    )
}