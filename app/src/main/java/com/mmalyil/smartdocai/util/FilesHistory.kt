package com.mmalyil.smartdocai.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.mmalyil.smartdocai.MainActivity
import com.mmalyil.smartdocai.R
import com.mmalyil.smartdocai.model.AppDatabase
import com.mmalyil.smartdocai.model.ScannedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persist export / generated files into Room Files history and nudge the user.
 */
object FilesHistory {

    const val EXTRA_OPEN_FILES = "openFiles"

    fun saveAsync(
        context: Context,
        fileName: String,
        fileUri: String,
        content: String,
        aiResponse: String? = null,
        scope: CoroutineScope,
        anchor: View? = null,
        onSavedUi: (() -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getDatabase(context.applicationContext).scannedFileDao()
                // Never persist blank/placeholder AI — only real replies or omit.
                val ai = aiResponse?.trim()?.takeIf { it.isNotEmpty() }
                dao.insert(
                    ScannedFile(
                        fileName = fileName,
                        fileUri = fileUri,
                        content = content,
                        aiResponse = ai
                    )
                )
                withContext(Dispatchers.Main) {
                    onSavedUi?.invoke()
                    showSavedSnackbar(context, anchor)
                }
            } catch (_: Exception) {
                // Don't fail the export path if history insert fails.
            }
        }
    }

    fun showSavedSnackbar(context: Context, anchor: View?) {
        val root = anchor
            ?: (context as? Activity)?.findViewById(android.R.id.content)
            ?: return
        val sb = Snackbar.make(root, "Saved to Files", Snackbar.LENGTH_LONG)
            .setAction("Open in Files") {
                openFilesTab(context)
            }
        sb.show()
    }

    fun openFilesTab(context: Context) {
        when (val act = context as? Activity) {
            is MainActivity -> {
                act.findViewById<BottomNavigationView>(R.id.bottomNav)?.selectedItemId =
                    R.id.nav_files
            }
            else -> {
                val i = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_OPEN_FILES, true)
                }
                context.startActivity(i)
                act?.finish()
            }
        }
    }
}
