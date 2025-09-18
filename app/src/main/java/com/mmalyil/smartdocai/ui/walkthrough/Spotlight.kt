package com.mmalyil.smartdocai.ui.walkthrough

// com/mmalyil/smartdocai/ui/walkthrough/Spotlight.kt


import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

data class Spotlight(
    val x: Dp,
    val y: Dp,
    val width: Dp,
    val height: Dp,
    val tip: String,
    val accent: Color = Color(0xFF00BCD4), // default cyan
    val preferAbove: Boolean = false       // try placing tip above the hole if there's room
)