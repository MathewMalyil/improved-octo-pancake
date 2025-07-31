package com.mmalyil.smartdocai.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ChatApiHelper {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://smartdoc-ai-backend.vercel.app/") // ✅ Updated
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val chatService: ChatProxyService by lazy {
        retrofit.create(ChatProxyService::class.java)
    }
}