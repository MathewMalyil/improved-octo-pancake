package com.mmalyil.smartdocai
// FilesFragment.kt


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.mmalyil.smartdocai.model.ScannedFileAdapter

import android.view.*

import androidx.lifecycle.ViewModelProvider

import com.mmalyil.smartdocai.model.ScannedFileViewModel

import com.mmalyil.smartdocai.model.ScannedFile
import com.mmalyil.smartdocai.model.ScannedFileRepository
import com.mmalyil.smartdocai.model.AppDatabase

import com.mmalyil.smartdocai.model.ScannedFileViewModelFactory
import android.widget.Button
import java.text.DateFormat
import java.util.Date
import android.widget.Toast
import com.mmalyil.smartdocai.R
import com.mmalyil.smartdocai.model.ScannedFileDao

// FilesFragment.kt



class FilesFragment : Fragment() {
    private lateinit var viewModel: ScannedFileViewModel
    private lateinit var adapter: ScannedFileAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_files, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.filesRecyclerView)


        // ✅ Initialize DAO, Repository, ViewModel
        val dao = AppDatabase.getDatabase(requireContext()).scannedFileDao()
        val repository = ScannedFileRepository(dao)
        val factory = ScannedFileViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ScannedFileViewModel::class.java]

        // ✅ Set up RecyclerView
        adapter = ScannedFileAdapter { file ->
            // Open file or show options
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // ✅ Observe LiveData
        viewModel.allFiles.observe(viewLifecycleOwner) { list: List<ScannedFile> ->
            adapter.submitList(list)
        }

        // ✅ Test Insert Button
        val addTestButton = view.findViewById<Button>(R.id.btnAddTest)
        addTestButton.setOnClickListener {

            val extractedText = "Text from PDF or OCR"
            val aiSummary = "AI summary of the text"
            val fileName = "scanned_file_${System.currentTimeMillis()}.txt"
            val aiResponse = "Sample content from AI"


            viewModel.insertFile(
                content = extractedText,
                fileName = fileName,
                aiResponse = aiSummary
            )

        }


        return view
    }
}

