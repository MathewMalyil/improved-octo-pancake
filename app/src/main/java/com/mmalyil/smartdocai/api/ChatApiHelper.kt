package com.mmalyil.smartdocai.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory



object ChatApiHelper {
    private val diag = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("Accept", "application/json")
            .build()
        val res = chain.proceed(req)
        android.util.Log.i("AI_DIAG", "→ ${req.method} ${req.url} ← ${res.code}")
        res
    }

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()

    private val client = OkHttpClient.Builder()
        .addInterceptor(diag)
        .build()



    private val retrofit = Retrofit.Builder()
        .baseUrl("https://smartdoc-ai-backend.vercel.app/")
        .addConverterFactory(ScalarsConverterFactory.create())   // <- add this first
        .addConverterFactory(GsonConverterFactory.create(gson))   // then gson
        .client(client)
        .build()

    val chatService: ChatProxyService by lazy {
        retrofit.create(ChatProxyService::class.java)



    }



}