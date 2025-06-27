package com.mmalyil.smartdocai.model


import android.os.Parcelable


/**
 * Data class representing a scanned file with its metadata.
 *
 * @property fileName The name of the scanned file.
 * @property fileUri The URI of the scanned file.
 * @property content The text content extracted from the scanned file.
 * @property date The timestamp when the file was scanned.
 */

data class ScannedFile(
    val fileName: String,
    val fileUri: String,
    val content: String = "",
    val date: Long = System.currentTimeMillis()
)
