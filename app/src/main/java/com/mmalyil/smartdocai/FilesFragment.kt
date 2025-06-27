package com.mmalyil.smartdocai
// FilesFragment.kt


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mmalyil.smartdocai.R
import com.mmalyil.smartdocai.model.ScannedFile
import com.mmalyil.smartdocai.model.ScannedFileAdapter

class FilesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_files, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.filesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Sample data
        val dummyFiles = listOf(
            ScannedFile("Visa_Letter.pdf", "file:///storage/emulated/0/Documents/Visa_Letter.pdf"),
            ScannedFile("Boarding_Pass.jpg", "file:///storage/emulated/0/Pictures/Boarding_Pass.jpg"),
            ScannedFile("Flight_Ticket.png", "file:///storage/emulated/0/Pictures/Flight_Ticket.png")
        )

        val adapter = ScannedFileAdapter(
            requireContext(),
            dummyFiles,
            onShareClicked = { file ->
                // Handle share action
                // For example, you can use an Intent to share the file
            },
            onDeleteClicked = { file ->
                // Handle delete action
                // For example, remove the file from the list and notify the adapter
            }
        )
        recyclerView.adapter = adapter

        return view
    }
}
