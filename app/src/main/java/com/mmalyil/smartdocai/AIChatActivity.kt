package com.mmalyil.smartdocai



import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.mmalyil.smartdocai.model.ChatMessage
import com.mmalyil.smartdocai.model.ChatRequest
import com.mmalyil.smartdocai.util.ChatReply
import com.mmalyil.smartdocai.util.analyzeWithAI


import kotlinx.coroutines.*

class AIChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var modelTextView: TextView

    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        inputEditText = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        modelTextView = findViewById(R.id.modelUsedText) // 👈 Add this TextView in layout XML

        chatAdapter = ChatAdapter(chatMessages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatRecyclerView.adapter = chatAdapter

        sendButton.setOnClickListener {
            val userInput = inputEditText.text.toString().trim()
            if (userInput.isEmpty()) return@setOnClickListener

            addMessage("user", userInput)
            inputEditText.text.clear()

            runAI(userInput)
        }
    }

    private fun addMessage(role: String, content: String) {
        chatMessages.add(ChatMessage(role, content))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun runAI(prompt: String) {
        coroutineScope.launch {
            analyzeWithAI(
                context = this@AIChatActivity,
                prompt = prompt,
                history = chatMessages,
                onResult = { reply: ChatReply ->
                    addMessage("assistant", reply.content)
                    modelTextView.text = "Model: ${reply.modelUsed}"

                    // Optional: show usage
                    // val used = UsageManager.getTokensUsed(this@AIChatActivity)
                    // val cap = UsageManager.getTokenCap(this@AIChatActivity)
                    // Toast.makeText(this@AIChatActivity, "$used / $cap tokens used", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }
}