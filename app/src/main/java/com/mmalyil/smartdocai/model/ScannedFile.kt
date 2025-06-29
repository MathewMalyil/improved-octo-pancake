package com.mmalyil.smartdocai.model


import androidx.room.Entity
import androidx.room.PrimaryKey

import java.io.Serializable


@Entity(tableName = "scannedfiles")
data class ScannedFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileUri: String,
    val content: String,
    val aiResponse: String?,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable


