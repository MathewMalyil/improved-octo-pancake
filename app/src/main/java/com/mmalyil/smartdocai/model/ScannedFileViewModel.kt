package com.mmalyil.smartdocai.model

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScannedFileViewModel(private val repository: ScannedFileRepository) : ViewModel() {

    /** Room LiveData — updates after inserts/deletes once the InvalidationTracker is warm. */
    val allFiles: LiveData<List<ScannedFile>> = repository.getAllFiles()

    /**
     * Immediate list for first paint. Room LiveData can miss the first emission on a
     * cold Files tab; [refreshNow] fills this so UI is never stuck blank.
     */
    private val _filesSnapshot = MutableLiveData<List<ScannedFile>>()
    val filesSnapshot: LiveData<List<ScannedFile>> = _filesSnapshot

    fun refreshNow() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                repository.getAllFilesOnce()
            }
            _filesSnapshot.value = list
        }
    }

    fun insertFile(fileUri: String, fileName: String, content: String, aiResponse: String?) {
        val file = ScannedFile(
            fileName = fileName,
            fileUri = fileUri,
            content = content,
            aiResponse = aiResponse
        )
        Log.d("DEBUG_INSERT", "Inserting file: $file")
        Log.d("DEBUG_INSERT", "File URI: $fileUri")
        Log.d("DEBUG_INSERT", "File Name: $fileName")
        insert(file)
    }

    private fun insert(file: ScannedFile) {
        viewModelScope.launch {
            repository.insert(file)
            refreshNow()
        }
    }

    fun deleteFile(file: ScannedFile) {
        viewModelScope.launch {
            repository.delete(file)
            refreshNow()
        }
    }
}
