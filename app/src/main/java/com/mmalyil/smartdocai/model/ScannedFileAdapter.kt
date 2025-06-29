package com.mmalyil.smartdocai.model


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mmalyil.smartdocai.model.ScannedFile
import android.view.View
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import com.mmalyil.smartdocai.R
import android.view.*
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import android.content.Intent

class ScannedFileAdapter(
    private var fileList: List<ScannedFile>,
    private val onClick: (ScannedFile) -> Unit
) : RecyclerView.Adapter<ScannedFileAdapter.FileViewHolder>() {

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.tvFileName)
        val timestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val contentPreview: TextView = itemView.findViewById(R.id.tvContentPreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scanned_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = fileList[position]
        holder.fileName.text = file.fileName
        holder.timestamp.text = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            .format(Date(file.timestamp))
        holder.contentPreview.text = file.content.take(100)

        holder.itemView.setOnClickListener {
            onClick(file)
        }
    }

    override fun getItemCount(): Int = fileList.size

    fun updateFiles(newList: List<ScannedFile>) {
        fileList = newList
        notifyDataSetChanged()
    }
}