package com.mmalyil.smartdocai.model











data class ChatModel(
    val name: String,
    val source: String
)
data class ChatModelList(
    val models: List<ChatModel>
)
