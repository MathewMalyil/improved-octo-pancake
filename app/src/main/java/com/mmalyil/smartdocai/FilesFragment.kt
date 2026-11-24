package com.mmalyil.smartdocai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.mmalyil.smartdocai.databinding.FragmentFilesBinding
import com.mmalyil.smartdocai.model.AppDatabase
import com.mmalyil.smartdocai.model.ScannedFile
import com.mmalyil.smartdocai.model.ScannedFileAdapter
import com.mmalyil.smartdocai.model.ScannedFileDetailActivity
import com.mmalyil.smartdocai.model.ScannedFileRepository
import com.mmalyil.smartdocai.model.ScannedFileViewModel
import com.mmalyil.smartdocai.model.ScannedFileViewModelFactory

/**
 * Files history. Must paint on first open — Room LiveData alone can miss the first
 * emission until another screen warms/invalidates the DB (S25 blank-tab bug).
 */
class FilesFragment : Fragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ScannedFileAdapter
    private lateinit var viewModel: ScannedFileViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getDatabase(requireContext())
        val repository = ScannedFileRepository(db.scannedFileDao())
        viewModel = ViewModelProvider(
            requireActivity(),
            ScannedFileViewModelFactory(repository)
        )[ScannedFileViewModel::class.java]

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
                confirmDeleteFile(file)
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

        binding.btnFilesCombinePdfs.setOnClickListener {
            startActivity(Intent(requireContext(), CombinePdfActivity::class.java))
        }

        // Snapshot covers cold first open; LiveData covers later invalidations.
        viewModel.filesSnapshot.observe(viewLifecycleOwner) { files ->
            bindFiles(files, source = "snapshot")
        }
        viewModel.allFiles.observe(viewLifecycleOwner) { files ->
            bindFiles(files, source = "live")
        }

        // Eager one-shot so first resume is never blank waiting on LiveData.
        viewModel.refreshNow()
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshNow()
        }
    }

    private fun bindFiles(files: List<ScannedFile>?, source: String) {
        if (files == null) return
        Log.d("FilesFragment", "bindFiles source=$source count=${files.size}")
        adapter.updateFiles(files)
        val empty = files.isEmpty()
        binding.filesEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvFiles.visibility = if (empty) View.GONE else View.VISIBLE
        // Keep Combine reachable even when empty (button sits above the list).
        binding.btnFilesCombinePdfs.visibility = View.VISIBLE
    }

    /** Same confirm as Files detail — long-press must not wipe a row silently. */
    private fun confirmDeleteFile(file: ScannedFile) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle("Delete file?")
            .setMessage(
                "Remove “${file.fileName}” from SmartDocAI history. " +
                    "The file stays in app storage until cleared with app data."
            )
            .setPositiveButton("Delete") { _, _ ->
                Log.d("DEBUG_DELETE", "Confirmed delete: ${file.fileName}")
                viewModel.deleteFile(file)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
