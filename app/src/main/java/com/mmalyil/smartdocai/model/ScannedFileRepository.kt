package com.mmalyil.smartdocai.model

import kotlinx.coroutines.flow.Flow


import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

import androidx.room.Delete

class ScannedFileRepository(private val dao: ScannedFileDao) {

    fun getAllFiles(): LiveData<List<ScannedFile>> {
        return dao.getAllFiles()
    }

    suspend fun insert(file: ScannedFile) {
        dao.insert(file)
    }
}