package com.mmalyil.smartdocai




import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mmalyil.smartdocai.model.ChatMessage


class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textRole: TextView = view.findViewById(R.id.textRole)
        val textMessage: TextView = view.findViewById(R.id.textMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.textRole.text = message.role.capitalize()
        holder.textMessage.text = message.content
    }

    override fun getItemCount(): Int = messages.size
}



// item_chat_message.xml should contain textMessage and textRole TextViews