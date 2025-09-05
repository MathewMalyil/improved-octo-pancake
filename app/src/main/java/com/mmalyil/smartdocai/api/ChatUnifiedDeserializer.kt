package com.mmalyil.smartdocai.api

import com.google.gson.*
import com.mmalyil.smartdocai.model.ChatUnifiedResponse
import java.lang.reflect.Type

class ChatUnifiedDeserializer : JsonDeserializer<ChatUnifiedResponse> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        ctx: JsonDeserializationContext
    ): ChatUnifiedResponse {
        val root = json.asJsonObject
        val raw = root.toString()

        fun modelFrom(o: JsonObject): String {
            return o.get("modelUsed")?.asString
                ?: o.get("model")?.asString
                ?: ""
        }

        // 1) OpenAI/Groq: choices[0].message.content
        root.getAsJsonArray("choices")?.takeIf { it.size() > 0 }?.let { arr ->
            val first = arr[0].asJsonObject
            val msg = first.getAsJsonObject("message")
            val content = msg?.get("content")?.asString
            if (!content.isNullOrBlank()) {
                return ChatUnifiedResponse(content, modelFrom(root), raw)
            }
            // 2) choices[0].text
            val choiceText = first.get("text")?.asString
            if (!choiceText.isNullOrBlank()) {
                return ChatUnifiedResponse(choiceText, modelFrom(root), raw)
            }
        }

        // 3) Direct content
        root.get("content")?.asString?.let { direct ->
            if (direct.isNotBlank()) {
                return ChatUnifiedResponse(direct, modelFrom(root), raw)
            }
        }

        // 4) message.content at root
        root.getAsJsonObject("message")?.get("content")?.asString?.let { mc ->
            if (mc.isNotBlank()) {
                return ChatUnifiedResponse(mc, modelFrom(root), raw)
            }
        }

        // 5) Root text
        root.get("text")?.asString?.let { t ->
            if (t.isNotBlank()) {
                return ChatUnifiedResponse(t, modelFrom(root), raw)
            }
        }

        // 6) Error message (provider error surfaces to user)
        root.getAsJsonObject("error")?.let { errObj ->
            val msg = errObj.get("message")?.asString ?: errObj.toString()
            if (msg.isNotBlank()) {
                return ChatUnifiedResponse(msg, modelFrom(root), raw)
            }
        }

        // Fallback: nothing usable
        return ChatUnifiedResponse("", modelFrom(root), raw)
    }
}