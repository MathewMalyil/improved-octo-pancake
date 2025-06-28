package com.mmalyil.smartdocai.model



import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.lifecycle.LiveData

import androidx.lifecycle.ViewModel


class ScannedFileViewModel(private val repository: ScannedFileRepository) : ViewModel() {

    val allFiles: LiveData<List<ScannedFile>> = repository.getAllFiles()

    fun insertFile(content: String, fileName: String, aiResponse: String?) {
        val file = ScannedFile(
            fileUri = fileName,
            fileName = fileName,
            content = content,
            aiResponse = aiResponse
        )
        insert(file)
    }

    fun insert(file: ScannedFile) {
        viewModelScope.launch {
            repository.insert(file)
        }
    }
    }
