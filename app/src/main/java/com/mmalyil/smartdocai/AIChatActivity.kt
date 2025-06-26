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
import kotlinx.coroutines.withContext
import java.lang.Exception



class AIChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var modelSpinner: Spinner

    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        inputEditText = findViewById(R.id.messageInput)
        sendButton = findViewById<ImageButton>(R.id.sendButton)
        modelSpinner = findViewById(R.id.modelSpinner)

        // Set up model selector
        val modelOptions = listOf("GPT-3.5", "GPT-4", "Mistral", "Groq")
        modelSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modelOptions)

        // Set up chat
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

            val modelName = when (modelSpinner.selectedItem.toString()) {
                "GPT-4" -> "gpt-4" to "openai"
                "Mistral" -> "mistral" to "mistral"
                "Groq" -> "meta-llama/llama-4-scout-17b-16e-instruct" to "groq"
                else -> "gpt-3.5-turbo" to "openai"
            }

            queryAI(modelName.first, modelName.second)
        }
    }

    private fun addMessage(role: String, content: String) {
        chatMessages.add(ChatMessage(role, content))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    private fun queryAI(model: String, source: String) {
        coroutineScope.launch {
            try {
                val request = ChatRequest(
                    model = model,
                    messages = chatMessages.map { ChatMessage(it.role, it.content) },
                    temperature = 0.7
                )

                val service = RetrofitClient.getService(source)
                val authHeader = when (source) {
                    "groq" -> "Bearer ${BuildConfig.GROQ_API_KEY}"
                    "openai" -> "Bearer ${BuildConfig.GROQ_API_KEY}"
                    else -> "" // Local Mistral requires no header
                }

                val response = withContext(Dispatchers.IO) {
                    service.createChatCompletion(authHeader, request)
                }

                val reply = response.choices.firstOrNull()?.message?.content ?: "No reply"
                addMessage("assistant", reply)

            } catch (e: Exception) {
                addMessage("assistant", "Error: ${e.localizedMessage}")
            }
        }
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }
}