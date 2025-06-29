package com.mmalyil.smartdocai.model



import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.lifecycle.LiveData

import androidx.lifecycle.ViewModel
import android.util.Log
import com.mmalyil.smartdocai.model.ScannedFile
import com.mmalyil.smartdocai.model.ScannedFileRepository

class ScannedFileViewModel(private val repository: ScannedFileRepository) : ViewModel() {

    val allFiles: LiveData<List<ScannedFile>> = repository.getAllFiles()

    fun insertFile(fileUri: String, fileName: String, content: String, aiResponse: String?) {
        val file = ScannedFile(
            fileName = fileName,
            fileUri = fileUri,
            content = content,
            aiResponse = aiResponse
        )
        Log.d("DEBUG_INSERT", "Inserting file: $file") // ✅ Add this line
        Log.d("DEBUG_INSERT", "File URI: $fileUri") // ✅ Add this line
        Log.d("DEBUG_INSERT", "File Name: $fileName") // ✅ Add this line
        insert(file)
    }


    private fun insert(file: ScannedFile) {
        viewModelScope.launch {
            repository.insert(file)
        }
    }
}