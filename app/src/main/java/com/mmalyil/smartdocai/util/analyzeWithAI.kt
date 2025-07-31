package com.mmalyil.smartdocai.util

import android.content.Context
import android.widget.Toast
import com.mmalyil.smartdocai.api.ChatApiHelper
import com.mmalyil.smartdocai.model.ChatMessage
import com.mmalyil.smartdocai.model.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChatReply(
    val content: String,
    val modelUsed: String
)

suspend fun analyzeWithAI(
    context: Context,
    prompt: String,
    history: List<ChatMessage> = emptyList(),
    onResult: (ChatReply) -> Unit,
    triedFallback: Boolean = false
) {
    val isPro = UsageManager.isPro(context)
    val used = UsageManager.getTokensUsed(context)
    val cap = UsageManager.getTokenCap(context)

    val selectedModel = if (isPro && used < cap && !triedFallback) "gpt-4o" else "llama3-8b-8192"

    val finalMessages = history + ChatMessage("user", prompt)

    val request = ChatRequest(
        model = selectedModel,
        messages = finalMessages,
        temperature = 0.7
    )

    try {
        val reply = withContext(Dispatchers.IO) {
            ChatApiHelper.chatService.getChatReply(request)
        }

        val responseText = reply.choices.firstOrNull()?.message?.content ?: "No reply"
        val model = reply.modelUsed ?: selectedModel

        // Approximate token usage
        val estimatedTokens = estimateTokens(prompt, responseText)
        if (model.startsWith("gpt")) {
            UsageManager.recordUsage(context, estimatedTokens)
        }

        onResult(ChatReply(responseText, model))

    } catch (e: Exception) {
        if (!triedFallback) {
            analyzeWithAI(context, prompt, history, onResult, triedFallback = true)
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "AI error: ${e.message}", Toast.LENGTH_LONG).show()
                onResult(ChatReply("AI failed. Try again later.", "error"))
            }
        }
    }
}

fun estimateTokens(prompt: String, response: String): Int {
    val totalChars = prompt.length + response.length
    return (totalChars / 4.0).toInt().coerceAtLeast(1)
}