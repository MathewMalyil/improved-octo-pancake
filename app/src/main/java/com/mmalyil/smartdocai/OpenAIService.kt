package com.mmalyil.smartdocai
import retrofit2.http.*
import retrofit2.http.Body

import retrofit2.http.Header
import retrofit2.http.POST


interface OpenAIService {
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): ChatResponse
}





// Note: The OpenAIService interface defines the API endpoint for creating chat completions.