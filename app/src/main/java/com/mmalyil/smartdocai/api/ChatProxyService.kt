
package com.mmalyil.smartdocai.api

import android.R
import com.mmalyil.smartdocai.model.ChatRequest
import com.mmalyil.smartdocai.model.ChatUnifiedResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface ChatProxyService {


        @POST("api/chat")                 // ✅ must be api/chat (no leading slash)
        suspend fun getChatReply(@Body request: ChatRequest): ChatUnifiedResponse
    }