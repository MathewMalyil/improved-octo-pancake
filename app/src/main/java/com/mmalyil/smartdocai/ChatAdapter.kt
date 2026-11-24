package com.mmalyil.smartdocai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mmalyil.smartdocai.model.ChatMessage
import com.mmalyil.smartdocai.util.FilesHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val expanded = mutableSetOf<Int>()

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textRole: TextView = view.findViewById(R.id.textRole)
        val textMessage: TextView = view.findViewById(R.id.textMessage)
        val btnShowMore: MaterialButton = view.findViewById(R.id.btnShowMore)
        val assistantActions: View = view.findViewById(R.id.assistantActions)
        val btnCopy: MaterialButton = view.findViewById(R.id.btnCopyReply)
        val btnShare: MaterialButton = view.findViewById(R.id.btnShareReply)
        val btnSaveTxt: MaterialButton = view.findViewById(R.id.btnSaveTxt)
        val btnSavePdf: MaterialButton = view.findViewById(R.id.btnSavePdf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        val ctx = holder.itemView.context
        val isAssistant = message.role.equals("assistant", ignoreCase = true) ||
            message.role.equals("ai", ignoreCase = true)

        holder.textRole.text = message.role.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

        val full = message.content
        val long = full.length > COLLAPSE_CHARS || full.count { it == '\n' } >= COLLAPSE_LINES
        val isExpanded = expanded.contains(position)

        if (!long || isExpanded) {
            holder.textMessage.maxLines = Integer.MAX_VALUE
            holder.textMessage.text = full
            holder.btnShowMore.visibility = if (long) View.VISIBLE else View.GONE
            holder.btnShowMore.text = if (long) "Show less · Full screen" else ""
        } else {
            holder.textMessage.maxLines = COLLAPSE_LINES
            holder.textMessage.text = full
            holder.btnShowMore.visibility = View.VISIBLE
            holder.btnShowMore.text = "Show more · Full screen"
        }

        holder.btnShowMore.setOnClickListener {
            if (!isExpanded) {
                expanded.add(position)
                notifyItemChanged(position)
                showFullScreen(ctx, full)
            } else {
                expanded.remove(position)
                notifyItemChanged(position)
            }
        }

        holder.assistantActions.visibility = if (isAssistant) View.VISIBLE else View.GONE
        if (!isAssistant) return

        holder.btnCopy.setOnClickListener {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("AI reply", full))
            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
        }
        holder.btnShare.setOnClickListener {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, full)
                putExtra(Intent.EXTRA_SUBJECT, "SmartDocAI chat reply")
            }
            ctx.startActivity(Intent.createChooser(share, "Share reply"))
        }
        holder.btnSaveTxt.setOnClickListener { saveReplyAsTxt(ctx, full, holder.itemView) }
        holder.btnSavePdf.setOnClickListener { saveReplyAsPdf(ctx, full, holder.itemView) }
    }

    override fun getItemCount(): Int = messages.size

    private fun showFullScreen(ctx: Context, text: String) {
        val scroll = ScrollView(ctx).apply {
            setPadding(48, 32, 48, 32)
            addView(TextView(ctx).apply {
                this.text = text
                textSize = 16f
                setTextIsSelectable(true)
            })
        }
        AlertDialog.Builder(ctx)
            .setTitle("Full reply")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy") { _, _ ->
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AI reply", text))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun saveReplyAsTxt(ctx: Context, content: String, anchor: View) {
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
                val file = File(dir, "SmartDocAI_Chat_${System.currentTimeMillis()}.txt")
                file.writeText(content)
                val uri = FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", file
                )
                withContext(Dispatchers.Main) {
                    val ai = content.trim().takeIf { it.isNotEmpty() }
                    FilesHistory.saveAsync(
                        context = ctx,
                        fileName = file.name,
                        fileUri = uri.toString(),
                        content = content.take(2000),
                        aiResponse = ai,
                        scope = scope,
                        anchor = anchor
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Couldn't save TXT", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveReplyAsPdf(ctx: Context, content: String, anchor: View) {
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
                val file = File(dir, "SmartDocAI_Chat_${System.currentTimeMillis()}.pdf")
                val pageWidth = 595
                val pageHeight = 842
                val margin = 40f
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 12f
                }
                val pdf = PdfDocument()
                var pageNum = 1
                var y = margin
                var page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                )
                val lines = content.replace("\r", "").split('\n')
                fun ensureSpace(need: Float) {
                    if (y + need > pageHeight - margin) {
                        pdf.finishPage(page)
                        pageNum++
                        page = pdf.startPage(
                            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                        )
                        y = margin
                    }
                }
                for (para in lines) {
                    var start = 0
                    val len = para.length
                    if (len == 0) {
                        ensureSpace(paint.textSize + 4f)
                        y += paint.textSize + 4f
                        continue
                    }
                    while (start < len) {
                        ensureSpace(paint.textSize + 4f)
                        val count = paint.breakText(para, start, len, true, pageWidth - 2 * margin, null)
                        val end = (start + count).coerceAtMost(len)
                        page.canvas.drawText(para.substring(start, end), margin, y, paint)
                        y += paint.textSize + 4f
                        start = end
                    }
                }
                pdf.finishPage(page)
                FileOutputStream(file).use { pdf.writeTo(it) }
                pdf.close()
                val uri = FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", file
                )
                withContext(Dispatchers.Main) {
                    val ai = content.trim().takeIf { it.isNotEmpty() }
                    FilesHistory.saveAsync(
                        context = ctx,
                        fileName = file.name,
                        fileUri = uri.toString(),
                        content = content.take(2000),
                        aiResponse = ai,
                        scope = scope,
                        anchor = anchor
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Couldn't save PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val COLLAPSE_CHARS = 420
        private const val COLLAPSE_LINES = 8
    }
}
