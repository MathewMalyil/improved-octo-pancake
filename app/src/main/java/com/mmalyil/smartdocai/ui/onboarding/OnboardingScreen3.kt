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
    onTryNow: (() -> Unit)? = null // ← NEW (optional)
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Upgrade Icon",
                modifier = Modifier.size(100.dp),
                tint = Color(0xFFFFC107)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Power Up with Pro",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text("🚀 Access GPT-4 for deep analysis", style = MaterialTheme.typography.bodyLarge)
                Text("📊 More token limits", style = MaterialTheme.typography.bodyLarge)
                Text("⚡ Faster response times", style = MaterialTheme.typography.bodyLarge)
                Text("🛡️ Priority AI access", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onTryNow != null) {
                    OutlinedButton(
                        onClick = { onTryNow() }, // safe call
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}