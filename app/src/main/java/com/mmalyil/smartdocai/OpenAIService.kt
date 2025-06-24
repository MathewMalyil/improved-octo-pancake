package com.mmalyil.smartdocai
import retrofit2.http.*
import retrofit2.http.Body

import retrofit2.http.Header
import retrofit2.http.POST



interface OpenAIService {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse







}