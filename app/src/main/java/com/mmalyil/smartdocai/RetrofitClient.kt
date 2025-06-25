package com.mmalyil.smartdocai
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory





object RetrofitClient {

    // ✅ Replace this with your Mac's actual IP address
    private const val MISTRAL_BASE_URL = "http://192.168.1.5:8000/" // Local Mistral server
    private const val OPENAI_BASE_URL = "https://api.openai.com/v1/"

    private val interceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .build()

    // 🔁 Choose Mistral or OpenAI dynamically
    fun getService(useMistral: Boolean): OpenAIService {
        val baseUrl = if (useMistral) MISTRAL_BASE_URL else OPENAI_BASE_URL

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAIService::class.java)
    }
}