package com.focusflow.services

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

sealed interface DownloadResult {
    data object Success : DownloadResult
    data class Failure(val message: String) : DownloadResult
}

/**
 * Saves the generated PDF into the public Downloads collection via
 * MediaStore — the scoped-storage-compliant way to do this on API 29+
 * without requesting WRITE_EXTERNAL_STORAGE. Falls back to the legacy
 * direct-file-path approach on API 28 and below, which does need that
 * permission (see the README's manifest note).
 */
class ReportDownloadService(private val context: Context) {

    fun saveToDownloads(sourceFile: File, displayName: String): DownloadResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(sourceFile, displayName)
            } else {
                saveViaLegacyFile(sourceFile, displayName)
            }
            DownloadResult.Success
        } catch (e: Exception) {
            DownloadResult.Failure(e.message ?: "Couldn't save the report")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(sourceFile: File, displayName: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values) ?: error("MediaStore insert failed")

        resolver.openOutputStream(itemUri)?.use { out ->
            sourceFile.inputStream().use { input -> input.copyTo(out) }
        } ?: error("Couldn't open output stream")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacyFile(sourceFile: File, displayName: String) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val destination = File(downloadsDir, displayName)
        sourceFile.copyTo(destination, overwrite = true)
    }
}
