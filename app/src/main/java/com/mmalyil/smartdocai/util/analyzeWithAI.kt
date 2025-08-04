package com.mmalyil.smartdocai.util

import android.content.Context
import android.util.Log
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
    val gpt4Used = UsageManager.getTokensUsed(context)
    val gpt4Cap = UsageManager.getTokenCap(context)
    val groqUsed = UsageManager.getTokensUsed(context) // same as free usage
    val gpt35Used = UsageManager.getGpt35Used(context)
    val gpt35Cap = UsageManager.getGpt35Cap()

    // ✅ Select model by priority
    val selectedModel = when {
        isPro && gpt4Used < gpt4Cap -> "gpt-4o"
        groqUsed < 30_000 -> "llama3-8b-8192"
        gpt35Used < gpt35Cap -> "gpt-3.5-turbo"
        else -> null
    }

    if (selectedModel == null) {
        withContext(Dispatchers.Main) {
            onResult(
                ChatReply("Upgrade required", modelUsed = "none")
            )
        }
        return
    }

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
        val modelUsed = reply.modelUsed ?: selectedModel

        // ✅ Track last used model
        UsageManager.getPrefs(context).edit()
            .putString("lastModelUsed", modelUsed)
            .apply()

        // ✅ Estimate tokens
        val estimatedTokens = estimateTokens(prompt, responseText)

        // ✅ Token tracking
        when {
            modelUsed.startsWith("gpt-4") -> UsageManager.recordUsage(context, estimatedTokens)
            modelUsed.startsWith("gpt-3.5") -> UsageManager.incrementGpt35Usage(context, estimatedTokens)
            else -> UsageManager.recordUsage(context, estimatedTokens) // Groq fallback
        }

        // ✅ Return result
        withContext(Dispatchers.Main) {
            onResult(
                ChatReply(
                    content = responseText,

                    modelUsed = modelUsed
                )
            )
        }

    } catch (e: Exception) {
        Log.e("AI_ERROR", "Model: $selectedModel, Error: ${e.localizedMessage}")

        // Fallback: if GPT-4 failed, retry Groq or GPT-3.5 (only once)
        if (!triedFallback && selectedModel == "gpt-4o") {
            analyzeWithAI(
                context = context,
                prompt = prompt,
                history = history,
                onResult = onResult,
                triedFallback = true
            )
        } else {
            withContext(Dispatchers.Main) {
                onResult(
                    ChatReply(
                        content = "Error: ${e.localizedMessage ?: "Unknown error"}",

                        modelUsed = selectedModel
                    )
                )
            }
        }
    }
}

fun estimateTokens(prompt: String, response: String): Int {
    val totalChars = prompt.length + response.length
    return (totalChars / 4.0).toInt().coerceAtLeast(1)
}