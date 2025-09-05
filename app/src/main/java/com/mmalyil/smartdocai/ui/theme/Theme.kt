package com.mmalyil.smartdocai.ui.theme



import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun SmartDocAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(), // Or custom colors
        typography = Typography(),
        content = content
    )

}
