package com.mmalyil.smartdocai.api



import com.mmalyil.smartdocai.model.ChatRequest
import com.mmalyil.smartdocai.model.ChatResponse

import retrofit2.http.Body
import retrofit2.http.POST

interface ChatProxyService {
    @POST("chat")
    suspend fun getChatReply(@Body request: ChatRequest): ChatResponse
}