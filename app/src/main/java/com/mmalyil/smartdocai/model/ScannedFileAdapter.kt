package com.mmalyil.smartdocai.model

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mmalyil.smartdocai.databinding.ItemScannedFileBinding



class ScannedFileAdapter(
    private val context: Context,
    private var fileList: List<ScannedFile>,
    private val onShareClicked: (ScannedFile) -> Unit,
    private val onDeleteClicked: (ScannedFile) -> Unit
) : RecyclerView.Adapter<ScannedFileAdapter.FileViewHolder>() {

    inner class FileViewHolder(val binding: ItemScannedFileBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemScannedFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = fileList[position]
        with(holder.binding) {
            fileName.text = file.fileName
            fileThumbnail.setImageURI(Uri.parse(file.fileUri))

            btnShare.setOnClickListener { onShareClicked(file) }
            btnDelete.setOnClickListener { onDeleteClicked(file) }
        }
    }

    override fun getItemCount(): Int = fileList.size

    fun updateFiles(newList: List<ScannedFile>) {
        fileList = newList
        notifyDataSetChanged()
    }
}