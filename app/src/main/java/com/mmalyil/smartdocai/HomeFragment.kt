package com.mmalyil.smartdocai

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class HomeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        view.findViewById<TextView>(R.id.textViewHome).text = "SmartDocAI"

        view.findViewById<MaterialButton>(R.id.btnHomeScan).setOnClickListener {
            startActivity(
                Intent(requireContext(), ScanActivity::class.java).apply {
                    putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_CAMERA)
                }
            )
        }
        view.findViewById<MaterialButton>(R.id.btnHomeFromPhotos).setOnClickListener {
            startActivity(
                Intent(requireContext(), ScanActivity::class.java).apply {
                    putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_PHOTOS)
                }
            )
        }
        return view
    }
}
