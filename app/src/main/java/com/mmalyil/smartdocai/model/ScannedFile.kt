package com.mmalyil.smartdocai.model


import androidx.room.Entity
import androidx.room.PrimaryKey





@Entity(tableName = "scannedfile")
data class ScannedFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileUri: String,
    var fileName: String,
    val content: String,

    val aiResponse: String?,
    val timestamp: Long = System.currentTimeMillis(
) // Default to current time
)