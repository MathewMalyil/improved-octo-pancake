package com.mmalyil.smartdocai.model



data class ChatMessage(
    val role: String,  // "user" or "assistant"
    val content: String
)