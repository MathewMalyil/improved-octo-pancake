package com.mmalyil.smartdocai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mmalyil.smartdocai.model.AppDatabase
import com.mmalyil.smartdocai.model.ScannedFile
import com.mmalyil.smartdocai.model.ScannedFileDetailActivity
import com.mmalyil.smartdocai.model.ScannedFileRepository
import com.mmalyil.smartdocai.model.ScannedFileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var viewModel: ScannedFileViewModel
    private lateinit var recentsAdapter: HomeRecentsAdapter
    private lateinit var recentsEmpty: View
    private lateinit var rvRecents: RecyclerView
    private lateinit var btnSeeAll: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialCardView>(R.id.cardHomeScan).setOnClickListener {
            openScan(ScanActivity.MODE_CAMERA)
        }
        view.findViewById<MaterialButton>(R.id.btnEmptyScan).setOnClickListener {
            openScan(ScanActivity.MODE_CAMERA)
        }
        view.findViewById<MaterialCardView>(R.id.cardHomeFromPhotos).setOnClickListener {
            openScan(ScanActivity.MODE_PHOTOS)
        }
        view.findViewById<MaterialCardView>(R.id.cardHomeImageToExcel).setOnClickListener {
            (activity as? MainActivity)?.goToToolsAndAsk("imageToExcel")
        }

        btnSeeAll = view.findViewById(R.id.btnSeeAllFiles)
        btnSeeAll.setOnClickListener { openFilesTab() }

        recentsEmpty = view.findViewById(R.id.homeRecentsEmpty)
        rvRecents = view.findViewById(R.id.rvHomeRecents)
        recentsAdapter = HomeRecentsAdapter { file ->
            startActivity(
                Intent(requireContext(), ScannedFileDetailActivity::class.java).apply {
                    putExtra("scannedFile", file)
                    putExtra(ScannedFileDetailActivity.EXTRA_AUTO_OPEN, true)
                }
            )
        }
        rvRecents.layoutManager = LinearLayoutManager(requireContext())
        rvRecents.adapter = recentsAdapter
        rvRecents.isNestedScrollingEnabled = false

        val dao = AppDatabase.getDatabase(requireContext()).scannedFileDao()
        viewModel = ScannedFileViewModel(ScannedFileRepository(dao))
        viewModel.allFiles.observe(viewLifecycleOwner) { files ->
            bindRecents(files)
        }
    }

    private fun bindRecents(files: List<ScannedFile>) {
        val recent = files.take(RECENT_LIMIT)
        if (recent.isEmpty()) {
            recentsEmpty.visibility = View.VISIBLE
            rvRecents.visibility = View.GONE
            btnSeeAll.visibility = View.GONE
        } else {
            recentsEmpty.visibility = View.GONE
            rvRecents.visibility = View.VISIBLE
            btnSeeAll.visibility = View.VISIBLE
            recentsAdapter.submit(recent)
        }
    }

    private fun openScan(mode: String) {
        startActivity(
            Intent(requireContext(), ScanActivity::class.java).apply {
                putExtra(ScanActivity.EXTRA_START_MODE, mode)
            }
        )
    }

    private fun openFilesTab() {
        val nav = activity?.findViewById<BottomNavigationView>(R.id.bottomNav) ?: return
        nav.selectedItemId = R.id.nav_files
    }

    private class HomeRecentsAdapter(
        private val onClick: (ScannedFile) -> Unit
    ) : RecyclerView.Adapter<HomeRecentsAdapter.VH>() {

        private var items: List<ScannedFile> = emptyList()
        private val fmt = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())

        fun submit(list: List<ScannedFile>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_home_recent, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val file = items[position]
            holder.name.text = file.fileName
            holder.meta.text = fmt.format(Date(file.timestamp))
            holder.itemView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount(): Int = items.size

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.tvRecentName)
            val meta: TextView = itemView.findViewById(R.id.tvRecentMeta)
        }
    }

    companion object {
        private const val RECENT_LIMIT = 5
    }
}
