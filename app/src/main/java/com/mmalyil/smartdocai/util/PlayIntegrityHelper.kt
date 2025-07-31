package com.mmalyil.smartdocai.util


import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.tasks.await

suspend fun requestIntegrityToken(context: Context): String? {
    val manager = IntegrityManagerFactory.create(context)
    val request = IntegrityTokenRequest.builder()
        //.setCloudProjectNumber((.setCloudProjectNumber(123456789012L)) // replace this below
        .build()

    return try {
        val response = manager.requestIntegrityToken(request).await()
        response.token() // 🔐 Send this to server later (optional)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}