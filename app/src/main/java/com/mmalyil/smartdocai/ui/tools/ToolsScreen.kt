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
                        tip = "Upload a PDF/Excel/image — or use Image→Excel, Excel→Word, Combine, Scan ID.",
                        accent = Color(0xFF3ECFBF)
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
                        tip = "Analyze with free Groq AI (summary, key points, chat).",
                        accent = Color(0xFF1F6F78),
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
                        tip = "Export TXT/PDF/Word — saves into Files. Document Pro = clean unlimited tools.",
                        accent = Color(0xFF3ECFBF),
                        preferAbove = true
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