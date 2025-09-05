package com.mmalyil.smartdocai.util

import android.content.Context
import android.util.Log
import com.mmalyil.smartdocai.api.ChatApiHelper
import com.mmalyil.smartdocai.model.ChatMessage
import com.mmalyil.smartdocai.model.ChatRequest
import com.mmalyil.smartdocai.model.ChatUnifiedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

data class ChatReply(
    val content: String,
    val modelUsed: String
)

suspend fun analyzeWithAI(
    context: Context,
    prompt: String,
    history: List<ChatMessage> = emptyList(),
    onResult: (ChatReply) -> Unit = {}
): ChatReply {
    // ---- Usage / caps ----
    val isPro     = UsageManager.isPro(context)
    val gpt4Used  = UsageManager.getTokensUsed(context)
    val gpt4Cap   = UsageManager.getTokenCap(context)
    val groqUsed  = UsageManager.getGroqUsed(context) ?: UsageManager.getTokensUsed(context)
    val groqCap   = UsageManager.getGroqCap(context) ?: 30_000
    val gpt35Used = UsageManager.getGpt35Used(context)
    val gpt35Cap  = UsageManager.getGpt35Cap()

    // ---- Choose model ----
    val selectedModel = when {
        isPro && gpt4Used < gpt4Cap -> "gpt-4o"
        groqUsed < groqCap          -> "llama-3.1-8b-instant"
        gpt35Used < gpt35Cap        -> "gpt-3.5-turbo"
        else                        -> null
    }

    Log.i("AI_DIAG",
        "selectedModel=$selectedModel isPro=$isPro gpt4=$gpt4Used/$gpt4Cap groq=$groqUsed/$groqCap gpt35=$gpt35Used/$gpt35Cap"
    )
    Log.i("AI_DIAG", "baseUrl=${ChatApiHelper.baseUrl}")

    if (selectedModel == null) {
        val out = ChatReply("Upgrade required (quota reached).", "none")
        onResult(out); return out
    }

    val finalMessages = history + ChatMessage(role = "user", content = prompt)
    val request = ChatRequest(model = selectedModel, messages = finalMessages, temperature = 0.7)

    return try {
        val reply: ChatUnifiedResponse = withContext(Dispatchers.IO) {
            ChatApiHelper.chatService.getChatReply(request)
        }

        // Start with unified field
        var responseText = reply.content

        // If blank, try to salvage from raw OpenAI/Groq shape
        if (responseText.isBlank() && reply.raw.isNotBlank()) {
            val fromRaw = extractContentFromRaw(reply.raw)
            if (fromRaw.isNotBlank()) responseText = fromRaw
        }

        if (responseText.isBlank()) responseText = "[No content returned from AI]"
        val modelUsed = reply.modelUsed.ifBlank { selectedModel }

        Log.w("AI_RAW", "modelUsed=$modelUsed preview='${responseText.take(160)}' rawLen=${reply.raw.length}")

        // usage tracking
        // NEW (always works):
        UsageManager.getPrefs(context).edit().putString("lastModelUsed", modelUsed).apply()
        val estimatedTokens = estimateTokens(prompt, responseText) // already Int
        when {
            modelUsed.startsWith("gpt-3.5") -> UsageManager.incrementGpt35Usage(context, estimatedTokens)
            modelUsed.startsWith("gpt-4")   -> UsageManager.recordUsage(context, estimatedTokens)
            else                             -> UsageManager.recordGroqUsage(context, estimatedTokens)
        }

        val out = ChatReply(responseText, modelUsed)
        onResult(out)
        out
    } catch (e: Exception) {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()?.take(1000)
            Log.e("AI_ERROR", "HTTP ${e.code()} body: $body")
        }
        Log.e("AI_ERROR", "Model: $selectedModel, Error: ${e.localizedMessage}", e)
        val out = ChatReply("Error: ${e.localizedMessage ?: "Unknown error"}", selectedModel)
        onResult(out)
        out
    }
}

fun estimateTokens(prompt: String, response: String): Int {
    val totalChars = prompt.length + response.length
    return (totalChars / 4.0).toInt().coerceAtLeast(1)
}

/** Parse common OpenAI/Groq shapes from the raw JSON string if top-level content is empty. */
private fun extractContentFromRaw(raw: String): String {
    return try {
        val root = org.json.JSONObject(raw)

        // OpenAI: choices[0].message.content
        if (root.has("choices")) {
            val choices = root.getJSONArray("choices")
            if (choices.length() > 0) {
                val first = choices.getJSONObject(0)
                if (first.has("message")) {
                    val msg = first.getJSONObject("message")
                    val c = msg.optString("content", "")
                    if (c.isNotBlank()) return c
                }
                // Some providers: choices[0].text
                val text = first.optString("text", "")
                if (text.isNotBlank()) return text
            }
        }

        // Some wrappers: root.message.content
        if (root.has("message")) {
            val msg = root.getJSONObject("message")
            val mc = msg.optString("content", "")
            if (mc.isNotBlank()) return mc
        }

        // Plain root fields
        root.optString("content", "").ifBlank { root.optString("text", "") }
    } catch (_: Exception) {
        ""
    }
}