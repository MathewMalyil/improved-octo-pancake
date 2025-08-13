// app/src/main/java/com/mmalyil/smartdocai/api/ChatApiHelper.kt
package com.mmalyil.smartdocai.api

import com.google.gson.*
import com.mmalyil.smartdocai.model.ChatUnifiedResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type

// api/ChatApiHelper.kt (inside ChatUnifiedDeserializer)
// ChatUnifiedDeserializer.kt (same file you already have)
// api/ChatApiHelper.kt (keep your existing imports)
class ChatUnifiedDeserializer : JsonDeserializer<ChatUnifiedResponse> {
    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): ChatUnifiedResponse {
        val root = json.asJsonObject
        val raw = root.toString()

        // OpenAI-like: choices[0].message.content
        root.getAsJsonArray("choices")?.takeIf { it.size() > 0 }?.let { arr ->
            val first = arr[0].asJsonObject
            val msg = first.getAsJsonObject("message")
            val content = msg?.get("content")?.asString ?: ""
            val modelUsed = root.get("modelUsed")?.asString
                ?: root.get("model")?.asString
                ?: ""
            return ChatUnifiedResponse(content = content, modelUsed = modelUsed, raw = raw)
        }

        // Direct content
        root.get("content")?.asString?.let { direct ->
            val modelUsed = root.get("modelUsed")?.asString
                ?: root.get("model")?.asString
                ?: ""
            return ChatUnifiedResponse(content = direct, modelUsed = modelUsed, raw = raw)
        }

        // message.content at root
        root.getAsJsonObject("message")?.get("content")?.asString?.let { mc ->
            val modelUsed = root.get("modelUsed")?.asString
                ?: root.get("model")?.asString
                ?: ""
            return ChatUnifiedResponse(content = mc, modelUsed = modelUsed, raw = raw)
        }

        // Fallback
        val modelUsed = root.get("modelUsed")?.asString
            ?: root.get("model")?.asString
            ?: ""
        return ChatUnifiedResponse(content = "", modelUsed = modelUsed, raw = raw)
    }
}