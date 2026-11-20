package com.mmalyil.smartdocai
// FilesFragment.kt


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mmalyil.smartdocai.model.ScannedFileAdapter
import com.mmalyil.smartdocai.model.ScannedFileViewModel
import com.mmalyil.smartdocai.model.ScannedFileRepository
import com.mmalyil.smartdocai.model.AppDatabase
import android.content.Intent
import com.mmalyil.smartdocai.model.ScannedFileDetailActivity
import android.util.Log
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.math.max
import com.mmalyil.smartdocai.databinding.FragmentFilesBinding





class FilesFragment : Fragment() {

    private lateinit var binding: FragmentFilesBinding
    private lateinit var adapter: ScannedFileAdapter
    private lateinit var viewModel: ScannedFileViewModel


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {


        val dao = AppDatabase.getDatabase(requireContext()).scannedFileDao()
        val repository = ScannedFileRepository(dao)
        viewModel = ScannedFileViewModel(repository)


        adapter = ScannedFileAdapter(
            fileList = emptyList(),
            onClick = { file ->
                val intent = Intent(requireContext(), ScannedFileDetailActivity::class.java).apply {
                    putExtra("scannedFile", file)
                    putExtra(ScannedFileDetailActivity.EXTRA_AUTO_OPEN, true)
                }
                startActivity(intent)
            },
            onDelete = { file ->
                Log.d("DEBUG_DELETE", "Deleting file: $file")
                viewModel.deleteFile(file)
            }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = adapter

        binding.btnFilesEmptyScan.setOnClickListener {
            startActivity(
                Intent(requireContext(), ScanActivity::class.java).apply {
                    putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_CAMERA)
                }
            )
        }

        viewModel.allFiles.observe(viewLifecycleOwner) { files ->
            Log.d("DEBUG", "Observed ${files.size} files")
            adapter.updateFiles(files)
            val empty = files.isEmpty()
            binding.filesEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            binding.rvFiles.visibility = if (empty) View.GONE else View.VISIBLE
        }
    }
}