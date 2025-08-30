package com.mmalyil.smartdocai

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mmalyil.smartdocai.model.ChatMessage
import com.mmalyil.smartdocai.util.ChatReply
import com.mmalyil.smartdocai.util.analyzeWithAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AIChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var modelTextView: TextView

    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        val root = findViewById<View>(R.id.chatRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, maxOf(ime.bottom, sysBars.bottom))
            insets
        }

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        inputEditText = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        modelTextView = findViewById(R.id.modelUsedText)

        chatAdapter = ChatAdapter(chatMessages)
        chatRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        chatRecyclerView.adapter = chatAdapter

        sendButton.setOnClickListener {
            val userInput = inputEditText.text.toString().trim()
            if (userInput.isEmpty()) return@setOnClickListener

            sendButton.isEnabled = false
            inputEditText.isEnabled = false

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
            try {
                analyzeWithAI(
                    context = this@AIChatActivity,
                    prompt = prompt,
                    history = chatMessages,
                    onResult = { reply: ChatReply ->
                        addMessage("assistant", reply.content)
                        modelTextView.text = "Model: ${reply.modelUsed}"
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@AIChatActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                sendButton.isEnabled = true
                inputEditText.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        coroutineScope.cancel()
        super.onDestroy()
    }
}