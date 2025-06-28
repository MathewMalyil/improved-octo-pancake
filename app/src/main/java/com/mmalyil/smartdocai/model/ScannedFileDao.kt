package com.mmalyil.smartdocai.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow


import androidx.lifecycle.LiveData
import androidx.room.OnConflictStrategy


@Dao
interface ScannedFileDao {

    @Query("SELECT * FROM scannedfile ORDER BY timestamp DESC")
    fun getAllFiles(): LiveData<List<ScannedFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: ScannedFile)
}