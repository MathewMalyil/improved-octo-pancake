package com.mmalyil.smartdocai.util



import android.content.Context
import android.util.Log
import com.mmalyil.smartdocai.api.ChatApiHelper
import com.mmalyil.smartdocai.model.ChatMessage
import com.mmalyil.smartdocai.model.ChatRequest
import com.mmalyil.smartdocai.util.UsageManager // adjust if your package differs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChatReply(
    val content: String,
    val modelUsed: String
)

/**
 * Model selection priority:
 * 1) GPT-4o (if Pro and under cap)
 * 2) Groq (llama3-8b-8192) under free cap
 * 3) GPT-3.5 (strict small cap)
 * Otherwise -> “Upgrade required”.
 *
 * Fallbacks: If GPT-4o call fails, retry once with Groq; if Groq fails, retry once with GPT-3.5.
 */
suspend fun analyzeWithAI(
    context: Context,
    prompt: String,
    history: List<ChatMessage> = emptyList(),
    onResult: (ChatReply) -> Unit,
    triedFallback: Boolean = false
) {
    // ---- Usage / caps ----
    val isPro = UsageManager.isPro(context)
    val gpt4Used = UsageManager.getTokensUsed(context)
    val gpt4Cap = UsageManager.getTokenCap(context)

    // NOTE: If you have a separate Groq counter (recommended), swap to it here.
    // For now this uses the existing tokens counter as in your code.
    val groqUsed = UsageManager.getGroqUsed(context) ?: UsageManager.getTokensUsed(context)
    val groqCap = UsageManager.getGroqCap(context) ?: 30_000

    val gpt35Used = UsageManager.getGpt35Used(context)
    val gpt35Cap = UsageManager.getGpt35Cap()

    // ---- Choose model ----
    val selectedModel = when {
        isPro && gpt4Used < gpt4Cap -> "gpt-4o"
        groqUsed < groqCap -> "llama3-8b-8192"
        gpt35Used < gpt35Cap -> "gpt-3.5-turbo"
        else -> null
    }

    if (selectedModel == null) {
        withContext(Dispatchers.Main) {
            onResult(ChatReply("Upgrade required", modelUsed = "none"))
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

        // Coerce to safe strings
        val responseText = reply.content?.takeIf { it.isNotBlank() } ?: "[No content returned from AI]"
        val modelUsed = reply.modelUsed?.takeIf { it.isNotBlank() } ?: selectedModel

        // Track last used model
        UsageManager.getPrefs(context).edit().putString("lastModelUsed", modelUsed).apply()

        // Estimate tokens & record usage (after success)
        val estimatedTokens = estimateTokens(prompt, responseText)
        when {
            modelUsed.startsWith("gpt-4") -> UsageManager.recordUsage(context, estimatedTokens)
            modelUsed.startsWith("gpt-3.5") -> UsageManager.incrementGpt35Usage(context, estimatedTokens)
            else /* Groq */ -> UsageManager.recordGroqUsage(context, estimatedTokens)
        }

        withContext(Dispatchers.Main) {
            onResult(ChatReply(content = responseText, modelUsed = modelUsed))
        }
    } catch (e: Exception) {
        Log.e("AI_ERROR", "Model: $selectedModel, Error: ${e.localizedMessage}")

        // One-pass graceful fallback chain
        if (!triedFallback) {
            val fallbackNext = when (selectedModel) {
                "gpt-4o" -> true  // allow selection to move to Groq/GPT-3.5
                "llama3-8b-8192" -> true // allow selection to move to GPT-3.5
                else -> false
            }
            if (fallbackNext) {
                analyzeWithAI(
                    context = context,
                    prompt = prompt,
                    history = history,
                    onResult = onResult,
                    triedFallback = true
                )
                return
            }
        }

        withContext(Dispatchers.Main) {
            onResult(ChatReply(content = "Error: ${e.localizedMessage ?: "Unknown error"}", modelUsed = selectedModel))
        }
    }
}

fun estimateTokens(prompt: String, response: String): Int {
    val totalChars = prompt.length + response.length
    return (totalChars / 4.0).toInt().coerceAtLeast(1)
}