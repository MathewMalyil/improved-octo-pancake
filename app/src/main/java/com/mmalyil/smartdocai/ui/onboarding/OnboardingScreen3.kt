package com.mmalyil.smartdocai.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen3(
    onNextClick: () -> Unit,
    onTryNow: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Upgrade Icon",
                modifier = Modifier.size(100.dp),
                // Use theme color so it adapts to light/dark. If you want “gold”, keep the hex.
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Power Up with Pro",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text("🚀 Access GPT-4 for deep analysis",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text("📊 Higher token limits",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text("⚡ Faster responses",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text("🛡️ Priority AI access",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onTryNow != null) {
                    OutlinedButton(
                        onClick = onTryNow,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Try Upload")
                    }
                }
                Button(
                    onClick = onNextClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Next", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}