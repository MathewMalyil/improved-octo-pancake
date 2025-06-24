package com.mmalyil.smartdocai



data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)