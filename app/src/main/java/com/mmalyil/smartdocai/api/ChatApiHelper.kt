// ChatApiHelper.kt
package com.mmalyil.smartdocai.api

import android.net.TrafficStats            // ← add
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import okhttp3.Request
import okhttp3.Response
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

object ChatApiHelper {

    // Tag every network thread so StrictMode doesn’t warn about untagged sockets
    private val socketTagging = Interceptor { chain ->
        TrafficStats.setThreadStatsTag(0x53444F43) // 'SDOC' – any non-zero int is fine
        try {
            chain.proceed(chain.request())
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }

    private val diag = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("Accept", "application/json")
            .build()
        val res = chain.proceed(req)
        android.util.Log.i("AI_DIAG", "→ ${req.method} ${req.url} ← ${res.code}")
        res
    }

    private val timeoutRetry = Interceptor { chain ->
        fun proceedOnce(request: Request): Response = chain.proceed(request)
        val req = chain.request()
        try {
            proceedOnce(req)
        } catch (e: SocketTimeoutException) {
            android.util.Log.w("AI_WARN", "SocketTimeout → retrying once…")
            proceedOnce(req)
        }.let { res ->
            if (res.code == 408 || res.code == 504) {
                res.close()
                android.util.Log.w("AI_WARN", "HTTP ${res.code} → retrying once…")
                proceedOnce(req)
            } else res
        }
    }

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .registerTypeAdapter(
            com.mmalyil.smartdocai.model.ChatUnifiedResponse::class.java,
            ChatUnifiedDeserializer()
        )
        .create()

    private val client = OkHttpClient.Builder()
        // IMPORTANT: put the socketTagging as the FIRST network interceptor
        .addNetworkInterceptor(socketTagging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(65, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(timeoutRetry)
        .addInterceptor(diag)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://smartdoc-ai-backend.vercel.app/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(client)
        .build()

    val baseUrl: String get() = retrofit.baseUrl().toString()

    val chatService: ChatProxyService by lazy {
        retrofit.create(ChatProxyService::class.java)
    }
}