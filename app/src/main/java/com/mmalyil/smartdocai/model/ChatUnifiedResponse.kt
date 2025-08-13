package com.mmalyil.smartdocai.model


// com.mmalyil.smartdocai.model.ChatUnifiedResponse
import com.google.gson.annotations.SerializedName

data class ChatUnifiedResponse(
    @SerializedName("content")   val content: String?,
    @SerializedName("modelUsed") val modelUsed: String?,
    @SerializedName("raw")       val raw: String?
)