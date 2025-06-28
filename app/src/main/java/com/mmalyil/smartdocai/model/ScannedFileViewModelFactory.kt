package com.mmalyil.smartdocai.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mmalyil.smartdocai.model.ScannedFileRepository
import com.mmalyil.smartdocai.model.ScannedFileViewModel
import com.mmalyil.smartdocai.model.ScannedFile
import com.mmalyil.smartdocai.model.ScannedFileDao
import com.mmalyil.smartdocai.model.AppDatabase


class ScannedFileViewModelFactory(
    private val repository: ScannedFileRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScannedFileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScannedFileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}