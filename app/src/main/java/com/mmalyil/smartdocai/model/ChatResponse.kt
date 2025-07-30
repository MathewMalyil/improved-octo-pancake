package com.mmalyil.smartdocai.model

data class ChatResponse(
    val choices: List<Choice>,
    val model: String,
    val modelUsed: String? = null  // optional: "gpt-4o", "llama3", etc.
)

data class Choice(
    val message: ChatMessage
)