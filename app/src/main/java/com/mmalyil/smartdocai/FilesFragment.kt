package com.mmalyil.smartdocai
// FilesFragment.kt


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.mmalyil.smartdocai.R

class FilesFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_files, container, false)
        view.findViewById<TextView>(R.id.textViewFiles).text = "Your saved files will appear here"
        return view
    }
}