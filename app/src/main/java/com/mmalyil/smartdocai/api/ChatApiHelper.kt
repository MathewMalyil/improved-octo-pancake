package com.mmalyil.smartdocai.api



import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ChatApiHelper {
    private const val BASE_URL = "https://ai-proxy-bncv.vercel.app/" // your proxy

    val chatService: ChatProxyService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatProxyService::class.java)
    }
}