package com.mmalyil.smartdocai





data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ChatMessage

)

data class ChatMessage(
    val role: String,    // "user" or "assistant"
    val content: String
)


data class ChatModel(
    val name: String,
    val source: String
)
data class ChatModelList(
    val models: List<ChatModel>
)
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7
)