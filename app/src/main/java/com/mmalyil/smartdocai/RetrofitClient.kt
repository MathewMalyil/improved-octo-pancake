package com.mmalyil.smartdocai

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object RetrofitClient {

    private const val MISTRAL_BASE_URL = "http://192.168.1.10:8000/v1/"
    private const val OPENAI_BASE_URL = "https://api.openai.com/v1/"
    private const val GROQ_BASE_URL = "https://api.groq.com/openai/v1/"

    fun getService(source: String): OpenAIService {
        val baseUrl = when (source) {
            "groq" -> GROQ_BASE_URL
            "mistral" -> MISTRAL_BASE_URL
            else -> OPENAI_BASE_URL // default to OpenAI
        }

        val interceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAIService::class.java)
    }

    fun apiKey (source: String): String {
        return when (source) {
            "groq" -> BuildConfig.GROQ_API_KEY
            "openai" -> BuildConfig.OPENAI_API_KEY
            "mistral" -> "" // local Mistral, no key
            else -> "DUMMY_KEY"
        }
    }
}