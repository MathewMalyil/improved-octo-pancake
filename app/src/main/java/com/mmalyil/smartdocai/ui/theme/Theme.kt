// SmartDocAITheme.kt
package com.mmalyil.smartdocai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.mmalyil.smartdocai.R

@Composable
fun SmartDocAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Pull all colors from XML resources (these auto-switch for night via resource qualifiers)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(colorResource(R.color.colorPrimary).value),
            onPrimary = Color(colorResource(R.color.colorOnPrimary).value),
            secondary = Color(colorResource(R.color.colorSecondary).value),
            onSecondary = Color(colorResource(R.color.colorOnSecondary).value),
            background = Color(colorResource(R.color.colorBackground).value),
            onBackground = Color(colorResource(R.color.colorOnBackground).value),
            surface = Color(colorResource(R.color.colorSurface).value),
            onSurface = Color(colorResource(R.color.colorOnSurface).value),
            error = Color(colorResource(R.color.colorError).value),
            onError = Color(colorResource(R.color.colorOnError).value),
        )
    } else {
        lightColorScheme(
            primary = Color(colorResource(R.color.colorPrimary).value),
            onPrimary = Color(colorResource(R.color.colorOnPrimary).value),
            secondary = Color(colorResource(R.color.colorSecondary).value),
            onSecondary = Color(colorResource(R.color.colorOnSecondary).value),
            background = Color(colorResource(R.color.colorBackground).value),
            onBackground = Color(colorResource(R.color.colorOnBackground).value),
            surface = Color(colorResource(R.color.colorSurface).value),
            onSurface = Color(colorResource(R.color.colorOnSurface).value),
            error = Color(colorResource(R.color.colorError).value),
            onError = Color(colorResource(R.color.colorOnError).value),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}