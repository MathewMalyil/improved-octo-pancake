package com.mmalyil.smartdocai.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit


object ChatApiHelper {
    private val diag = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("Accept", "application/json")
            .build()
        val res = chain.proceed(req)
        android.util.Log.i("AI_DIAG", "→ ${req.method} ${req.url} ← ${res.code}")
        res
    }

    // One-shot retry for timeouts (safe)
    private val timeoutRetry = Interceptor { chain ->
        fun proceedOnce(request: Request): Response = chain.proceed(request)

        val req = chain.request()
        return@Interceptor try {
            proceedOnce(req)
        } catch (e: SocketTimeoutException) {
            android.util.Log.w("AI_WARN", "SocketTimeout → retrying once…")
            proceedOnce(req) // retry once
        }.let { res ->
            // Retry once on classic timeout HTTP codes
            if (res.code == 408 || res.code == 504) {
                res.close()
                android.util.Log.w("AI_WARN", "HTTP ${res.code} → retrying once…")
                proceedOnce(req)
            } else {
                res
            }
        }
    }




    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)   // connect faster
        .writeTimeout(60, TimeUnit.SECONDS)     // send body
        .readTimeout(60, TimeUnit.SECONDS)      // wait for body
        .callTimeout(65, TimeUnit.SECONDS)   // total cap
        .pingInterval(15, TimeUnit.SECONDS)  //keep HTTP/2 connections alive
        .retryOnConnectionFailure(true)
        .addInterceptor(timeoutRetry)
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