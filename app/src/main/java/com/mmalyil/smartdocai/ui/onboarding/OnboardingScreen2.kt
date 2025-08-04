package com.mmalyil.smartdocai.ui.onboarding


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen2(onNextClick: () -> Unit) {
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
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = "AI Features",
                modifier = Modifier.size(100.dp),
                tint = Color(0xFF4CAF50) // Green
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Everything You Need, In One Place",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text("📄 Scan PDFs, DOCX, PPTX ,XLSX & images", style = MaterialTheme.typography.bodyLarge)
                Text("🤖 AI-powered text & document analysis", style = MaterialTheme.typography.bodyLarge)
                Text("🧠 Save files for later", style = MaterialTheme.typography.bodyLarge)
                Text("☁️ Send uploaded document & AI analyzed document separate or together ", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNextClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Next", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}