package com.mmalyil.smartdocai.model

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mmalyil.smartdocai.databinding.ItemScannedFileBinding
import androidx.recyclerview.widget.DiffUtil

import androidx.recyclerview.widget.ListAdapter
import android.view.View
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import com.mmalyil.smartdocai.R
import android.view.*



class ScannedFileAdapter(
    private val onClick: (ScannedFile) -> Unit
) : ListAdapter<ScannedFile, ScannedFileAdapter.FileViewHolder>(DiffCallback()) {

    class FileViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(file: ScannedFile, onClick: (ScannedFile) -> Unit) {
            view.findViewById<TextView>(R.id.fileNameText).text = file.fileName
            view.findViewById<TextView>(R.id.fileDateText).text = DateFormat.getDateTimeInstance().format(Date(file.timestamp))
            view.setOnClickListener { onClick(file) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_scanned_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class DiffCallback : DiffUtil.ItemCallback<ScannedFile>() {
        override fun areItemsTheSame(old: ScannedFile, new: ScannedFile) = old.id == new.id
        override fun areContentsTheSame(old: ScannedFile, new: ScannedFile) = old == new
    }
}